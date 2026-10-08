@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import androidx.activity.compose.setContent
import dev.gbalite.player.ui.GbaTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        compose.waitForIdle();Thread.sleep(650)
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
    @Test fun roundBHomeLibraryDetailsAndRealImportStates() {
        val m=model()
        fun fixture(name: String): Uri {
            val uri=Uri.parse("content://dev.gbalite.app.test.rom/$name")
            i.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            return uri
        }
        // Presentation-only empty fixture: existing library/data remain intact.
        compose.runOnUiThread {compose.activity.setContent {GbaTheme {AppShell(m,emptyList())}}}
        compose.onNodeWithTag("nav-游戏库").performClick();shot("empty");compose.onNodeWithText("还没有游戏").assertExists()
        compose.runOnUiThread {compose.activity.setContent {GbaTheme {AppShell(m)}}}
        compose.runOnUiThread {m.add(fixture("phase6-slow.gba"))}
        waitFor {m.loading};compose.onNodeWithTag("app-loading").assertExists();shot("loading");compose.onNodeWithText("取消").performClick();waitFor {!m.loading}
        compose.runOnUiThread {m.add(fixture("phase6-multiple.zip"))}
        waitFor {!m.loading && m.message=="ZIP 中包含多个 GBA 游戏"};compose.onNodeWithTag("app-error").assertExists();shot("error");compose.onNodeWithText("关闭").performClick()
        compose.runOnUiThread {m.add(fixture("phase6-library.gba"))};waitFor {!m.loading && m.imported!=null}
        val game=m.imported!!;compose.runOnUiThread {m.dismissMessage();m.play(game,false)}
        waitFor {m.playing && !m.loading && m.session.playerMetrics().frames>5}
        // This test keeps AppShell mounted to capture presentation; stop via the existing model.
        compose.runOnUiThread {m.exit()};waitFor {!m.playing && m.library.any {it.record.gameId==game.gameId && it.lastPlayedAt.isNotEmpty()}}
        shot("home");compose.onNodeWithTag("home-continue").assertExists()
        compose.onNodeWithTag("nav-游戏库").performClick();shot("library")
        compose.onNodeWithTag("library-search").performTextInput(game.displayName)
        compose.onNodeWithText("详情").performClick();shot("details")
        compose.onNodeWithTag("details-play").assertExists();compose.onNodeWithTag("nav-首页").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-设置").performClick();compose.onNodeWithTag("settings-关于").performScrollTo().performClick();shot("about")
        compose.onNodeWithText("GBA Lite 0.7.5").assertExists();compose.onNodeWithTag("nav-首页").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick();compose.onNodeWithTag("nav-首页").assertExists()
    }

    @Test fun flatControlsPaintAndTouchShareExpandedBounds() {
        compose.runOnUiThread {
            for(key in listOf("L","R","START","SELECT")) {
                val down=mutableSetOf<GbaButton>()
                val router=dev.gbalite.input.InputRouter {button,value->if(value) down+=button else down-=button}
                val view=dev.gbalite.player.TouchControls(compose.activity,router)
                view.layout(0,0,1000,1000)
                view.profile=dev.gbalite.input.InputProfile(false,listOf(dev.gbalite.input.TouchControl(key,.5f,.5f,.1f)))
                val radius=maxOf(50f,24*view.resources.displayMetrics.density)
                val x=500+radius*1.10f
                val bitmap=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                assertTrue("$key visible outside former circular bound",android.graphics.Color.alpha(bitmap.getPixel(x.toInt(),500))>0)
                val event=android.view.MotionEvent.obtain(1,2,android.view.MotionEvent.ACTION_DOWN,x,500f,0)
                view.onTouchEvent(event);event.recycle();assertEquals(setOf(GbaButton.valueOf(key)),down)
                val cancel=android.view.MotionEvent.obtain(1,3,android.view.MotionEvent.ACTION_CANCEL,x,500f,0)
                view.onTouchEvent(cancel);cancel.recycle();assertTrue(down.isEmpty());bitmap.recycle()
            }
        }
    }

    @Test fun smallViewportLargeTextKeepsNavigationAndLayoutSaveReachable() {
        val m=model();val original=m.profiles.read(false)
        compose.runOnUiThread {compose.activity.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.3f)) {
                GbaTheme {Box(Modifier.width(320.dp).height(568.dp)) {AppShell(m)}}
            }
        }}
        try {
            compose.onNodeWithTag("nav-设置").performClick();shot("small-large-text-settings")
            compose.onNodeWithTag("settings-控制").performScrollTo().performClick();click("竖屏布局")
            click("整体");compose.onNodeWithText("整体大小：100%").assertExists()
            compose.onNodeWithText("保存布局").performScrollTo().assertIsDisplayed();shot("small-large-text-layout")
            click("保存布局");waitFor {m.message=="布局已保存"}
            assertEquals(original,m.profiles.read(false))
            compose.onNodeWithContentDescription("返回").performClick();compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithTag("nav-首页").assertExists()
        } finally {m.profiles.write(original)}
    }

}
