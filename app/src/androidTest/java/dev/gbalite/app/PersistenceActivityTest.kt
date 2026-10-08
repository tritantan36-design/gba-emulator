@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.GameId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class) class PersistenceActivityTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private fun open(filename: String="persistence.gba") {
        val i=InstrumentationRegistry.getInstrumentation()
        val uri=Uri.parse("content://dev.gbalite.app.test.rom/$filename")
        // TEST ONLY grants from the standalone fixture provider, no app permission changes.
        i.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        val data=Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        val filter=android.content.IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") }
        val monitor=Instrumentation.ActivityMonitor(filter,Instrumentation.ActivityResult(Activity.RESULT_OK,data),true)
        i.addMonitor(monitor)
        try { compose.onNodeWithText("添加游戏").performClick(); compose.waitUntil(10000) {compose.onAllNodesWithText("打开游戏").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("打开游戏").performClick();waitPlayer() }
        finally { i.removeMonitor(monitor) }
    }
    private fun waitPlayer() {
        try {compose.waitUntil(10000) {compose.onAllNodesWithText("菜单").fetchSemanticsNodes().isNotEmpty()}}
        catch(e: AssertionError) {
            val m=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
            throw AssertionError("Player navigation failed: loading=${m.loading}; playing=${m.playing}; message=${m.message}; state=${m.session.state.value}; persistence=${m.session.persistenceError.value}",e)
        }
    }
    private fun id(): String {
        val i=InstrumentationRegistry.getInstrumentation()
        return i.context.assets.open("persistence.gba").use { GameId.fromBytes(it.readBytes()+"PHASE3_TEST_ONLY".toByteArray()).value }
    }
    private fun persistence()=File(compose.activity.filesDir,"persistence/${id()}")
    private fun waitFile(key: String) {
        compose.waitUntil(10000) { File(persistence(),"$key/current.json").exists() }
    }
    private fun stateOperation(label: String,key: String?=null) {
        val page=if(label.startsWith("读取") || label=="Quick Load") "load" else "save"
        if(compose.onAllNodesWithTag("pause-$page").fetchSemanticsNodes().isEmpty()) {
            if(compose.onAllNodesWithTag("pause-root").fetchSemanticsNodes().isEmpty()) compose.onNodeWithContentDescription("返回菜单").performClick()
            compose.onNodeWithText(if(page=="load") "读取存档" else "保存存档").performScrollTo().performClick()
        }
        val manifest=key?.let {File(persistence(),"$it/current.json")}
        val before=manifest?.takeIf {it.exists()}?.readText()
        compose.onNodeWithText(label,useUnmergedTree=true).performScrollTo()
        val button=hasClickAction() and hasAnyDescendant(hasText(label)) and isEnabled()
        compose.waitUntil(10000) {compose.onAllNodes(button,useUnmergedTree=true).fetchSemanticsNodes().size==1}
        compose.onNode(button,useUnmergedTree=true).performClick()
        if(manifest!=null) compose.waitUntil(10000) {manifest.exists() && manifest.readText()!=before}
        compose.waitUntil(10000) {compose.onAllNodes(button,useUnmergedTree=true).fetchSemanticsNodes().size==1}
    }
    @Test fun batteryBackgroundRecreateManualSlotsQuickAndResume() {
        open(); Thread.sleep(200)
        compose.onNodeWithTag("touch-controls").performTouchInput { down(androidx.compose.ui.geometry.Offset(width*.83f,height*.35f)) }; Thread.sleep(100)
        compose.onNodeWithTag("touch-controls").performTouchInput { up() }
        compose.onNodeWithText("菜单").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        for(slot in 1..4) {
            stateOperation("保存 $slot","slot-$slot")
            stateOperation("读取 $slot")
        }
        stateOperation("Quick Save","quick")
        stateOperation("Quick Load")
        compose.onNodeWithContentDescription("返回菜单").performClick()
        compose.onNodeWithText("继续").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitFile("battery"); waitFile("auto-a")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate(); waitPlayer()
        compose.onNodeWithText("菜单").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithText("退出游戏",useUnmergedTree=true).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("继续上次游戏").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("继续上次游戏").performClick(); waitPlayer()
        assertTrue(File(persistence(),"battery/current.json").exists())
        compose.onNodeWithText("菜单").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithText("退出游戏",useUnmergedTree=true).performScrollTo().performClick()
    }
    @Test fun unavailableUriHasVisibleErrorAndOldFilesRemain() {
        val root=File(compose.activity.filesDir,"persistence")
        val before=root.walkTopDown().count { it.isFile && it.name=="battery.sav" }
        compose.runOnUiThread { ViewModelProvider(compose.activity)[PlayerViewModel::class.java].open(Uri.parse("content://missing.test/rom.gba"),true) }
        compose.waitUntil(10000) { compose.onAllNodesWithText("上次 ROM 已无法访问，请重新选择文件；存档仍保留。").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before,root.walkTopDown().count { it.isFile && it.name=="battery.sav" })
    }
    @Test fun fixtureUriPersistedForResume() {
        open()
        val uri=Uri.parse("content://dev.gbalite.app.test.rom/persistence.gba")
        assertTrue(compose.activity.contentResolver.persistedUriPermissions.any { it.uri==uri && it.isReadPermission })
        compose.onNodeWithText("菜单").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithText("退出游戏",useUnmergedTree=true).performScrollTo().performClick()
    }
    @Test fun renamedRomUsesSameGameAndFreshActivityResumes() {
        open()
        compose.onNodeWithText("菜单").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithText("退出游戏",useUnmergedTree=true).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("添加游戏").fetchSemanticsNodes().isNotEmpty() }
        val batteryRoot=persistence().canonicalPath
        open("renamed.gba")
        compose.onNodeWithText("菜单").performClick(); compose.waitUntil(5000) { compose.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithText("退出游戏",useUnmergedTree=true).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("继续上次游戏").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(batteryRoot,persistence().canonicalPath)
        // Close clears ViewModel; relaunch must resolve persisted Room/URI/state data.
        compose.activityRule.scenario.close()
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var resumed=false
            scenario.onActivity { activity ->
                val model=ViewModelProvider(activity)[PlayerViewModel::class.java]
                model.resumeLast()
            }
            // LastSession is asynchronously read, so wait then retry via the application API.
            Thread.sleep(300)
            scenario.onActivity { ViewModelProvider(it)[PlayerViewModel::class.java].resumeLast() }
            val deadline=System.currentTimeMillis()+10000
            while(!resumed && System.currentTimeMillis()<deadline) {
                scenario.onActivity { resumed=ViewModelProvider(it)[PlayerViewModel::class.java].playing }
                if(!resumed) Thread.sleep(50)
            }
            assertTrue("Fresh Activity must load persisted LastSession/autosave",resumed)
        }
    }
    @Test fun roomMigrationPreservesGameAndLastSession() {
        val i=InstrumentationRegistry.getInstrumentation(); val ctx=i.targetContext
        val name="save-metadata-test-migration.db"
        ctx.deleteDatabase(name)
        val schema=i.context.assets.open("room-schema-1.json").use { org.json.JSONObject(it.bufferedReader().readText()).getJSONObject("database") }
        ctx.openOrCreateDatabase(name,0,null).use { db ->
            val entities=schema.getJSONArray("entities")
            for(n in 0 until entities.length()) {
                val entity=entities.getJSONObject(n)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(n in 0 until setup.length()) db.execSQL(setup.getString(n))
            val game=android.content.ContentValues().apply {
                put("gameId",id()); put("displayName","legacy.gba"); put("romUri","content://dev.gbalite.app.test.rom/persistence.gba")
                put("romHash",id()); put("lastPlayedAt","2026-10-06T12:00:00Z")
            }
            db.insertOrThrow("GameEntity",null,game)
            val last=android.content.ContentValues().apply {
                put("id",1); put("gameId",id()); put("romUri","content://dev.gbalite.app.test.rom/persistence.gba")
                put("lastPlayedAt","2026-10-06T12:00:00Z"); put("sessionClosedCleanly",1)
            }
            db.insertOrThrow("LastSessionEntity",null,last); db.version=1
        }
        try {
            dev.gbalite.data.SaveRepository(ctx,name).use { repo ->
                val retained=repo.lastGame()!!
                assertEquals(id(),retained.gameId.value); assertEquals("legacy.gba",retained.displayName)
                assertEquals(-1L,retained.sourceSize); assertEquals(-1L,retained.sourceModified)
            }
        } finally { ctx.deleteDatabase(name) }
    }
}



