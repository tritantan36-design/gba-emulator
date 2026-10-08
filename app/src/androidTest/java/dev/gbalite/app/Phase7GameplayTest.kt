@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import dev.gbalite.renderer.OriginalSurface
import dev.gbalite.session.SessionState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.CRC32

/** Opt-in, separately staged legal homebrew. No game ROM is packaged in either APK. */
class Phase7GameplayTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun waitFor(message: String,condition: ()->Boolean) {
        try {compose.waitUntil(15000,condition)}
        catch(e: Throwable) {throw AssertionError("$message; state=${model().session.state.value}; message=${model().message}; metrics=${model().session.playerMetrics()}",e)}
    }
    private fun surface(): OriginalSurface? {
        fun find(v: View): OriginalSurface? {
            if(v is OriginalSurface) return v
            if(v is ViewGroup) for(n in 0 until v.childCount) find(v.getChildAt(n))?.let {return it}
            return null
        }
        var result: OriginalSurface?=null
        compose.runOnUiThread {result=find(compose.activity.window.decorView)}
        return result
    }
    private fun press(button: GbaButton,duration: Long=150) {
        model().session.input.setButton(button,true)
        try {Thread.sleep(duration)} finally {model().session.input.setButton(button,false)}
        Thread.sleep(200)
    }
    @Test fun stagedHomebrewGameplay() {
        val args=InstrumentationRegistry.getArguments()
        val game=args.getString("phase7Gameplay")
        assumeTrue("Explicit audited phase7Gameplay selection required",game in setOf("blob-v1.1","hyperspace-v0.25.0"))
        val blob=game=="blob-v1.1"
        val filename=if(blob) "blob-goes-3d-v1.1.gba" else "hyperspace-roll-agb-v0.25.0.gba"
        val expectedSha=if(blob) "313cbb23444ef35497b4de23a459311f732c885d392f5e465ebe1861a26746f4" else "de5a61698452d5f409970f1f5e042d0d4ace21ccc0ea7d2a770b8c311b8f3c57"
        val seconds=args.getString("phase7GameplaySeconds","600").toInt()
        require(seconds in 20..1800)
        val rom=File(i.targetContext.filesDir,"phase7-gameplay/$filename")
        assertTrue("Stage the audited upstream ROM in app-private files first",rom.isFile)
        val sha=MessageDigest.getInstance("SHA-256").digest(rom.readBytes()).joinToString("") {"%02x".format(it)}
        assertEquals("Only the reviewed official binary is accepted",expectedSha,sha)
        val output=File(i.targetContext.filesDir,"phase7-gameplay-result-${System.currentTimeMillis()}").apply {mkdirs()}
        val rows=File(output,"events.jsonl")
        val started=SystemClock.elapsedRealtime()
        val oldDisplay=model().displaySettings
        val hashes=mutableSetOf<Long>()
        var completed=false
        var inputs=0
        fun frameHash(): Long {
            val bytes=ByteBuffer.allocateDirect(240*160*4)
            assertTrue(model().session.frames.copyFrame(bytes));bytes.rewind()
            val data=ByteArray(bytes.remaining());bytes.get(data)
            return CRC32().apply {update(data)}.value
        }
        fun audioCounter(name: String): Long {
            val session=model().session
            val core=session.javaClass.getDeclaredField("core").apply {isAccessible=true}.get(session)!!
            val handle=core.javaClass.getDeclaredField("handle").apply {isAccessible=true}.getLong(core)
            val c=Class.forName("dev.gbalite.mgba.JniBridge")
            return c.getDeclaredMethod(name,java.lang.Long.TYPE).invoke(c.getDeclaredConstructor().newInstance(),handle) as Long
        }
        fun sample(event: String,shot: Boolean=false) {
            val hash=frameHash();hashes+=hash
            rows.appendText(JSONObject().put("event",event).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("frames",model().session.playerMetrics().frames).put("metrics",model().session.playerMetrics().toString())
                .put("state",model().session.state.value.name).put("frameCrc32",hash)
                .put("nonzeroSamples",audioCounter("nonzeroSamplesForTest")).put("playedSamples",audioCounter("playedForTest"))
                .put("renderer",surface()?.diagnostics().toString()).toString()+"\n")
            if(shot) {
                i.uiAutomation.takeScreenshot()?.let {bitmap ->
                    File(output,"$event.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                }
            }
        }
        fun visibleFrame() {
            waitFor("Actual game TextureView remained black") {
                var visible=false
                compose.runOnUiThread {surface()?.let {s ->
                    if(s.isAvailable) s.bitmap?.let {b ->
                        // A sparse title can fall entirely between grid samples.
                        // Inspect the whole actual TextureView rather than only 96 points.
                        val pixels=IntArray(b.width*b.height)
                        b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
                        visible=pixels.count {(it and 0x00ffffff)!=0}>64;b.recycle()
                    }
                }}
                visible
            }
        }
        try {
            val uri=Uri.parse("content://dev.gbalite.app.test.rom/$filename")
            i.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            compose.runOnUiThread {model().open(uri)}
            waitFor("Homebrew failed to boot") {model().playing && !model().loading && model().session.playerMetrics().frames>10}
            compose.runOnUiThread {model().display(DisplaySettings(DisplayMode.ORIGINAL))}
            visibleFrame();sample("title",true)
            // Upstream controls: Start, A to play, A to select level 0.
            if(blob) {press(GbaButton.START);press(GbaButton.A);press(GbaButton.A)}
            else {
                // Any key leaves the title; A selects die/face/upgrade in the customise screen.
                press(GbaButton.A)
                press(GbaButton.A)
                repeat(4) {press(GbaButton.RIGHT);repeat(3) {press(GbaButton.A)}}
            }
            Thread.sleep(1500)
            visibleFrame();sample("level-start",true)
            val session=model().session
            runBlocking {
                session.setPaused(true)
                assertTrue(session.saveState())
                session.setPaused(false)
                session.input.setButton(GbaButton.RIGHT,true)
                Thread.sleep(500);session.input.releaseAll();session.setPaused(true)
                assertTrue(session.loadState())
                assertTrue(session.saveState(1));assertTrue(session.loadState(1))
                session.setPaused(false)
            }
            // Native State import intentionally invalidates the previously published frame.
            // Require a fresh post-load frame, not a stale paused framebuffer.
            waitFor("No fresh frame after State load") {
                model().session.frames.copyFrame(ByteBuffer.allocateDirect(240*160*4))
            }
            visibleFrame()
            sample("state-restored",true)
            assertTrue(runBlocking {session.setSpeed(4)});Thread.sleep(1200)
            assertEquals(4,session.playerMetrics().speed);assertTrue(runBlocking {session.setSpeed(1)})
            waitFor("Rewind history unavailable") {session.playerMetrics().rewindSnapshots>3}
            val snapshots=session.playerMetrics().rewindSnapshots
            assertTrue(runBlocking {session.setRewinding(true)});Thread.sleep(250)
            assertTrue("Rewind consumed no snapshots",session.playerMetrics().rewindSnapshots<snapshots)
            assertTrue(runBlocking {session.setRewinding(false)})
            sample("ff-rewind",true)
            val gameplayStarted=SystemClock.elapsedRealtime()
            var nextSample=0L
            while(SystemClock.elapsedRealtime()-gameplayStarted<seconds*1000L) {
                val elapsed=SystemClock.elapsedRealtime()-gameplayStarted
                // The normal 45-second autosave briefly pauses the core under its lock.
                waitFor("Gameplay did not resume after checkpoint") {session.state.value==SessionState.RUNNING}
                if(blob) {
                    press(if(inputs%4<2) GbaButton.RIGHT else GbaButton.UP,600)
                    press(GbaButton.A,200);inputs+=2
                } else {
                    // Customise dice, roll, then accept battle rolls; repeat after win/loss.
                    press(GbaButton.RIGHT);inputs++
                    repeat(3) {press(GbaButton.A);inputs++}
                    press(GbaButton.START);inputs++
                }
                waitFor("Gameplay remained paused") {session.state.value==SessionState.RUNNING}
                assertNull("Persistence error during gameplay",session.persistenceError.value)
                if(elapsed>=nextSample) {visibleFrame();sample("play-${elapsed/1000}",true);nextSample+=60000}
                Thread.sleep(250)
            }
            assertTrue("No video change across input/gameplay",hashes.size>2)
            assertTrue("No nonzero audio samples from the game",audioCounter("nonzeroSamplesForTest")>0)
            assertTrue(runBlocking {session.checkpoint()})
            sample("gameplay-end",true)
            i.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {p ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(p).use {it.readBytes()}
            }
            waitFor("Home lifecycle did not settle") {compose.activity.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.CREATED && model().lifecycleApplied.isCompleted && session.state.value==SessionState.PAUSED}
            val paused=session.playerMetrics().frames;Thread.sleep(600);assertEquals(paused,session.playerMetrics().frames)
            // Preserve ActivityScenario's MAIN/LAUNCHER identity on older Android task stacks.
            i.targetContext.startActivity(Intent(compose.activity.intent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor("Foreground failed") {session.state.value==SessionState.RUNNING && session.playerMetrics().frames>paused}
            visibleFrame();sample("foreground",true)
            compose.runOnUiThread {model().exit()};waitFor("Exit failed") {!model().playing}
            compose.runOnUiThread {model().resumeLast()};waitFor("Continue failed") {model().playing && model().session.state.value==SessionState.RUNNING}
            visibleFrame();sample("continue",true)
            completed=true
        } finally {
            model().session.input.releaseAll()
            if(!completed) runCatching {sample("failure",true)}
            File(output,"result.json").writeText(JSONObject().put("completed",completed).put("game",game).put("romSha256",sha)
                .put("requestedGameplaySeconds",seconds).put("totalElapsedMs",SystemClock.elapsedRealtime()-started)
                .put("inputActions",inputs).put("distinctSampledFrameHashes",hashes.size)
                .put("scope","scripted homebrew gameplay; not human acceptance, not level-completion proof").toString())
            compose.runOnUiThread {model().display(oldDisplay)}
            runBlocking {model().session.stop()}
        }
    }
}
