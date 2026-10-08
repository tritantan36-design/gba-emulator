@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import dev.gbalite.renderer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
import kotlin.math.pow

class RendererExperienceTest {
    private var watchdog: Thread?=null
    @Before fun diagnosticWatchdog() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        watchdog=Thread({
            try { while(!Thread.currentThread().isInterrupted) {
                val tick=CountDownLatch(1);Handler(Looper.getMainLooper()).post { tick.countDown() }
                if(!tick.await(4,TimeUnit.SECONDS)) {
                    val stacks=Thread.getAllStackTraces().entries.filter { it.key.name=="main" || it.key.name.startsWith("GLThread") || it.key.name.startsWith("GbaRendererGL") || it.key.name.contains("Dispatcher") }
                        .joinToString("\n\n") { (thread,frames)->thread.name+" "+thread.state+"\n"+frames.joinToString("\n") }
                    File(app.filesDir,"phase4-watchdog.txt").writeText(stacks)
                }
                Thread.sleep(1000)
            } } catch(_: InterruptedException) { }
        },"renderer-test-watchdog").apply { isDaemon=true;start() }
    }
    @After fun stopWatchdog() { watchdog?.interrupt() }
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun open(name: String): PlayerViewModel {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation(); val uri=Uri.parse("content://dev.gbalite.app.test.rom/$name.gba")
        // This OEM leaves the freshly installed test APK stopped. Resolve its standalone
        // provider from the test shell first; production SAF access remains unchanged.
        i.uiAutomation.executeShellCommand("content query --uri $uri").use { descriptor ->
            val result=android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            check(result.contains("Row: 0")) { "Test ROM provider unavailable: $result" }
        }
        i.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        i.targetContext.contentResolver.openInputStream(uri)?.use { check(it.read()>=0) }
        lateinit var m: PlayerViewModel
        compose.runOnUiThread { m=model();m.open(uri) }
        try { compose.waitUntil(15000) { compose.onAllNodesWithText("菜单",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() } }
        catch(e: Throwable) { error("ROM=$name playing=${m.playing} loading=${m.loading} message=${m.message} session=${m.session.state.value}: $e") }
        compose.waitUntil(5000) { runCatching { surface().diagnostics().viewport.width>0 }.getOrDefault(false) }
        Thread.sleep(100);return m
    }
    private fun surface(): OriginalSurface {
        var result: OriginalSurface?=null
        compose.runOnUiThread {
            fun find(view: View): OriginalSurface? {
                if(view is OriginalSurface) return view
                if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            result=find(compose.activity.window.decorView)
        }
        return checkNotNull(result)
    }
    private fun capture(): Bitmap {
        repeat(30) {
            var image: Bitmap?=null
            compose.runOnUiThread { val s=surface();if(s.isAvailable && s.diagnostics().draws>=2) image=s.bitmap }
            if(image!=null) return image!!
            Thread.sleep(100)
        }
        error("Actual TextureView Surface capture failed")
    }
    private fun select(m: PlayerViewModel,mode: DisplayMode,scale: ScaleMode=ScaleMode.FIT) {
        compose.runOnUiThread { m.display(DisplaySettings(mode,scale)) }
        compose.waitUntil(5000) { runCatching { surface().diagnostics().mode==mode && surface().diagnostics().viewport.width>0 }.getOrDefault(false) }
        Thread.sleep(100)
    }
    private fun pixel(image: Bitmap,x: Float,y: Float): Int {
        val v=surface().diagnostics().viewport
        return image.getPixel((v.x+x*v.width).toInt().coerceIn(0,image.width-1),
            (image.height-v.y-v.height+y*v.height).toInt().coerceIn(0,image.height-1))
    }
    private fun battery(m: PlayerViewModel): ByteArray {
        val dir=File(compose.activity.filesDir,"persistence/${m.session.gameId!!.value}/battery")
        val name=org.json.JSONObject(File(dir,"current.json").readText()).getString("current")
        return File(dir,"$name/battery.sav").readBytes()
    }
    private fun verifyColor(mode: DisplayMode) {
        // Rotation replaces the TextureView before its asynchronous EGL resources
        // are ready. Wait for the same resource invariants asserted below rather
        // than treating a fixed 500ms sleep as a renderer acknowledgement.
        compose.waitUntil(5000) {runCatching {
            val d=surface().diagnostics()
            d.mode==mode && d.textures==1 && d.programs in 1..4 && d.buffers==2 &&
                d.activeSurfaces==1 && d.viewport.width>0
        }.getOrDefault(false)}
        val s=surface();val d=s.diagnostics();val v=d.viewport
        assertEquals(2*v.width,3*v.height)
        assertTrue(v.width<=s.width && v.height<=s.height)
        assertEquals(1,d.textures);assertTrue(d.programs in 1..4);assertEquals(2,d.buffers);assertEquals(1,d.activeSurfaces)
        val image=capture()
        try {
            val black=pixel(image,15f/240,112f/160);val white=pixel(image,45f/240,112f/160)
            assertTrue(Color.red(black)<8 && Color.green(black)<8 && Color.blue(black)<8)
            assertTrue("Actual Surface white mode=$mode",Color.red(white)>225 && Color.green(white)>225 && Color.blue(white)>225)
            val grays=listOf(10f,60f,120f,180f,230f).map { Color.red(pixel(image,it/240,12f/160)) }
            assertTrue("Monotonic grayscale $mode: $grays",grays.zipWithNext().all { (a,b)->a<b })
            val primaries=listOf(75f,105f,135f).map { pixel(image,it/240,112f/160) }
            val expected=if(mode==DisplayMode.GBA_COLOR) listOf(
                doubleArrayOf(.82,.125,.195),doubleArrayOf(.24,.665,.075),doubleArrayOf(-.06,.21,.73)
            ).map { row->row.map { (it*.94).coerceIn(0.0,1.0).pow(1.0/2.2)*255 } } else null
            if(expected!=null) primaries.forEachIndexed { i,p ->
                listOf(Color.red(p),Color.green(p),Color.blue(p)).forEachIndexed { j,c ->
                    assertTrue("Color matrix channel=$i/$j actual=$c expected=${expected[i][j]}",kotlin.math.abs(c-expected[i][j])<6)
                }
            } else {
                assertTrue("Red $mode ${Integer.toHexString(primaries[0])}",Color.red(primaries[0])>225 && Color.green(primaries[0])<10)
                assertTrue("Green $mode ${Integer.toHexString(primaries[1])}",Color.green(primaries[1])>225 && Color.blue(primaries[1])<10)
                assertTrue("Blue $mode ${Integer.toHexString(primaries[2])}",Color.blue(primaries[2])>225 && Color.red(primaries[2])<10)
            }
        } finally { image.recycle() }
    }
    @Test fun fourModesScaleTwentyFourRotationsLifecycleAndActualColor() {
        val m=open("color-pattern");val original=m.displaySettings;val session=m.session
        try {
            for(mode in DisplayMode.entries) for(scale in ScaleMode.entries) {
                select(m,mode,scale)
                for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                    compose.runOnUiThread { compose.activity.requestedOrientation=orientation }
                    Thread.sleep(500);compose.waitForIdle();assertSame(m,model());assertSame(session,m.session)
                    verifyColor(mode)
                    val s=surface();assertEquals(Viewport.calculate(s.width,s.height,scale),s.diagnostics().viewport)
                    compose.runOnUiThread {
                        val location=IntArray(2);s.getLocationOnScreen(location)
                        if(orientation!=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                            assertEquals("Landscape Surface fills window width",compose.activity.window.decorView.width,s.width)
                            assertEquals("Landscape Surface fills window height",compose.activity.window.decorView.height,s.height)
                            assertEquals(0,location[0]);assertEquals(0,location[1])
                        } else {
                            val insets=androidx.core.view.ViewCompat.getRootWindowInsets(s)!!
                            assertTrue("Portrait status bar stays visible",insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()))
                            assertTrue("Portrait Surface stays below status bar",location[1]>=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top)
                        }
                    }
                }
                val context=surface().diagnostics().contextId
                compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                // Stopped Compose has no frame clock; wait in wall time, never waitForIdle here.
                Thread.sleep(350)
                compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                compose.waitUntil(5000) { session.state.value==dev.gbalite.session.SessionState.RUNNING }
                Thread.sleep(250);verifyColor(mode);assertTrue(surface().diagnostics().contextId>context)
            }
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            compose.waitUntil(5000) { runCatching { surface().diagnostics().textures==1 }.getOrDefault(false) }
            assertSame(m,model());verifyColor(DisplayMode.LCD)
        } finally { compose.runOnUiThread { m.display(original) };runBlocking { m.session.stop() } }
    }
    @Test fun shaderFailuresRestoreOriginalWithoutCoreRestart() {
        val m=open("color-pattern");val original=m.displaySettings
        try {
            for(stage in FailureStage.entries) {
                surface().failModeForTest(DisplayMode.GBA_COLOR,stage)
                compose.runOnUiThread { m.display(DisplaySettings(DisplayMode.GBA_COLOR)) }
                compose.waitUntil(5000) { m.displaySettings.displayMode==DisplayMode.ORIGINAL && surface().diagnostics().mode==DisplayMode.ORIGINAL }
                compose.waitForIdle();Thread.sleep(150)
                verifyColor(DisplayMode.ORIGINAL)
                assertTrue(surface().diagnostics().fallbacks>0)
                surface().failModeForTest(null);Thread.sleep(50)
            }
            assertTrue(m.session.playerMetrics().frames>0)
        } finally { compose.runOnUiThread { m.display(original) };runBlocking { m.session.stop() } }
    }
    @Test fun actualHomeStopsOwnAudioAndReturningResumes() {
        val m=open("persistence");val instrumentation=InstrumentationRegistry.getInstrumentation()
        // Debug-only observation of the existing native PCM test counter, not a UI/core shortcut.
        val coreField=m.session.javaClass.getDeclaredField("core").apply { isAccessible=true }
        val core=checkNotNull(coreField.get(m.session))
        val handleField=core.javaClass.getDeclaredField("handle").apply { isAccessible=true }
        val handle=handleField.getLong(core)
        val bridgeClass=Class.forName("dev.gbalite.mgba.JniBridge")
        val bridge=bridgeClass.getDeclaredConstructor().newInstance()
        val playedMethod=bridgeClass.getDeclaredMethod("playedForTest",java.lang.Long.TYPE)
        fun played()=playedMethod.invoke(bridge,handle) as Long
        try {
            runBlocking { m.session.setSpeed(1) };val before=played()
            compose.waitUntil(5000) { played()>before }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use { }
            compose.waitUntil(5000) { m.session.state.value==dev.gbalite.session.SessionState.PAUSED }
            Thread.sleep(200);val silent=played();val frame=m.session.playerMetrics().frames
            Thread.sleep(1200);assertEquals(silent,played());assertEquals(frame,m.session.playerMetrics().frames)
            val paused=played();val pausedFrame=m.session.playerMetrics().frames
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            compose.waitUntil(5000) { m.session.state.value==dev.gbalite.session.SessionState.RUNNING && played()>silent }
            File(instrumentation.targetContext.filesDir,"phase4-home-audio-metrics.txt").writeText(
                "Before Home played=$before\nBackground start played=$silent frames=$frame\n"+
                "After 1200ms background played=$paused frames=$pausedFrame\nReturned foreground played=${played()}\nPASS\n")
            compose.waitForIdle();val image=capture()
            try { assertTrue(Color.red(pixel(image,.5f,.5f))>200) } finally { image.recycle() }
        } finally { runBlocking { m.session.stop() } }
    }
    @Test fun backgroundSettingsChangeActualMarginsAndPersistWithoutRestart() {
        val m=open("color-pattern");val original=m.displaySettings;val session=m.session
        try {
            select(m,DisplayMode.ORIGINAL,ScaleMode.FIT)
            compose.runOnUiThread { compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(500);compose.waitForIdle()
            compose.onNodeWithText("菜单",useUnmergedTree=true).performClick()
            compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
            compose.onNodeWithText("显示").performClick()
            for(tone in listOf(BackgroundTone.WHITE,BackgroundTone.BLACK)) {
                compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("background-${tone.name}"))
                compose.onNodeWithTag("background-${tone.name}",useUnmergedTree=true).performClick()
                val file=File(compose.activity.filesDir,"settings/display-v1.settings")
                compose.waitUntil(5000) { m.displaySettings.background==tone &&
                    runCatching { DisplaySettings.decode(file.readText()).background==tone }.getOrDefault(false) }
                repeat(2) {compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
                compose.onNodeWithText("继续").performClick()
                compose.waitForIdle()
                val draws=surface().diagnostics().draws
                compose.waitUntil(5000) { surface().diagnostics().draws>=draws+2 }
                Thread.sleep(100)
                assertTrue(surface().diagnostics().viewport.x>0)
                val image=capture()
                try {
                    // Sample inside the side margin, away from device rounded corners.
                    val p=image.getPixel(surface().diagnostics().viewport.x/2,image.height/2)
                    val expected=if(tone==BackgroundTone.WHITE) 255 else 0
                    assertEquals(expected,Color.red(p));assertEquals(expected,Color.green(p));assertEquals(expected,Color.blue(p))
                } finally { image.recycle() }
                assertSame(session,m.session);verifyColor(DisplayMode.ORIGINAL)
                compose.activityRule.scenario.recreate();compose.waitForIdle()
                assertEquals(tone,model().displaySettings.background)
                // Compose dialogs are transient; reopen settings after Activity recreation.
                compose.onNodeWithText("菜单",useUnmergedTree=true).performClick()
                compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
                compose.onNodeWithText("显示").performClick()
            }
        } finally { compose.runOnUiThread { m.display(original) };runBlocking { m.session.stop() } }
    }
    @Test fun lcdAndSharpPreserveFinePatternAndRawScreenshot() {
        val m=open("lcd-pattern");val original=m.displaySettings
        try {
            for(mode in DisplayMode.entries) {
                select(m,mode,ScaleMode.INTEGER)
                val image=capture()
                try {
                    // Source pixel centers, not blended texel boundaries.
                    for(x in 10..25) {
                        val p=pixel(image,(x+.5f)/240,20.5f/160)
                        val value=Color.red(p)
                        if((x+20)%2==1) assertTrue("Fine white survives $mode: $value",value>220)
                        else assertTrue("Fine black survives $mode: $value",value<20)
                    }
                } finally { image.recycle() }
                val raw=android.graphics.BitmapFactory.decodeFile(m.images.screenshot(m.session.frames).path)
                assertEquals(240,raw.width);assertEquals(160,raw.height)
                assertEquals(Color.WHITE,raw.getPixel(11,20));assertEquals(Color.BLACK,raw.getPixel(10,20));raw.recycle()
            }
        } finally { compose.runOnUiThread { m.display(original) };runBlocking { m.session.stop() } }
    }
    @Test fun everyModeFastForwardRewindStateImagesAndPerformance() {
        val m=open("persistence");val original=m.displaySettings;val log=StringBuilder()
        val gpuWindowNs=java.util.concurrent.atomic.AtomicLong();val gpuWindowSamples=java.util.concurrent.atomic.AtomicLong()
        val listener=android.view.Window.OnFrameMetricsAvailableListener { _,metrics,_ ->
            if(android.os.Build.VERSION.SDK_INT>=31) {
                val ns=metrics.getMetric(android.view.FrameMetrics.GPU_DURATION)
                if(ns>=0) { gpuWindowNs.addAndGet(ns);gpuWindowSamples.incrementAndGet() }
            }
        }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener,Handler(Looper.getMainLooper())) }
        try {
            for(mode in DisplayMode.entries) {
                select(m,mode)
                for(speed in listOf(1,2,4,8,1)) {
                    runBlocking { assertTrue(m.session.setSpeed(speed)) }
                    val start=m.session.playerMetrics();val render=surface().diagnostics();val time=System.nanoTime()
                    Thread.sleep(if(speed==8) 4000 else 1500)
                    val end=m.session.playerMetrics();val d=surface().diagnostics();val seconds=(System.nanoTime()-time)/1e9
                    val fps=(end.frames-start.frames)/seconds
                    val cpu=(d.cpuTotalNs-render.cpuTotalNs)/1e6/maxOf(1,d.draws-render.draws)
                    val gpu=(d.completionWaitTotalNs-render.completionWaitTotalNs)/1e6/maxOf(1,d.completionSamples-render.completionSamples)
                    log.append("$mode request=$speed actual=${fps/59.7275} displayFps=${(d.draws-render.draws)/seconds} cpuMs=$cpu gpuCompletionWaitUpperBoundMs=$gpu gpuSamples=${d.completionSamples-render.completionSamples} skippedDisplayLowerBound=${maxOf(0,end.frames-start.frames-d.draws+render.draws)} audioUnderrunDelta=${end.audioUnderruns-start.audioUnderruns}\n")
                    assertTrue("$mode/$speed fps=$fps",fps>59.7275*minOf(speed,4)*.85)
                    val screen=capture();try { assertTrue(Color.red(pixel(screen,.5f,.5f))>200) } finally { screen.recycle() }
                }
                runBlocking { m.session.setPaused(true) }
                assertTrue(runBlocking { m.session.saveState(1) });val saved=m.session.slots.value.first { it.slot==1 }
                val thumb=android.graphics.BitmapFactory.decodeFile(m.images.thumbnail(m.session.gameId,1,saved.createdAt)!!.path)
                assertEquals(240,thumb.width);assertEquals(160,thumb.height);thumb.recycle()
                assertTrue(runBlocking { m.session.checkpoint() });val savedBattery=battery(m)
                val rewindBefore=m.session.playerMetrics().rewindSnapshots;assertTrue(rewindBefore>=20)
                runBlocking { m.session.setPaused(false);m.session.setRewinding(true) }
                Thread.sleep(5000);assertTrue(m.session.playerMetrics().rewinding)
                assertTrue(m.session.playerMetrics().rewindSnapshots<rewindBefore)
                runBlocking { m.session.setRewinding(false) };Thread.sleep(200)
                val screen=capture();try { assertTrue(Color.red(pixel(screen,.5f,.5f))>200) } finally { screen.recycle() }
                assertEquals(mode,surface().diagnostics().mode)
                assertTrue(runBlocking { m.session.checkpoint() });assertArrayEquals(savedBattery,battery(m))
                assertTrue(runBlocking { m.session.loadState(1) })
                assertNull(m.session.persistenceError.value)
            }
        } finally {
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            log.append("Window GPU_DURATION averageMs=${if(gpuWindowSamples.get()>0) gpuWindowNs.get()/1e6/gpuWindowSamples.get() else -1.0} samples=${gpuWindowSamples.get()}; Window compositor scope, not isolated GLES Surface execution.\n")
            File(compose.activity.filesDir,"phase4-renderer-metrics.txt").writeText(log.toString())
            compose.runOnUiThread { m.display(original) };runBlocking { m.session.stop() }
        }
    }
}
