@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import dev.gbalite.input.InputProfile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Explicit two-install QA on an isolated AVD. Never downgrade/clear the user's phone. */
class Phase7UpgradeQaTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private val root get()=instrumentation.targetContext.filesDir
    private val proof get()=File(root,"phase7-upgrade-proof.json")
    private fun gameId()=instrumentation.context.assets.open("phase7-upgrade.gba").use {GameId.fromBytes(it.readBytes())}
    @Suppress("DEPRECATION") private fun versionCode()=instrumentation.targetContext.packageManager
        .getPackageInfo(instrumentation.targetContext.packageName,0).versionCode
    private fun open() {
        val uri=Uri.parse("content://dev.gbalite.app.test.rom/phase7-upgrade.gba")
        instrumentation.uiAutomation.executeShellCommand("content query --uri $uri").use {fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use {assertTrue(it.readText().contains("Row: 0"))}
        }
        instrumentation.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        compose.runOnUiThread {model().open(uri)}
        compose.waitUntil(15000) {model().playing && model().session.playerMetrics().frames>10}
    }
    private fun files(id: GameId): JSONObject {
        val hashes=JSONObject()
        for(relative in listOf("persistence/${id.value}","thumbnails/${id.value}","screenshots/${id.value}","input","settings")) {
            File(root,relative).walkTopDown().filter {it.isFile}.forEach {file ->
                hashes.put(file.relativeTo(root).invariantSeparatorsPath,sha256(file.readBytes()))
            }
        }
        return hashes
    }
    @Test fun seedActualPhaseSixDataBeforePackageUpgrade() {
        assumeTrue("Explicit isolated upgrade seed requested",
            InstrumentationRegistry.getArguments().getString("phase7Upgrade")=="seed")
        assertEquals(6,versionCode())
        assertFalse("Do not overwrite an existing upgrade proof",proof.exists())
        open();val id=gameId();assertEquals(id,model().session.gameId)
        runBlocking {model().session.input.setButton(GbaButton.A,true)};Thread.sleep(100);model().session.input.releaseAll()
        Thread.sleep(1200)
        assertTrue(runBlocking {model().session.checkpoint()})
        assertTrue(runBlocking {model().session.saveState()});assertTrue(runBlocking {model().session.saveState(1)})
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {model().images.screenshot(model().session.frames,id)}
        val display=DisplaySettings(DisplayMode.LCD,ScaleMode.INTEGER,BackgroundTone.WHITE)
        val peripherals=PeripheralSettings(PeripheralMode.MANUAL,PeripheralMode.DISABLED,PeripheralMode.MANUAL,73,false)
        compose.runOnUiThread {model().display(display);model().peripherals(peripherals)}
        compose.waitUntil(10000) {File(root,"settings/display-v1.settings").let {it.exists() && it.readText()==display.encode()} &&
            File(root,"settings/peripherals-v1.settings").let {it.exists() && it.readText()==peripherals.encode()}}
        runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            model().profiles.write(InputProfile.default(false).copy(opacity=.47f))
            model().profiles.write(InputProfile.default(true).copy(opacity=.63f))
        }
        compose.runOnUiThread {model().exit()};compose.waitUntil(10000) {!model().playing}
        dev.gbalite.data.SaveRepository(instrumentation.targetContext).use {repository ->
            val row=repository.game(id)!!;assertTrue(row.playTimeMs>=900)
            val states=repository.listStates(id)
            assertTrue(states.any {it.kind=="auto"});assertTrue(states.any {it.kind=="quick"});assertTrue(states.any {it.slot==1})
            assertTrue(states.all {it.appVersion=="0.6.0"})
            proof.writeText(JSONObject().put("createdByVersionCode",6).put("gameId",id.value)
                .put("displayName",row.displayName).put("romUri",row.romUri).put("addedAt",row.addedAt)
                .put("playTimeMs",row.playTimeMs).put("files",files(id)).toString(2))
        }
    }
    @Test fun phaseSevenRetainsActualPhaseSixFilesAndCanResumeQuickAndSlot() {
        assumeTrue("Explicit isolated upgrade verification requested",
            InstrumentationRegistry.getArguments().getString("phase7Upgrade")=="verify")
        assertEquals(7,versionCode());assertTrue(proof.exists())
        val old=JSONObject(proof.readText());assertEquals(6,old.getInt("createdByVersionCode"))
        val id=GameId(old.getString("gameId"));assertEquals(gameId(),id)
        val expected=old.getJSONObject("files");val actual=files(id)
        assertEquals(expected.keys().asSequence().toSet(),actual.keys().asSequence().toSet())
        for(name in expected.keys()) assertEquals("Preserved $name",expected.getString(name),actual.getString(name))
        dev.gbalite.data.SaveRepository(instrumentation.targetContext).use {repository ->
            val row=repository.game(id)!!;assertEquals(id,repository.lastGame()!!.gameId)
            assertEquals(old.getString("displayName"),row.displayName);assertEquals(old.getString("romUri"),row.romUri)
            assertEquals(old.getString("addedAt"),row.addedAt);assertEquals(old.getLong("playTimeMs"),row.playTimeMs)
            assertTrue(row.inLibrary);assertFalse(row.unavailable)
            assertTrue(repository.listStates(id).all {it.appVersion=="0.6.0"})
        }
        compose.waitUntil(10000) {model().lastGame?.gameId==id}
        compose.runOnUiThread {model().resumeLast()}
        compose.waitUntil(15000) {model().playing && model().session.playerMetrics().frames>5}
        assertEquals(id,model().session.gameId)
        assertTrue(runBlocking {model().session.loadState()});assertTrue(runBlocking {model().session.loadState(1)})
        File(root,"phase7-upgrade-results.json").writeText(JSONObject().put("result","PASS")
            .put("fromVersionCode",6).put("toVersionCode",7).put("preservedFiles",expected.length())
            .put("quickLoad",true).put("slotLoad",true).put("resume",true).toString(2))
        compose.runOnUiThread {model().exit()};compose.waitUntil(10000) {!model().playing}
    }
    @Test fun prepareLiveGameForControlledProcessDeath() {
        assumeTrue("Explicit isolated process-death preparation",
            InstrumentationRegistry.getArguments().getString("phase7ProcessDeath")=="prepare")
        assertEquals(7,versionCode())
        val marker=File(root,"phase7-process-death-proof.json")
        assertFalse("Keep previous evidence",marker.exists())
        open();val id=gameId()
        assertTrue(runBlocking {model().session.checkpoint()})
        assertTrue(runBlocking {model().session.saveState()})
        assertTrue(runBlocking {model().session.saveState(1)})
        marker.writeText(JSONObject().put("gameId",id.value).put("files",files(id))
            .put("pid",android.os.Process.myPid()).put("state","RUNNING_BEFORE_CONTROLLED_KILL").toString(2))
        // The host force-stops this isolated AVD process while gameplay is live.
        // An interrupted runner is expected evidence, never counted as a PASS.
        runBlocking {model().session.input.setButton(GbaButton.A,true)}
        Thread.sleep(120000)
        fail("Host did not perform controlled force-stop within the preparation window")
    }
    @Test fun coldRestartRecoversLastSafePointAfterControlledProcessDeath() {
        assumeTrue("Explicit isolated process-death verification",
            InstrumentationRegistry.getArguments().getString("phase7ProcessDeath")=="verify")
        val marker=JSONObject(File(root,"phase7-process-death-proof.json").readText())
        assertNotEquals(marker.getInt("pid"),android.os.Process.myPid())
        val id=GameId(marker.getString("gameId"));val expected=marker.getJSONObject("files");val actual=files(id)
        assertEquals(expected.keys().asSequence().toSet(),actual.keys().asSequence().toSet())
        for(name in expected.keys()) assertEquals("Last safe file $name",expected.getString(name),actual.getString(name))
        compose.waitUntil(10000) {model().lastGame?.gameId==id}
        compose.runOnUiThread {model().resumeLast()}
        compose.waitUntil(15000) {model().playing && model().session.playerMetrics().frames>5}
        assertEquals(id,model().session.gameId)
        assertTrue(runBlocking {model().session.loadState()});assertTrue(runBlocking {model().session.loadState(1)})
        File(root,"phase7-process-death-results.json").writeText(JSONObject().put("result","PASS")
            .put("lastSafePoint",true).put("lastFramePerfectClaim",false).put("preservedFiles",expected.length()).toString(2))
        compose.runOnUiThread {model().exit()};compose.waitUntil(10000) {!model().playing}
    }
}
