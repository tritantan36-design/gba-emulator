@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import dev.gbalite.session.SessionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PeripheralExperienceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    private fun model()=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
    private fun open(name: String): PlayerViewModel {
        val i=InstrumentationRegistry.getInstrumentation();val uri=Uri.parse("content://dev.gbalite.app.test.rom/$name.gba")
        i.uiAutomation.executeShellCommand("content query --uri $uri").use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { assertTrue(it.readText().contains("Row: 0")) }
        }
        i.context.grantUriPermission("dev.gbalite.app",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        lateinit var m: PlayerViewModel
        compose.runOnUiThread {m=model();m.open(uri)}
        compose.waitUntil(15000) { m.playing && m.session.state.value==SessionState.RUNNING }
        return m
    }
    @Test fun realSensorRegistrationOrientationHomeAndExit() {
        val m=open("tilt-probe")
        try {
            compose.runOnUiThread { m.peripherals(PeripheralSettings()) }
            compose.waitUntil(5000) { m.peripheralDetected and 16!=0 && (!m.sensors.hasTilt || m.sensors.listeners==1) }
            val expected=if(m.sensors.hasTilt) 1 else 0
            java.io.File(compose.activity.filesDir,"phase5-device-sensors.txt").writeText(
                "Device=${android.os.Build.MODEL}, API=${android.os.Build.VERSION.SDK_INT}\nTilt=${m.sensors.hasTilt}, Gyro=${m.sensors.hasGyro}, Light=${m.sensors.hasLight}\nRunning tilt listeners=${m.sensors.listeners}\nPhysical motion/light acceptance is separate; no trajectory recorded.\n")
            for(direction in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                compose.runOnUiThread {compose.activity.requestedOrientation=direction}
                Thread.sleep(500);compose.waitUntil(5000) { m.sensors.listeners==expected }
                assertEquals(SessionState.RUNNING,m.session.state.value)
            }
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            compose.waitUntil(5000) {m.session.state.value==SessionState.PAUSED && m.sensors.listeners==0}
            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(Intent(InstrumentationRegistry.getInstrumentation().targetContext,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            compose.waitUntil(5000) {m.session.state.value==SessionState.RUNNING && m.sensors.listeners==expected}
            runBlocking { m.session.setRewinding(true) };compose.waitUntil(5000) {m.sensors.listeners==0}
            runBlocking { m.session.setRewinding(false) };compose.waitUntil(5000) {m.sensors.listeners==expected}
        } finally {runBlocking {m.session.stop()};compose.waitUntil(5000) {m.sensors.listeners==0}}
    }
    @Test fun settingsEntryPersistenceAndMissingSensorManualContract() {
        val m=open("solar-probe")
        val previous=m.peripheralSettings
        try {
            compose.onNodeWithText("菜单",useUnmergedTree=true).performClick()
            compose.onNodeWithText("设置").performScrollTo().performClick()
            compose.onNodeWithText("外设").performClick()
            compose.onNodeWithText("实时时钟：自动（系统时间）").assertExists()
            val selected=PeripheralSettings(PeripheralMode.MANUAL,PeripheralMode.MANUAL,PeripheralMode.MANUAL,80,false,1f,2f,.1f)
            compose.runOnUiThread {m.peripherals(selected)}
            compose.waitUntil(5000) {java.io.File(compose.activity.filesDir,"settings/peripherals-v1.settings").let {it.exists() && it.readText()==selected.encode()}}
            compose.activityRule.scenario.recreate();compose.waitForIdle()
            assertEquals(selected,model().peripheralSettings)
            assertTrue(m.session.hapticStatus.contains("BLOCKED_BY_PERMISSION_APPROVAL"))
            val adapter=SensorAdapter(InstrumentationRegistry.getInstrumentation().targetContext)
            adapter.configure(selected);adapter.manual(.5f,-.5f,1f)
            assertTrue(adapter.snapshot().tiltX<0);assertTrue(adapter.snapshot().tiltY<0);assertTrue(adapter.snapshot().gyroZ<0)
            adapter.stop();assertEquals(0,adapter.snapshot().tiltX);assertEquals(0,adapter.listeners)
        } finally {
            compose.runOnUiThread {m.peripherals(previous)}
            compose.waitUntil(5000) {java.io.File(compose.activity.filesDir,"settings/peripherals-v1.settings").let {it.exists() && it.readText()==previous.encode()}}
            runBlocking {m.session.stop()}
        }
    }
}
