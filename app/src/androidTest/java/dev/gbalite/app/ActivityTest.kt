@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule

@RunWith(AndroidJUnit4::class) class ActivityTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    @Test fun launchAndRecreate() {
        compose.onNodeWithText("添加游戏").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("添加游戏").assertExists()
    }
    @Test fun safOpenDocumentIsUsed() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val monitor=Instrumentation.ActivityMonitor(IntentFilterForSaf(),Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true)
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithText("添加游戏").performClick()
            instrumentation.waitForIdleSync()
            assertTrue(monitor.hits>0)
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun safResultNavigatesToPlayerAndRecreateReleasesCore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val data = Intent().setData(android.net.Uri.parse("content://dev.gbalite.app.test.rom/bringup.gba"))
        val monitor = Instrumentation.ActivityMonitor(IntentFilterForSaf(),
            Instrumentation.ActivityResult(Activity.RESULT_OK, data), true)
        instrumentation.addMonitor(monitor)
        var displayBefore: dev.gbalite.core.DisplaySettings?=null
        var player: PlayerViewModel?=null
        try {
            compose.onNodeWithText("添加游戏").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("打开游戏").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("打开游戏").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("菜单").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("菜单").assertExists()
            // This Phase 1 baseline asserts raw red, so choose Original explicitly rather
            // than inheriting a user/previous test's color-correction preference.
            compose.runOnUiThread {
                player=androidx.lifecycle.ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
                displayBefore=player!!.displaySettings
                player!!.display(displayBefore!!.copy(displayMode=dev.gbalite.core.DisplayMode.ORIGINAL))
            }
            // Real compositor pixels require the target window in front after synthetic SAF.
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            compose.waitUntil(5000) {compose.activity.hasWindowFocus()}
            assertRenderedRedFrameAndSaveEvidence()
            // Use the user's saved layout, rather than assuming the default A position.
            var a: dev.gbalite.input.TouchControl?=null
            compose.runOnUiThread {
                fun find(view: android.view.View): dev.gbalite.player.TouchControls? {
                    if(view is dev.gbalite.player.TouchControls) return view
                    if(view is android.view.ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                a=requireNotNull(find(compose.activity.window.decorView)).profile.controls.first { it.key=="A" }
            }
            compose.onNodeWithTag("touch-controls").performTouchInput { down(androidx.compose.ui.geometry.Offset(width*a!!.x,height*a!!.y)) }
            assertRenderedInputColor()
            compose.onNodeWithTag("touch-controls").performTouchInput { up() }
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            Thread.sleep(100)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertRenderedRedFrameAndSaveEvidence()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("菜单").assertExists()
        } finally {
            instrumentation.removeMonitor(monitor)
            displayBefore?.let { original ->
                compose.runOnUiThread {player?.display(original)}
                compose.waitUntil(5000) {java.io.File(compose.activity.filesDir,"settings/display-v1.settings").let {
                    it.exists() && dev.gbalite.core.DisplaySettings.decode(it.readText())==original
                }}
            }
        }
    }
    private fun IntentFilterForSaf() = android.content.IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*")
    }
    private fun frameRect(): android.graphics.Rect {
        var bounds: android.graphics.Rect? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun find(view: android.view.View): dev.gbalite.renderer.OriginalSurface? {
                if (view is dev.gbalite.renderer.OriginalSurface) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                    find(view.getChildAt(i))?.let { return it }
                }
                return null
            }
            val surface = requireNotNull(find(compose.activity.window.decorView))
            val xy = IntArray(2); surface.getLocationOnScreen(xy)
            bounds = android.graphics.Rect(xy[0], xy[1], xy[0]+surface.width, xy[1]+surface.height)
        }
        return requireNotNull(bounds)
    }
    private fun captureFrame(): android.graphics.Bitmap {
        val bounds = frameRect()
        val screen = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val frame = android.graphics.Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
        screen.recycle()
        return frame
    }
    private fun assertRenderedRedFrameAndSaveEvidence() {
        val deadline = System.currentTimeMillis()+5000
        while (true) {
            val frame = captureFrame()
            val pixel = frame.getPixel(frame.width/2, frame.height/2)
            val red = android.graphics.Color.red(pixel)>200 && android.graphics.Color.green(pixel)<25 && android.graphics.Color.blue(pixel)<25
            if (red) {
                // TEST ONLY: cropped GLES surface evidence, no personal phone UI or production screenshot feature.
                val file = java.io.File(compose.activity.getExternalFilesDir(null), "phase1-frame.png")
                file.outputStream().use { frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                frame.recycle(); return
            }
            if(System.currentTimeMillis()>=deadline) {
                java.io.File(compose.activity.getExternalFilesDir(null),"phase5-red-failure.png").outputStream().use {
                    frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            }
            frame.recycle()
            check(System.currentTimeMillis()<deadline) { "GLES surface must show the red homebrew framebuffer; pixel=${Integer.toHexString(pixel)} settings="+
                java.io.File(compose.activity.filesDir,"settings/display-v1.settings").let {if(it.exists()) it.readText() else "missing"}+
                "; focus=${compose.activity.hasWindowFocus()}; flags=${compose.activity.window.attributes.flags}; dim=${compose.activity.window.attributes.dimAmount}; alpha=${compose.activity.window.decorView.alpha}" }
            Thread.sleep(50)
        }
    }
    private fun assertRenderedInputColor() {
        val deadline=System.currentTimeMillis()+5000
        do {
            val frame=captureFrame()
            val pixel=frame.getPixel(frame.width/2,frame.height/2); frame.recycle()
            if(android.graphics.Color.green(pixel)>0) return
            Thread.sleep(50)
        } while(System.currentTimeMillis()<deadline)
        fail("Touch A must reach the rendered game within five seconds")
    }
}



