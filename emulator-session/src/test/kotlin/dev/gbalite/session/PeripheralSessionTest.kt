package dev.gbalite.session
import dev.gbalite.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
private class PeripheralFake: EmulatorCore {
    override val capabilities=EmulatorCapabilities()
    override val frames=FrameSource {false}
    var sample=PeripheralSample();var mask=0;var rewind=false
    override fun loadGame(source: GameSource)=LoadResult.Success
    override fun start() {};override fun resume() {};override fun pause() {sample=PeripheralSample()}
    override fun reset() {};override fun stop() {};override fun close() {}
    override fun setButton(button: GbaButton,pressed: Boolean) {}
    override fun peripheralStatus()=PeripheralStatus(31,true,1)
    override fun updatePeripherals(sample: PeripheralSample) {this.sample=sample}
    override fun configurePeripherals(manualMask: Int) {mask=manualMask}
    override fun setRewinding(active: Boolean): Boolean {rewind=active;return true}
    override fun playerMetrics()=PlayerMetrics(rewinding=rewind)
}
class PeripheralSessionTest {
    @Test fun scalarAndSyntheticHapticFollowSessionLifecycle()=runBlocking {
        val c=PeripheralFake();var motor=false;var stops=0
        val h=object: HapticOutput {
            override val status="SYNTHETIC TEST ONLY"
            override fun update(enabled: Boolean) {motor=enabled}
            override fun stop() {motor=false;stops++}
        }
        val s=EmulatorSession({c},Dispatchers.Unconfined,haptic=h)
        s.configurePeripherals(PeripheralSettings(PeripheralMode.MANUAL,PeripheralMode.MANUAL,PeripheralMode.MANUAL))
        s.setForeground(true);s.load(GameSource.FileDescriptorSource(1,512,"probe.gba"))
        assertEquals(28,c.mask)
        s.samplePeripherals(PeripheralSample(1,2,3,50));assertEquals(1,c.sample.tiltX);assertTrue(motor)
        s.setPaused(true);assertFalse(motor);s.samplePeripherals(PeripheralSample(4,5,6,50));assertEquals(0,c.sample.tiltX)
        s.setPaused(false);s.samplePeripherals(PeripheralSample());assertTrue(motor)
        s.setRewinding(true);assertFalse(motor);s.samplePeripherals(PeripheralSample());assertFalse(motor)
        s.setRewinding(false);s.setForeground(false);assertFalse(motor)
        s.setForeground(true);s.samplePeripherals(PeripheralSample());assertTrue(motor)
        s.configurePeripherals(PeripheralSettings(rumble=false));s.samplePeripherals(PeripheralSample());assertFalse(motor)
        s.stop();s.close();assertTrue(stops>=7)
    }
}
