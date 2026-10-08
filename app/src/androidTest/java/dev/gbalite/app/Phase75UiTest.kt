@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import dev.gbalite.core.DisplayMode
import dev.gbalite.renderer.OriginalSurface
import dev.gbalite.session.SessionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class Phase75UiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun waitFor(condition: ()->Boolean)=compose.waitUntil(15000,condition)
    private fun click(text: String) {compose.onNodeWithText(text,useUnmergedTree=true).performScrollTo().performClick()}
    private fun shot(name: String) {
        compose.waitForIdle();Thread.sleep(250)
        val output=File(i.targetContext.filesDir,"phase75-screenshots").apply {mkdirs()}
        val b=requireNotNull(i.uiAutomation.takeScreenshot())
        File(output,"$name.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    private fun surface(): OriginalSurface {
        fun find(v: View): OriginalSurface? {if(v is OriginalSurface) return v;if(v is ViewGroup) for(n in 0 until v.childCount) find(v.getChildAt(n))?.let {return it};return null}
        var value: OriginalSurface?=null;compose.runOnUiThread {value=find(compose.activity.window.decorView)};return requireNotNull(value)
    }
    private fun visible() {
        waitFor {
            var ok=false
            compose.runOnUiThread {surface().bitmap?.let {b ->ok=android.graphics.Color.red(b.getPixel(b.width/2,b.height/2))>200;b.recycle()}}
            ok
        }
        val viewport=surface().diagnostics().viewport
        assertEquals("Renderer retains 3:2 viewport",1.5,viewport.width.toDouble()/viewport.height,0.02)
    }
    private fun open() {
        val uri=Uri.parse("content://dev.gbalite.app.test.rom/persistence.gba")
        i.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        compose.runOnUiThread {model().open(uri);model().display(DisplaySettings())}
        waitFor {model().playing && !model().loading && model().session.playerMetrics().frames>5}
        visible()
    }
    private fun menu() {compose.onNodeWithTag("player-menu").performClick();waitFor {model().session.state.value==SessionState.PAUSED};compose.onNodeWithTag("pause-root").assertExists()}
    private fun root() {compose.onNodeWithContentDescription("返回菜单").performClick();compose.onNodeWithTag("pause-root").assertExists()}
    private fun operation(label: String,key: String?=null) {
        val file=key?.let {File(i.targetContext.filesDir,"persistence/${model().session.gameId!!.value}/$it/current.json")}
        val before=file?.takeIf {it.exists()}?.readText()
        click(label)
        if(file!=null) waitFor {file.exists() && file.readText()!=before}
        waitFor {compose.onAllNodes(hasText(label) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun roundAPlayerPauseSettingsAndSurface() {
        val original=model().displaySettings
        try {
            compose.onNodeWithTag("nav-设置").performClick();shot("settings-root");shot("bottom-navigation")
            compose.onNodeWithTag("settings-显示").performClick();shot("settings-display")
            compose.onNodeWithTag("nav-首页").assertDoesNotExist()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithTag("settings-控制").performClick();shot("settings-controls")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithTag("nav-首页").performClick()
            open();shot("player-portrait")
            for((orientation,name) in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to "player-landscape",ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE to "player-reverse-landscape",ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to "player-portrait")) {
                compose.runOnUiThread {compose.activity.requestedOrientation=orientation};Thread.sleep(600);visible();shot(name)
            }
            menu();val paused=model().session.playerMetrics().frames;Thread.sleep(200);assertEquals(paused,model().session.playerMetrics().frames);shot("pause-root")
            click("保存存档");operation("Quick Save","quick")
            for(n in 1..4) operation("保存 $n","slot-$n")
            shot("pause-save");root();click("读取存档");operation("Quick Load")
            for(n in 1..4) operation("读取 $n")
            shot("pause-load");root();click("快进 / 倒带");shot("pause-fast-forward");click("4×")
            waitFor {model().session.playerMetrics().speed==4};menu();click("快进 / 倒带");click("关闭快进（恢复1×）")
            waitFor {model().session.playerMetrics().speed==1};visible()
            menu();click("显示与控制");click("显示设置");click("Sharp");assertEquals(DisplayMode.SHARP,model().displaySettings.displayMode);click("Original");click("返回游戏");visible()
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            waitFor {model().lifecycleApplied.isCompleted && model().session.state.value==SessionState.PAUSED}
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            waitFor {model().session.state.value==SessionState.RUNNING};visible()
            menu();click("退出游戏");waitFor {!model().playing}
        } finally {compose.runOnUiThread {model().display(original)};runBlocking {model().session.stop()}}
    }
}
