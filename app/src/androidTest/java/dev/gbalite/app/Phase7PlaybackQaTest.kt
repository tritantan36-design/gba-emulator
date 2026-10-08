@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import dev.gbalite.session.SessionState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** Ordinary playback QA. No sanitizer, mutation or specialist security checks. */
class Phase7PlaybackQaTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun open(name: String) {
        val uri=Uri.parse("content://dev.gbalite.app.test.rom/$name.gba")
        instrumentation.uiAutomation.executeShellCommand("content query --uri $uri").use {fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use {assertTrue(it.readText().contains("Row: 0"))}
        }
        instrumentation.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        foreground()
        compose.runOnUiThread {model().open(uri)}
        compose.waitUntil(15000) {!model().loading && model().playing && model().session.playerMetrics().frames>5}
        assertEquals(SessionState.RUNNING,model().session.state.value)
    }
    private fun foreground() {
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        compose.waitUntil(15000) {compose.activity.hasWindowFocus()}
    }
    private fun renderer(): dev.gbalite.renderer.RendererSnapshot {
        var result: dev.gbalite.renderer.RendererSnapshot?=null
        compose.runOnUiThread {
            fun find(view: android.view.View): dev.gbalite.renderer.OriginalSurface? {
                if(view is dev.gbalite.renderer.OriginalSurface) return view
                if(view is android.view.ViewGroup) for(n in 0 until view.childCount) find(view.getChildAt(n))?.let {return it}
                return null
            }
            result=checkNotNull(find(compose.activity.window.decorView)).diagnostics()
        }
        return checkNotNull(result)
    }
    @Test fun fourDisplayModesAtOneTwoFourAndEightTimesMeasured() {
        val original=model().displaySettings
        val evidence=File(compose.activity.filesDir,"phase7-playback-performance.jsonl")
        evidence.writeText("")
        try {
            open("phase7-stress-a")
            for(mode in DisplayMode.entries) for(speed in listOf(1,2,4,8)) {
                compose.runOnUiThread {model().display(DisplaySettings(mode))}
                compose.waitUntil(5000) {renderer().let {it.mode==mode && it.draws>=2}}
                assertTrue(runBlocking {model().session.setSpeed(speed)})
                Thread.sleep(400)
                val before=model().session.playerMetrics();val started=SystemClock.elapsedRealtime()
                Thread.sleep(2000)
                val elapsed=SystemClock.elapsedRealtime()-started;val after=model().session.playerMetrics()
                val fps=(after.frames-before.frames)*1000.0/elapsed
                val display=renderer()
                assertEquals(1,display.activeSurfaces);assertEquals(1,display.textures)
                assertTrue(display.programs in 1..4)
                evidence.appendText(JSONObject().put("mode",mode.name).put("speed",speed)
                    .put("elapsedMs",elapsed).put("frames",after.frames-before.frames).put("fps",fps)
                    .put("lateFramesDelta",after.lateFrames-before.lateFrames)
                    .put("underrunsDelta",after.audioUnderruns-before.audioUnderruns)
                    .put("renderer",display.toString()).toString()+"\n")
                assertTrue("Playback stopped in $mode at ${speed}x",after.frames>before.frames)
                // Measurements are retained even if device throughput varies; 8x is best effort.
                assertTrue(runBlocking {model().session.setSpeed(1)})
            }
        } finally {
            runBlocking {model().session.setSpeed(1);model().session.stop()}
            compose.runOnUiThread {model().exit();model().display(original)}
        }
    }
    @Test fun rtcWallClockTenMinutesWithFastForwardHomeStateAndRomReopen() {
        assumeTrue("Explicit -e phase7RtcLongRun true required",
            InstrumentationRegistry.getArguments().getString("phase7RtcLongRun")=="true")
        val file=File(compose.activity.filesDir,"phase7-rtc-wall-clock.jsonl");file.writeText("")
        val started=SystemClock.elapsedRealtime()
        // The legal RTC probe renders each BCD byte as a meter column. Read the
        // public framebuffer API, never a UI-to-JNI shortcut. Quantization is known.
        val pixels=ByteBuffer.allocateDirect(240*160*4)
        fun height(channel: Int): Int {
            val x=channel*34+16
            return (0 until 160).count {y ->
                val offset=(y*240+x)*4
                (pixels.get(offset+channel%3).toInt() and 255)>200
            }
        }
        fun checkClock() {
            compose.waitUntil(5000) {
                if(!model().session.frames.copyFrame(pixels)) false else {
                    val actual=height(6);val now=java.time.ZonedDateTime.now()
                    (-2L..2L).any {offset ->
                        val seconds=now.plusSeconds(offset).second
                        val bcd=(seconds/10)*16+seconds%10
                        actual==bcd*140/255
                    }
                }
            }
            file.appendText(JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("wallTime",java.time.ZonedDateTime.now().toString())
                .put("secondsMeterHeight",height(6)).put("speed",model().session.playerMetrics().speed).toString()+"\n")
        }
        try {
            open("rtc-probe");var cycle=0
            while(SystemClock.elapsedRealtime()-started<610000) {
                assertTrue(runBlocking {model().session.setSpeed(listOf(1,2,4,8)[cycle%4])})
                Thread.sleep(300);checkClock()
                assertTrue(runBlocking {model().session.setSpeed(1)})
                if(cycle%5==0) {
                    assertTrue(runBlocking {model().session.saveState()})
                    instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    compose.waitUntil(5000) {model().session.state.value==SessionState.PAUSED && model().sensors.listeners==0}
                    Thread.sleep(3000);foreground()
                    compose.waitUntil(5000) {model().session.state.value==SessionState.RUNNING}
                    assertTrue(runBlocking {model().session.loadState()});Thread.sleep(200);checkClock()
                }
                if(cycle==10 || cycle==20) {
                    compose.runOnUiThread {model().exit()};compose.waitUntil(5000) {!model().playing};open("rtc-probe");checkClock()
                }
                Thread.sleep(20000);cycle++
            }
            checkClock();assertTrue(SystemClock.elapsedRealtime()-started>=600000)
        } finally {runBlocking {model().session.stop()};compose.runOnUiThread {model().exit()}}
    }
    @Test fun controlledStrictModeOrdinaryImportPlaybackImagesAndState() {
        assumeTrue("Thread-policy listener requires API 28",android.os.Build.VERSION.SDK_INT>=28)
        val violations=java.util.concurrent.CopyOnWriteArrayList<String>()
        var previous: android.os.StrictMode.ThreadPolicy?=null
        compose.runOnUiThread {
            previous=android.os.StrictMode.getThreadPolicy()
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyListener(java.util.concurrent.Executor {it.run()}) {violation ->
                    if(violation.stackTrace.any {it.className.startsWith("dev.gbalite.") &&
                        !it.className.startsWith("dev.gbalite.app.Phase7")}) violations.add(violation.toString()+"\n"+violation.stackTraceToString())
                }.build())
        }
        try {
            open("phase7-stress-b")
            assertTrue(runBlocking {model().session.saveState(1)})
            assertTrue(runBlocking {model().session.loadState(1)})
            compose.runOnUiThread {model().screenshot()}
            compose.waitUntil(10000) {model().message?.startsWith("截图已保存")==true}
            assertTrue(runBlocking {model().session.checkpoint()})
            compose.runOnUiThread {model().exit()};compose.waitUntil(10000) {!model().playing}
            Thread.sleep(500)
        } finally {
            compose.runOnUiThread {previous?.let {android.os.StrictMode.setThreadPolicy(it)}}
            File(compose.activity.filesDir,"phase7-strictmode.txt").writeText(
                "Controlled ordinary import/playback/state/screenshot/checkpoint/exit. App-origin main-thread violations=${violations.size}\n"+violations.joinToString("\n\n"))
        }
        assertTrue("App-origin main-thread IO violations; see phase7-strictmode.txt",violations.isEmpty())
    }
}
