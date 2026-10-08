package dev.gbalite.session

import dev.gbalite.core.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class SessionPlayTimeTest {
    private class Core: EmulatorCore {
        var framesDone=0L;var rewind=false;var speed=1
        override val frames=FrameSource {false};override val capabilities=EmulatorCapabilities()
        override fun playerMetrics()=PlayerMetrics(frames=framesDone,rewinding=rewind,speed=speed)
        override fun loadGame(source: GameSource)=LoadResult.Success
        override fun start() {};override fun resume() {};override fun pause() {};override fun reset() {};override fun stop() {};override fun close() {}
        override fun setButton(button: GbaButton,pressed: Boolean) {}
        override fun setSpeed(multiplier: Int): Boolean {speed=multiplier;return true}
        override fun setRewinding(active: Boolean): Boolean {rewind=active;return true}
        override fun exportSaveRam()=BytesResult.NoSave
        override fun exportState()=BytesResult.Success(byteArrayOf(1))
    }
    @Test fun validFrameGateSettingsForegroundFfRewindAndRelaunch()=runBlocking {
        var now=0L;var total=0L;var launches=0
        val store=object: PersistenceStore {
            override fun readBattery(id: GameId): ByteArray?=null
            override fun writeBattery(id: GameId,bytes: ByteArray) {}
            override fun writeState(id: GameId,key: String,state: StoredState) {}
            override fun readState(id: GameId,key: String): StoredState?=null
            override fun listStates(id: GameId)=emptyList<StateMetadata>()
            override fun markSession(game: GameRecord,clean: Boolean) {}
            override fun played(id: GameId) {launches++}
            override fun addPlayTime(id: GameId,deltaMs: Long) {total+=deltaMs}
        }
        val game=GameRecord(GameId.fromBytes(byteArrayOf(1)),"Test","content://test")
        val rom=GameSource.FileDescriptorSource(1,192,"test.gba");val core=Core()
        val session=EmulatorSession({core},Dispatchers.Unconfined,store,monotonicMillis={now})
        session.setForeground(true);session.load(rom,game);now=1000;session.samplePeripherals(PeripheralSample())
        session.checkpoint();assertEquals(0L,total);assertEquals(0,launches)
        core.framesDone=1;session.samplePeripherals(PeripheralSample());now=2000
        session.setPaused(true);now=4000;session.checkpoint();assertEquals(1000L,total);assertEquals(1,launches)
        session.setPaused(false);session.setSpeed(8);now=5000;session.setRewinding(true);now=6000
        session.setForeground(false);assertEquals(3000L,total)
        now=9000;session.setForeground(true);now=10000;session.stop();assertEquals(4000L,total)
        val reopened=EmulatorSession({Core().apply {framesDone=1}},Dispatchers.Unconfined,store,monotonicMillis={now})
        reopened.setForeground(true);reopened.load(rom,game);reopened.samplePeripherals(PeripheralSample());now=11000;reopened.stop()
        assertEquals(5000L,total);assertEquals(2,launches);session.close();reopened.close()
    }
}
