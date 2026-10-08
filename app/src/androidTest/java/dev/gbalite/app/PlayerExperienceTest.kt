@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.GbaButton
import dev.gbalite.core.DisplayMode
import dev.gbalite.core.DisplaySettings
import dev.gbalite.input.*
import dev.gbalite.player.TouchControls
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerExperienceTest {
    @Test fun fastForwardHoldToggleRewindAndBackground() {
        val m=open()
        compose.onNodeWithText("菜单",useUnmergedTree=true).performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2×",useUnmergedTree=true).performScrollTo().performClick()
        compose.waitUntil(5000) { m.session.playerMetrics().speed==2 }
        compose.onNodeWithText("菜单 · 2×").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("继续").performClick()
        compose.waitUntil(5000) { m.session.playerMetrics().speed==2 && compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("菜单 · 2×").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("关闭快进（恢复1×）",useUnmergedTree=true).performScrollTo().performClick()
        compose.waitUntil(5000) { m.session.playerMetrics().speed==1 }
        for(n in listOf(4,8)) {
            compose.waitUntil(5000) { compose.onAllNodesWithText("菜单",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("菜单",useUnmergedTree=true).performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("$n×",useUnmergedTree=true).performScrollTo().performClick()
            compose.waitUntil(5000) { m.session.playerMetrics().speed==n && compose.onAllNodesWithText("菜单 · $n×").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("菜单 · $n×").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("关闭快进（恢复1×）",useUnmergedTree=true).performScrollTo().performClick()
            compose.waitUntil(5000) { m.session.playerMetrics().speed==1 }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("菜单",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("菜单",useUnmergedTree=true).performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        // Select 2x again, then reopen to exercise hold at that selected multiplier.
        compose.onNodeWithText("2×",useUnmergedTree=true).performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("菜单 · 2×").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("菜单 · 2×").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("快进（按住）",useUnmergedTree=true).performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("按住快进 2×").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("按住快进 2×").performTouchInput { down(center) }
        Thread.sleep(150); assertEquals(2,m.session.playerMetrics().speed)
        compose.onNodeWithText("按住快进 2×").performTouchInput { up() }
        compose.waitUntil(5000) { m.session.playerMetrics().speed==1 }
        runBlocking { m.session.setSpeed(8) }; Thread.sleep(2500); runBlocking { m.session.setSpeed(1) }
        compose.onNodeWithText("完成").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("倒带（按住）",useUnmergedTree=true).performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("按住倒带").fetchSemanticsNodes().isNotEmpty() }
        val before=m.session.playerMetrics().rewindSnapshots
        compose.onNodeWithText("按住倒带").performTouchInput { down(center) }; Thread.sleep(600)
        assertTrue(m.session.playerMetrics().rewinding)
        assertTrue(m.session.playerMetrics().rewindSnapshots<before)
        compose.onNodeWithText("按住倒带").performTouchInput { up() }
        compose.waitUntil(5000) { !m.session.playerMetrics().rewinding }
        runBlocking { m.session.setSpeed(4) }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        Thread.sleep(200); assertEquals(1,m.session.playerMetrics().speed)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(5000) { m.session.state.value==dev.gbalite.session.SessionState.RUNNING }
        runBlocking { m.session.setSpeed(8); assertTrue(m.session.stop()) }
    }
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun assertVisibleGameFrame() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val deadline=System.currentTimeMillis()+5000
        do {
            var center: Pair<Int,Int>?=null
            compose.runOnUiThread {
                fun find(view: android.view.View): dev.gbalite.renderer.OriginalSurface? {
                    if(view is dev.gbalite.renderer.OriginalSurface) return view
                    if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                find(compose.activity.window.decorView)?.let {
                    val xy=IntArray(2); it.getLocationOnScreen(xy)
                    if(it.width>0 && it.height>0) center=(xy[0]+it.width/2) to (xy[1]+it.height/2)
                }
            }
            val screen=instrumentation.uiAutomation.takeScreenshot()
            val visible=if(screen!=null && center!=null) {
                val (x,y)=center!!
                x in 0 until screen.width && y in 0 until screen.height && android.graphics.Color.red(screen.getPixel(x,y))>200
            } else false
            screen?.recycle()
            if(visible) return
            Thread.sleep(50)
        } while(System.currentTimeMillis()<deadline)
        fail("Actual game Surface must remain visible after rotation/background, not merely retain native frames")
    }
    private fun open(): PlayerViewModel {
        val i=InstrumentationRegistry.getInstrumentation(); val uri=Uri.parse("content://dev.gbalite.app.test.rom/persistence.gba")
        i.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        var m: PlayerViewModel?=null
        compose.runOnUiThread { m=model(); m!!.open(uri) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("菜单").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(200); return m!!
    }
    @Test fun framebufferThumbnailScreenshotAndOrientationKeepCore() {
        val m=open(); val session=m.session
        // Phase3's red-screen assertion assumes unfiltered pixels. Later suites
        // persist LCD/Integer preferences, whose scanlines change screenshot colors.
        val originalDisplay=m.displaySettings
        try {
        compose.runOnUiThread {m.display(DisplaySettings(DisplayMode.ORIGINAL))}
        assertVisibleGameFrame()
        runBlocking { assertTrue(session.saveState(1)) }
        val meta=session.slots.value.first { it.slot==1 }
        val thumb=m.images.thumbnail(session.gameId,1,meta.createdAt)!!
        val image=BitmapFactory.decodeFile(thumb.path); assertEquals(240,image.width); assertEquals(160,image.height); image.recycle()
        val shot=m.images.screenshot(session.frames)
        val shotImage=BitmapFactory.decodeFile(shot.path); assertEquals(240,shotImage.width); assertEquals(160,shotImage.height)
        // Homebrew framebuffer only: screen pixels, without menu/control colors.
        assertTrue(android.graphics.Color.red(shotImage.getPixel(120,80))>200); shotImage.recycle()
        var previous=session.playerMetrics().frames
        for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
            compose.runOnUiThread { compose.activity.requestedOrientation=orientation }
            Thread.sleep(600); compose.waitForIdle()
            compose.runOnUiThread { assertSame(m,model()) }; assertSame(session,m.session)
            assertTrue(session.playerMetrics().frames>previous); previous=session.playerMetrics().frames
            assertVisibleGameFrame()
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitUntil(5000) { session.state.value==dev.gbalite.session.SessionState.RUNNING }
            assertVisibleGameFrame()
        }
        runBlocking { session.setPaused(true); session.saveState(); session.setForeground(false); session.setForeground(true) }
        assertEquals(dev.gbalite.session.SessionState.PAUSED,session.state.value)
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        assertEquals(dev.gbalite.session.SessionState.PAUSED,session.state.value)
        runBlocking { session.setPaused(false); assertTrue(session.loadState()); assertTrue(session.stop()) }
        } finally {compose.runOnUiThread {m.display(originalDisplay)}}
    }
    @Test fun realPointerEventsSlideMultitouchCancelAndProfileRoundTrip() {
        val down=mutableSetOf<GbaButton>(); val r=InputRouter { b,v-> if(v) down+=b else down-=b }
        compose.runOnUiThread {
            val view=TouchControls(compose.activity,r); view.layout(0,0,1000,1000)
            val p=InputProfile.default(false)
            fun event(action: Int,points: List<Pair<Int,Pair<Float,Float>>>) {
                val props=points.map { (id,_)->MotionEvent.PointerProperties().apply { this.id=id;toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
                val coords=points.map { (_,xy)->MotionEvent.PointerCoords().apply { x=xy.first;y=xy.second;pressure=1f;size=1f } }.toTypedArray()
                val e=MotionEvent.obtain(1,2,action,points.size,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
                view.onTouchEvent(e);e.recycle()
            }
            val a=1 to (830f to 350f); val b=2 to (650f to 530f)
            event(MotionEvent.ACTION_DOWN,listOf(0 to (240f to 300f))); assertEquals(setOf(GbaButton.UP),down)
            event(MotionEvent.ACTION_MOVE,listOf(0 to (380f to 430f))); assertEquals(setOf(GbaButton.RIGHT),down)
            event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),listOf(0 to (240f to 300f),a))
            event(MotionEvent.ACTION_POINTER_DOWN or (2 shl 8),listOf(0 to (240f to 300f),a,b))
            assertEquals(setOf(GbaButton.UP,GbaButton.A,GbaButton.B),down)
            event(MotionEvent.ACTION_CANCEL,listOf(0 to (240f to 300f),a,b)); assertTrue(down.isEmpty())
            val changed=p.copy(opacity=.4f,controls=p.controls.map { if(it.key=="A") it.copy(x=.8f,size=InputProfile.MAX_SIZE) else it })
            val store=PrivateInputProfiles(java.io.File(compose.activity.cacheDir,"phase3-profiles-test"))
            store.write(changed); assertEquals(changed,store.read(false)); assertEquals(InputProfile.default(true),store.read(true))
            val all=p.scaledSizes(1.4f)
            store.write(all); assertEquals(all,store.read(false)); assertEquals(InputProfile.default(true),store.read(true))
        }
    }
    @Test fun syntheticHidDisconnectReconnectAndTouchMerge() {
        val keys=mutableSetOf<GbaButton>(); val r=InputRouter { b,v->if(v) keys+=b else keys-=b }
        val h=HidInput(compose.activity,r)
        fun key(code: Int,action: Int)=android.view.KeyEvent(1,2,action,code,0,0,123,0,0,android.view.InputDevice.SOURCE_GAMEPAD)
        r.update("touch:0",setOf(GbaButton.A))
        for(code in listOf(96,99,102,103,108,109,19,20,21,22)) {
            assertTrue(h.key(key(code,0))); assertTrue(keys.contains(GamepadMapping.keys[code]))
            assertTrue(h.key(key(code,1)))
        }
        val props=arrayOf(MotionEvent.PointerProperties().apply { id=0 })
        val coords=arrayOf(MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_X,.8f);setAxisValue(MotionEvent.AXIS_Y,-.8f) })
        val motion=MotionEvent.obtain(1,2,MotionEvent.ACTION_MOVE,1,props,coords,0,0,1f,1f,123,0,android.view.InputDevice.SOURCE_JOYSTICK,0)
        assertTrue(h.motion(motion));motion.recycle()
        assertEquals(setOf(GbaButton.A,GbaButton.RIGHT,GbaButton.UP),keys);h.onInputDeviceRemoved(123)
        h.key(key(99,0)); h.onInputDeviceRemoved(123); assertEquals(setOf(GbaButton.A),keys)
        h.onInputDeviceAdded(123);h.key(key(99,0));assertEquals(setOf(GbaButton.A,GbaButton.B),keys)
        h.onInputDeviceChanged(123);assertEquals(setOf(GbaButton.A),keys);r.releaseAll()
    }
}



