@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Debug
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
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** TEST ONLY. Explicit opt-in avoids silently running a one-hour test in PR smoke. */
class Phase7StressTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun waitFor(message: String,timeout: Long=15000,condition: ()->Boolean) {
        try { compose.waitUntil(timeout,condition) }
        catch(e: Throwable) {throw AssertionError("$message; lifecycle=${compose.activity.lifecycle.currentState}; lifecycleApplied=${model().lifecycleApplied.isCompleted}; loading=${model().loading}; playing=${model().playing}; message=${model().message}; state=${model().session.state.value}; metrics=${model().session.playerMetrics()}",e)}
    }
    private fun pressHome() {
        i.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {p ->
            val reply=android.os.ParcelFileDescriptor.AutoCloseInputStream(p).bufferedReader().use {it.readText()}
            assertTrue("Home shell input reported an error: $reply",reply.isBlank())
        }
    }
    private fun open(letter: Char='a') {
        compose.waitForIdle()
        val uri=Uri.parse("content://dev.gbalite.app.test.rom/phase7-stress-$letter.gba")
        i.uiAutomation.executeShellCommand("content query --uri $uri").use {p ->
            val reply=android.os.ParcelFileDescriptor.AutoCloseInputStream(p).bufferedReader().use {it.readText()}
            assertTrue("Test provider not ready: $reply",reply.contains("Row: 0"))
        }
        i.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        // Provider wake-up and previous Home cycles can leave the OEM test activity
        // stopped. Restore the actual Activity; never override Session foreground.
        i.targetContext.startActivity(Intent(i.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        waitFor("Activity did not regain focus") {compose.activity.hasWindowFocus()}
        compose.runOnUiThread {model().open(uri)}
        waitFor("ROM load: ${model().message}") {!model().loading && model().playing && model().session.playerMetrics().frames>10}
        waitFor("Session running") {model().session.state.value==SessionState.RUNNING}
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
    private fun actualFrame() {
        waitFor("Actual game Surface missing or black") {
            var ok=false
            compose.runOnUiThread {surface()?.let {s ->
                val d=s.diagnostics()
                val image=if(s.isAvailable && d.draws>=2) s.bitmap else null
                if(image!=null) {
                    val p=image.getPixel(image.width/2,image.height/2)
                    ok=android.graphics.Color.red(p)>200
                    image.recycle()
                }
            }}
            ok
        }
    }
    private fun sample(file: File,event: String,started: Long) {
        val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory)
        val m=model().session.playerMetrics()
        val status=File("/proc/self/status").readLines().filter {it.startsWith("VmRSS:") || it.startsWith("Threads:")}
        val row=JSONObject().put("event",event).put("elapsedMs",SystemClock.elapsedRealtime()-started)
            .put("timestamp",java.time.Instant.now().toString()).put("pid",android.os.Process.myPid())
            .put("nativeHeapBytes",Debug.getNativeHeapAllocatedSize()).put("javaUsedBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
            .put("totalPssKiB",memory.totalPss).put("procStatus",status.joinToString(";"))
            .put("fdCount",File("/proc/self/fd").list()?.size ?: -1).put("jvmThreads",Thread.getAllStackTraces().size)
            .put("frames",m.frames).put("underruns",m.audioUnderruns).put("rewindBytes",m.rewindBytes)
            .put("renderer",surface()?.diagnostics().toString()).put("sessionState",model().session.state.value.name)
        file.appendText(row.toString()+"\n")
    }
    @Test fun sixtyMinuteMixedLifecycleAndResourceRun() {
        assumeTrue("Explicit -e phase7LongRun true required",InstrumentationRegistry.getArguments().getString("phase7LongRun")=="true")
        val file=File(i.targetContext.filesDir,"phase7-longrun-${System.currentTimeMillis()}.jsonl")
        val started=SystemClock.elapsedRealtime()
        val original=model().displaySettings
        val oldOrientation=compose.activity.requestedOrientation
        var completed=false
        try {
            open();compose.runOnUiThread {model().display(DisplaySettings(DisplayMode.ORIGINAL))};actualFrame()
            sample(file,"start",started)
            // Real elapsed time, no acceleration of the test deadline.
            for(cycle in 0 until 60) {
                val cycleStart=SystemClock.elapsedRealtime()
                val m=model();assertTrue(runBlocking {m.session.setSpeed(listOf(1,2,4,8)[cycle%4])})
                Thread.sleep(1000);assertTrue(runBlocking {m.session.setSpeed(1)})
                runBlocking {m.session.input.setButton(GbaButton.A,true)};Thread.sleep(80);m.session.input.releaseAll()
                assertTrue(runBlocking {m.session.saveState()});assertTrue(runBlocking {m.session.loadState()})
                assertTrue(runBlocking {m.session.saveState(cycle%4+1)});assertTrue(runBlocking {m.session.loadState(cycle%4+1)})
                if(m.session.playerMetrics().rewindSnapshots>2) {assertTrue(runBlocking {m.session.setRewinding(true)});Thread.sleep(250);assertTrue(runBlocking {m.session.setRewinding(false)})}
                if(cycle<50) {
                    for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                        compose.runOnUiThread {compose.activity.requestedOrientation=orientation};Thread.sleep(400);actualFrame()
                    }
                    Thread.sleep(10000)
                    pressHome()
                    // Periodic persistence also briefly enters PAUSED. Wait for the
                    // actual Activity onStop AND its queued lifecycle command
                    // completion before measuring background frames.
                    waitFor("Home did not stop Activity and pause") {
                        compose.activity.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.CREATED &&
                            model().lifecycleApplied.let {it.isCompleted && !it.isCancelled} &&
                            model().session.state.value==SessionState.PAUSED
                    }
                    val before=model().session.playerMetrics().frames;Thread.sleep(5000)
                    assertEquals("Core advanced while backgrounded",before,model().session.playerMetrics().frames)
                    i.targetContext.startActivity(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    waitFor("Foreground did not resume") {model().session.state.value==SessionState.RUNNING}
                    Thread.sleep(10000);actualFrame()
                }
                if(cycle<50) {compose.runOnUiThread {model().exit()};waitFor("Session did not close") {!model().playing};open("abc"[(cycle+1)%3])}
                if(cycle%6==0) {compose.runOnUiThread {compose.activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)};actualFrame()}
                // 120 selections, including duplicate ORIGINAL (90 actual changes).
                // A separate opt-in regression verifies 100 actual mode changes.
                for(mode in listOf(DisplayMode.entries[cycle%4],DisplayMode.ORIGINAL)) {
                    compose.runOnUiThread {model().display(DisplaySettings(mode))};Thread.sleep(150)
                }
                actualFrame();assertTrue(runBlocking {model().session.checkpoint()})
                sample(file,"cycle-$cycle",started)
                while(SystemClock.elapsedRealtime()-cycleStart<60000) Thread.sleep(250)
            }
            assertTrue(SystemClock.elapsedRealtime()-started>=3600000)
            completed=true
        } finally {
            runBlocking {model().session.setSpeed(1);model().session.setRewinding(false);model().session.stop()}
            compose.runOnUiThread {model().exit();model().display(original);compose.activity.requestedOrientation=oldOrientation}
            Thread.sleep(1500);sample(file,if(completed) "complete" else "incomplete",started)
        }
    }
    @Test fun homeDuringCheckpointStopsFramesAfterLifecycleAcknowledgement() {
        assumeTrue("Explicit -e phase7LifecycleRace true required",InstrumentationRegistry.getArguments().getString("phase7LifecycleRace")=="true")
        val started=SystemClock.elapsedRealtime()
        val file=File(i.targetContext.filesDir,"phase7-lifecycle-ack-${System.currentTimeMillis()}.jsonl")
        val original=model().displaySettings
        var completed=false
        try {
            open();compose.runOnUiThread {model().display(DisplaySettings(DisplayMode.ORIGINAL))};actualFrame()
            sample(file,"start",started)
            repeat(50) {cycle ->
                runBlocking {
                    val checkpoint=async(Dispatchers.IO) {model().session.checkpoint()}
                    pressHome()
                    waitFor("Background lifecycle command not completed") {
                        compose.activity.lifecycle.currentState==androidx.lifecycle.Lifecycle.State.CREATED &&
                            model().lifecycleApplied.let {it.isCompleted && !it.isCancelled} &&
                            model().session.state.value==SessionState.PAUSED
                    }
                    assertTrue(checkpoint.await())
                }
                val frames=model().session.playerMetrics().frames
                Thread.sleep(5000)
                assertEquals("Frames advanced after background acknowledgement",frames,model().session.playerMetrics().frames)
                sample(file,"cycle-$cycle",started)
                i.targetContext.startActivity(Intent(i.targetContext,MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                waitFor("Foreground did not resume") {model().session.state.value==SessionState.RUNNING}
                actualFrame()
            }
            completed=true
        } finally {
            runBlocking {model().session.stop()}
            compose.runOnUiThread {model().exit();model().display(original)}
            sample(file,if(completed) "complete" else "incomplete",started)
        }
    }
    @Test fun hundredActualShaderChangesKeepSurfaceAndFrames() {
        assumeTrue("Explicit -e phase7Shaders true required",InstrumentationRegistry.getArguments().getString("phase7Shaders")=="true")
        val started=SystemClock.elapsedRealtime()
        val file=File(i.targetContext.filesDir,"phase7-shader-changes-${System.currentTimeMillis()}.jsonl")
        val original=model().displaySettings
        var completed=false
        try {
            open();compose.runOnUiThread {model().display(DisplaySettings(DisplayMode.ORIGINAL))};actualFrame()
            val context=checkNotNull(surface()).diagnostics().contextId
            sample(file,"start",started)
            repeat(100) {index ->
                val requested=DisplayMode.entries[(index+1)%4]
                val before=checkNotNull(surface()).diagnostics()
                assertNotEquals(before.mode,requested)
                compose.runOnUiThread {model().display(DisplaySettings(requested))}
                waitFor("Mode $requested did not render without recreating Surface") {
                    var visible=false
                    compose.runOnUiThread {surface()?.let {s ->
                        val d=s.diagnostics()
                        if(d.mode==requested && d.contextId==context && d.draws>=before.draws+2 && s.isAvailable) {
                            s.bitmap?.let {image ->
                                visible=android.graphics.Color.red(image.getPixel(image.width/2,image.height/2))>32
                                image.recycle()
                            }
                        }
                    }}
                    visible
                }
                sample(file,"mode-$index-${requested.name}",started)
            }
            completed=true
        } finally {
            runBlocking {model().session.stop()}
            compose.runOnUiThread {model().exit();model().display(original)}
            sample(file,if(completed) "complete" else "incomplete",started)
        }
    }
    @Test fun hundredQuickAndSlotTransactionsAndHundredIoCycles() {
        open();val started=SystemClock.elapsedRealtime();val file=File(i.targetContext.filesDir,"phase7-transactions-${System.currentTimeMillis()}.jsonl")
        sample(file,"start",started)
        try {
            repeat(100) {n ->
                val s=model().session
                assertTrue("Quick Save $n",runBlocking {s.saveState()});assertTrue("Quick Load $n",runBlocking {s.loadState()})
                assertTrue("Slot Save $n",runBlocking {s.saveState(n%4+1)});assertTrue("Slot Load $n",runBlocking {s.loadState(n%4+1)})
                if(n%10==0) sample(file,"transaction-$n",started)
            }
            val fdBaseline=File("/proc/self/fd").list()!!.size
            val threadBaseline=Thread.getAllStackTraces().size
            repeat(100) {n ->
                compose.runOnUiThread {model().exit()};waitFor("close $n") {!model().playing};open("abc"[n%3]);actualFrame()
                val expected=i.context.assets.open("phase7-stress-${"abc"[n%3]}.gba").use {GameId.fromBytes(it.readBytes())}
                assertEquals("ROM identity $n",expected,model().session.gameId)
                assertTrue("Battery checkpoint $n",runBlocking {model().session.checkpoint()})
                assertTrue("State $n",runBlocking {model().session.saveState()})
                val image=runBlocking(kotlinx.coroutines.Dispatchers.IO) {model().images.screenshot(model().session.frames,expected)}
                assertTrue(image.length()>0);assertEquals(expected.value,image.parentFile!!.name)
                // Only this test-created screenshot is removed; user screenshots stay intact.
                assertTrue(image.delete())
                if(n%10==0) sample(file,"io-cycle-$n",started)
            }
            compose.runOnUiThread {model().exit()};waitFor("resource close") {!model().playing};Thread.sleep(2000)
            assertTrue("FDs grew beyond bounded warm-up allowance",File("/proc/self/fd").list()!!.size<=fdBaseline+12)
            assertTrue("Threads grew beyond bounded warm-up allowance",Thread.getAllStackTraces().size<=threadBaseline+12)
        } finally {compose.runOnUiThread {model().exit()};waitFor("final close") {!model().playing};Thread.sleep(1000);sample(file,"closed",started)}
    }
}
