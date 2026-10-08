@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import dev.gbalite.data.*
import dev.gbalite.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LibraryExperienceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun awaitLibraryBaseline(): Set<GameId> {
        val (ids,last)=SaveRepository(instrumentation.targetContext).use {repo ->
            repo.library().map {GameId(it.gameId)}.toSet() to repo.lastGame()?.gameId
        }
        compose.waitUntil(10000) {model().library.map {it.record.gameId}.toSet()==ids && model().lastGame?.gameId==last}
        return ids
    }
    private fun uri(name: String): Uri {
        val uri=Uri.parse("content://dev.gbalite.app.test.rom/$name")
        instrumentation.context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null).use {cursor ->
            assertTrue("Test provider query failed for $name",cursor?.moveToFirst()==true)
        }
        instrumentation.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        return uri
    }
    private fun fixtureId(name: String="phase6-library.gba")=instrumentation.context.assets.open(name).use {GameId.fromBytes(it.readBytes())}
    private fun select(name: String,button: String="添加游戏") {
        val uri=uri(name)
        val filter=android.content.IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);addDataType("*/*")}
        val monitor=android.app.Instrumentation.ActivityMonitor(filter,android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,Intent().setData(uri)),true)
        instrumentation.addMonitor(monitor)
        try {compose.onNodeWithText(button).performClick();compose.waitForIdle()}
        finally {instrumentation.removeMonitor(monitor)}
    }
    private fun add(name: String) {select(name);compose.waitUntil(10000) {!model().loading && model().imported!=null}}
    private fun back() {compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
    @Test fun explicitSchemaTwoToThreeKeepsIdentitySessionAndMetadata() {
        val ctx=instrumentation.targetContext;val name="save-metadata-test-librarymigration.db";ctx.deleteDatabase(name)
        val schema=instrumentation.context.assets.open("room-schema-2.json").use {org.json.JSONObject(it.bufferedReader().readText()).getJSONObject("database")}
        val id=fixtureId()
        ctx.openOrCreateDatabase(name,0,null).use {db ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {val e=entities.getJSONObject(i);db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))}
            val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO GameEntity(gameId,displayName,romUri,romHash,lastPlayedAt,sourceSize,sourceModified) VALUES(?,?,?,?,?,?,?)",arrayOf(id.value,"Legacy","content://legacy",id.value,"2026-10-06T12:00:00Z",512,123))
            db.execSQL("INSERT INTO LastSessionEntity(id,gameId,romUri,lastPlayedAt,sessionClosedCleanly) VALUES(1,?,?,?,1)",arrayOf(id.value,"content://legacy","2026-10-06T12:00:00Z"))
            db.execSQL("INSERT INTO SaveStateEntity(id,gameId,`key`,kind,createdAt,coreVersion,slot,sequence) VALUES(?,?,?,?,?,?,?,?)",
                arrayOf("${id.value}:slot-2",id.value,"slot-2","manual","2026-10-06T12:00:00Z","mGBA-0.10.5",2,7))
            db.version=2
        }
        SaveRepository(ctx,name).use {repo ->
            assertEquals(id,repo.lastGame()!!.gameId);val row=repo.game(id)!!
            assertEquals("Legacy",row.displayName);assertEquals(512L,row.sourceSize);assertEquals(123L,row.sourceModified)
            assertEquals("2026-10-06T12:00:00Z",row.addedAt);assertEquals(0L,row.playTimeMs);assertTrue(row.inLibrary);assertFalse(row.unavailable)
        }
        android.database.sqlite.SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {db ->
            db.rawQuery("SELECT gameId,`key`,coreVersion,slot,sequence FROM SaveStateEntity WHERE id=?",arrayOf("${id.value}:slot-2")).use {cursor ->
                assertTrue(cursor.moveToFirst());assertEquals(id.value,cursor.getString(0));assertEquals("slot-2",cursor.getString(1))
                assertEquals("mGBA-0.10.5",cursor.getString(2));assertEquals(2,cursor.getInt(3));assertEquals(7L,cursor.getLong(4))
            }
        }
        ctx.deleteDatabase(name)
    }
    @Test fun importIdentityDuplicateRemovalAndStrictRelinkKeepSaveData() {
        val ctx=instrumentation.targetContext;val id=fixtureId("phase6-crud.gba")
        SaveRepository(ctx,"save-metadata-test-library.db").use {repo ->
            val library=LibraryRepository(ctx,repo)
            val direct=runBlocking {library.import(uri("phase6-crud.gba"))}
            val zip=runBlocking {library.import(uri("phase6-crud.zip"))}
            assertEquals(id,direct.game.gameId);assertEquals(id,zip.game.gameId);assertTrue(zip.duplicate);assertEquals(1,repo.library().size)
            assertTrue(repo.game(id)!!.lastPlayedAt.isEmpty())
            repo.rename(id,"Local name");repo.addPlayTime(id,12345);repo.writeBattery(id,byteArrayOf(17,23,42))
            try {runBlocking {library.import(uri("phase6-other.gba"),id)};fail()} catch(e: ImportFailure) {assertEquals(ImportError.MISMATCH,e.error)}
            assertEquals("Local name",repo.game(id)!!.displayName)
            library.remove(id);assertTrue(repo.library().isEmpty());assertArrayEquals(byteArrayOf(17,23,42),repo.readBattery(id));assertEquals(12345L,repo.game(id)!!.playTimeMs)
            runBlocking {library.import(uri("phase6-crud.zip"),id)};assertEquals("Local name",repo.library().single().displayName)
            instrumentation.context.revokeUriPermission(direct.game.romUri.let(Uri::parse),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertEquals(id,runBlocking {library.resolve(direct.game)}.game.gameId)
            library.snapshot(id).delete()
            try {runBlocking {library.resolve(direct.game.copy(romUri="content://missing.test/game.gba"))};fail()} catch(_: ImportFailure) {}
            assertTrue(repo.game(id)!!.unavailable);assertArrayEquals(byteArrayOf(17,23,42),repo.readBattery(id))
        }
        ctx.deleteDatabase("save-metadata-test-library.db")
    }
    @Test fun homeLibrarySearchDetailsRenameRemoveAndSettingsNavigation() {
        add("phase6-library.gba")
        compose.onNodeWithText("打开游戏").assertExists()
        val id=fixtureId();compose.onNodeWithTag("library-list").performScrollToNode(hasTestTag("game-${id.value}"))
        compose.onNodeWithTag("game-${id.value}").assertExists()
        compose.onNodeWithTag("library-search").performTextInput(model().imported!!.displayName)
        compose.onNodeWithText("详情").performClick()
        compose.onNodeWithText("重命名").performClick()
        compose.waitUntil(5000) {compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size==1}
        compose.onNode(hasSetTextAction()).performTextReplacement("Phase 6 Test")
        compose.onNodeWithText("保存名称").performClick();compose.waitUntil(5000) {model().library.any {it.record.gameId==id && it.record.displayName=="Phase 6 Test"}}
        compose.onNodeWithText("重新选择原 ROM").assertExists();compose.onNodeWithText("存档管理 · 4 个存档位").assertExists()
        select("phase6-other.gba","重新选择原 ROM")
        compose.waitUntil(10000) {!model().loading && model().message=="选择的文件与原游戏不匹配"}
        assertEquals("Phase 6 Test",model().library.first {it.record.gameId==id}.record.displayName)
        select("phase6-single.zip","重新选择原 ROM")
        compose.waitUntil(10000) {!model().loading && model().imported?.gameId==id}
        back();compose.onNodeWithTag("library-search").performTextClearance()
        compose.onNodeWithText("排序：最近游玩").performClick();compose.onNodeWithText("游戏时长",useUnmergedTree=true).performClick()
        compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("外设"))
        compose.onNodeWithText("外设").performClick();compose.onNodeWithText("真实震动当前未启用").assertExists();back()
        compose.onNodeWithText("显示").performClick();compose.onNodeWithText("显示模式").assertExists();back()
        compose.onNodeWithText("控制").performClick();compose.onNodeWithText("竖屏布局").assertExists();back()
        compose.onNodeWithText("关于").performClick();compose.onNodeWithText("开源许可 · mGBA").performScrollTo().performClick();compose.onNodeWithText("Mozilla Public License",substring=true).assertExists();back();back();back()
        compose.onNodeWithText("游戏库",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("library-search").performTextClearance();compose.onNodeWithTag("library-search").performTextInput("Phase 6 Test")
        compose.onNodeWithText("详情").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("移出游戏库"))
        compose.onNodeWithText("移出游戏库").performClick();compose.onNodeWithText("确认移出").performClick()
        compose.waitUntil(5000) {model().library.none {it.record.gameId==id}}
    }
    @Test fun zipUiDuplicateAndInvalidZipLeaveLibraryIntact() {
        add("phase6-single.zip");val id=fixtureId();assertEquals(id,model().imported!!.gameId)
        add("phase6-library.gba");compose.onNodeWithText("已在游戏库中").assertExists()
        val before=model().library.map {it.record.gameId}.toSet();val invalid=uri("phase6-multiple.zip")
        compose.runOnUiThread {model().add(invalid)}
        compose.waitUntil(10000) {!model().loading && model().message=="ZIP 中包含多个 GBA 游戏"}
        assertEquals(before,model().library.map {it.record.gameId}.toSet())
    }
    @Test fun runningPlayTimePersistsAndSettingsPauseDoesNotCount() {
        add("phase6-single.zip");val m=model();val id=fixtureId()
        compose.runOnUiThread {m.rename(id,"Playable renamed game")}
        compose.waitUntil(5000) {m.library.any {it.record.gameId==id && it.record.displayName=="Playable renamed game"}}
        instrumentation.context.revokeUriPermission(Uri.parse(m.library.first {it.record.gameId==id}.record.romUri),Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The fixture provider is exported for baseline tests, so revoked grants alone would
        // not deny access. This test-only URI throws SecurityException for all source reads.
        val revoked=m.library.first {it.record.gameId==id}.record.copy(romUri="content://dev.gbalite.app.test.rom/phase6-revoked.gba")
        compose.runOnUiThread {m.play(revoked,false)}
        compose.waitUntil(10000) {!m.loading}
        assertTrue("Load failed: ${m.message}; state=${m.session.state.value}",m.playing)
        try {compose.waitUntil(10000) {m.session.playerMetrics().frames>5}}
        catch(e: AssertionError) {throw AssertionError("No frames: message=${m.message}; state=${m.session.state.value}; metrics=${m.session.playerMetrics()}",e)}
        Thread.sleep(1100);compose.runOnUiThread {m.openAppSettings()}
        compose.waitUntil(5000) {m.appSettings && m.session.state.value==dev.gbalite.session.SessionState.PAUSED}
        assertTrue(runBlocking {m.session.checkpoint()})
        SaveRepository(instrumentation.targetContext).use {repo ->
            val initial=repo.game(fixtureId())!!.playTimeMs;assertTrue(initial>=900)
            Thread.sleep(1100);assertTrue(runBlocking {m.session.checkpoint()});assertEquals(initial,repo.game(fixtureId())!!.playTimeMs)
            assertTrue(repo.game(fixtureId())!!.lastPlayedAt.isNotEmpty())
        }
        compose.runOnUiThread {m.closeAppSettings();m.exit()};compose.waitUntil(10000) {!m.playing}
        compose.activityRule.scenario.recreate();compose.waitUntil(5000) {model().library.any {it.record.gameId==fixtureId() && it.playTimeMs>=900}}
        compose.onNodeWithTag("nav-首页").performClick()
        compose.onNodeWithText("最近游戏").assertExists();compose.onNodeWithText("继续上次游戏").performClick()
        compose.waitUntil(10000) {model().playing && model().session.playerMetrics().frames>5}
        compose.runOnUiThread {model().exit()};compose.waitUntil(10000) {!model().playing}
    }
    @Test fun syntheticEmptyAndFiveHundredMetadataUiRemainResponsive() {
        val m=model();compose.runOnUiThread {compose.activity.setContent {MaterialTheme {AppShell(m,emptyList())}}}
        compose.onNodeWithText("还没有游戏").assertExists();compose.onNodeWithText("选择游戏").assertExists()
        val fake=(0 until 500).map {n ->LibraryGame(GameRecord(GameId.fromBytes("fake$n".toByteArray()),"Test Game %03d".format(n),"content://synthetic/$n"),"2026-10-07","2026-10-07",n*1000L,false)}
        val start=android.os.SystemClock.elapsedRealtime()
        compose.runOnUiThread {compose.activity.setContent {MaterialTheme {AppShell(m,fake)}}}
        compose.onNodeWithText("游戏库",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("library-list").performScrollToIndex(499);compose.onNodeWithText("Test Game 499").assertIsDisplayed()
        compose.onNodeWithTag("library-search").performTextInput("game 499");compose.onNodeWithText("Test Game 499").assertExists()
        File(compose.activity.filesDir,"phase6-library-ui-metrics.txt").writeText("Synthetic 500 metadata, list scroll and search elapsedMs=${android.os.SystemClock.elapsedRealtime()-start}; no ROM reads or DB insert.\n")
    }
    @Test fun importCancellationKeepsLibraryLastSessionAndCleansTemp() {
        val m=model();compose.waitForIdle();val before=awaitLibraryBaseline();val last=m.lastGame
        val root=File(compose.activity.filesDir,"roms");val oldTemps=root.listFiles()?.filter {it.name.startsWith("rom.tmp.") }?.map {it.name}?.toSet() ?: emptySet()
        select("phase6-slow.gba");compose.waitUntil(5000) {m.loading};compose.onNodeWithText("取消").performClick()
        compose.waitUntil(5000) {!m.loading};assertEquals(before,m.library.map {it.record.gameId}.toSet());assertEquals(last,m.lastGame)
        assertEquals(oldTemps,root.listFiles()?.filter {it.name.startsWith("rom.tmp.") }?.map {it.name}?.toSet() ?: emptySet<String>())
        assertNull(m.imported)
    }
    @Test fun slowProviderReadDeadlineFailsClosedWithoutPartialIndex() {
        val m=model();compose.waitForIdle();val before=awaitLibraryBaseline()
        select("phase6-timeout.gba");compose.waitUntil(15000) {!m.loading && m.message?.startsWith("无法读取游戏文件")==true}
        assertEquals(before,m.library.map {it.record.gameId}.toSet());assertNull(m.imported)
        compose.onNodeWithText("添加游戏").assertIsEnabled()
    }
    @Test fun slowProviderRecreateAndHomeFailWithoutPartialCommit() {
        val before=awaitLibraryBaseline();val last=model().lastGame
        val root=File(compose.activity.filesDir,"roms")
        val oldTemps=root.listFiles()?.filter {it.name.startsWith("rom.tmp.") }?.map {it.name}?.toSet() ?: emptySet()
        select("phase6-timeout.gba");compose.waitUntil(5000) {model().loading}
        compose.activityRule.scenario.recreate()
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use { p ->
            val reply=android.os.ParcelFileDescriptor.AutoCloseInputStream(p).bufferedReader().use {it.readText()}
            assertTrue("Home shell input reported an error: $reply",reply.isBlank())
        }
        // Home dispatch is asynchronous; assert actual onStop rather than
        // assuming it completed during an arbitrary fixed host-time sleep.
        compose.waitUntil(5000) {
            !compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        assertFalse(compose.activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        compose.waitUntil(15000) {compose.activity.hasWindowFocus() && !model().loading && model().message?.startsWith("无法读取游戏文件")==true}
        assertEquals(before,model().library.map {it.record.gameId}.toSet());assertEquals(last,model().lastGame)
        assertNull(model().imported)
        assertEquals(oldTemps,root.listFiles()?.filter {it.name.startsWith("rom.tmp.") }?.map {it.name}?.toSet() ?: emptySet<String>())
        compose.onNodeWithText("添加游戏").assertIsEnabled()
    }
}
