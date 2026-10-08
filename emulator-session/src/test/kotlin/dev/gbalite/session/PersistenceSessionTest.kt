package dev.gbalite.session
import dev.gbalite.core.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

private class PersistentCore: EmulatorCore {
    var speed=1; var rewind=false
    var battery=byteArrayOf(1); var closed=false; var running=false
    val events=mutableListOf<String>()
    override val frames=FrameSource { false }
    override val capabilities=EmulatorCapabilities()
    override fun loadGame(source: GameSource)=LoadResult.Success
    override fun start() { running=true }
    override fun resume() { running=true }
    override fun pause() { running=false; speed=1; rewind=false; events+="pause" }
    override fun setSpeed(multiplier: Int): Boolean { speed=multiplier; return true }
    override fun setRewinding(active: Boolean): Boolean { rewind=active; return true }
    override fun playerMetrics()=PlayerMetrics(speed=speed,rewinding=rewind)
    override fun reset() {}
    override fun stop() {}
    override fun setButton(button: GbaButton,pressed: Boolean) {}
    override fun exportSaveRam(): BytesResult { check(!running); events+="battery"; return BytesResult.Success(battery.copyOf()) }
    override fun importSaveRam(bytes: ByteArray): Boolean { battery=bytes.copyOf(); events+="import-battery"; return true }
    override fun exportState(): BytesResult { check(!running); events+="state"; return BytesResult.Success(byteArrayOf(7,0,0,1)) }
    override fun importState(bytes: ByteArray): Boolean { events+="import-state"; return bytes.contentEquals(byteArrayOf(7,0,0,1)) }
    override fun close() { closed=true; events+="close" }
}
private class MemoryStore: PersistenceStore {
    var fail=false; val batteries=mutableMapOf<GameId,ByteArray>(); val states=mutableMapOf<Pair<GameId,String>,StoredState>()
    override fun readBattery(id: GameId)=batteries[id]
    override fun writeBattery(id: GameId,bytes: ByteArray) { if(fail) throw PersistenceException(PersistenceError.SAVE_RAM_WRITE_FAILED); batteries[id]=bytes }
    override fun writeState(id: GameId,key: String,state: StoredState) { states[id to key]=state }
    override fun readState(id: GameId,key: String)=states[id to key]
    override fun listStates(id: GameId)=states.filterKeys { it.first==id }.values.map { it.metadata }
    override fun markSession(game: GameRecord,clean: Boolean) {}
}
class PersistenceSessionTest {
    @Test fun ffCheckpointRetainsSpeedAndBackgroundCancelsIt()=runBlocking {
        val c=PersistentCore(); val store=MemoryStore(); val s=EmulatorSession({c},Dispatchers.Unconfined,store)
        s.setForeground(true);s.load(rom,game)
        for(n in listOf(2,4,8,1)) { assertTrue(s.setSpeed(n));s.checkpoint();assertEquals(n,c.speed) }
        s.setSpeed(4);s.setForeground(false);assertEquals(1,c.speed);assertFalse(c.running)
        s.setForeground(true);assertEquals(1,c.speed);s.close()
    }
    @Test fun rewindDoesNotCommitDiskStatesAndBackgroundOrClosePreservesBattery()=runBlocking {
        val c=PersistentCore();val store=MemoryStore();val s=EmulatorSession({c},Dispatchers.Unconfined,store)
        s.setForeground(true);s.load(rom,game);s.saveState(1);s.saveState()
        val before=store.states.toMap();s.setRewinding(true);s.checkpoint()
        assertEquals(before,store.states);assertTrue(c.rewind)
        s.setForeground(false);assertEquals(before,store.states);assertFalse(c.rewind)
        assertArrayEquals(c.battery,store.batteries[game.gameId])
        s.setForeground(true);s.setRewinding(true);s.stop()
        assertEquals(before,store.states);assertArrayEquals(c.battery,store.batteries[game.gameId]);assertTrue(c.closed)
    }
    @Test fun thumbnailFailureDoesNotFailStateAndPauseSurvivesLifecycle()=runBlocking {
        val store=MemoryStore(); val core=PersistentCore()
        val s=EmulatorSession({core},Dispatchers.Unconfined,store,StateScreenshotWriter { _,_,_,_-> error("encode failed") })
        s.setForeground(true); s.load(rom,game); s.setPaused(true)
        assertTrue(s.saveState(1)); assertNotNull(store.readState(game.gameId,"slot-1"))
        s.setForeground(false); s.setForeground(true); assertFalse(core.running)
        assertEquals(SessionState.PAUSED,s.state.value); s.setPaused(false); assertTrue(core.running)
        s.close()
    }
    private val rom=GameSource.FileDescriptorSource(1,512,"a.gba")
    private val game=GameRecord(GameId.fromBytes(byteArrayOf(1)),"a.gba","content://test/a")
    @Test fun backgroundOrderRotationAndRelaunch()=runBlocking {
        val store=MemoryStore(); val core=PersistentCore(); val s=EmulatorSession({core},Dispatchers.Unconfined,store)
        s.setForeground(true); s.load(rom,game)
        repeat(4) { s.setForeground(false); s.setForeground(true) }
        assertEquals(listOf(2L,3L,4L),store.listStates(game.gameId).map { it.sequence }.sorted())
        assertTrue(core.events.indexOf("battery")<core.events.indexOf("state"))
        s.stop(); assertTrue(core.closed)
        val next=PersistentCore(); val reopened=EmulatorSession({next},Dispatchers.Unconfined,store)
        reopened.load(rom,game,true)
        assertEquals(listOf("import-battery","import-state"),next.events)
        reopened.close()
    }
    @Test fun failedBatteryDoesNotAutosaveCloseOrSwitch()=runBlocking {
        val store=MemoryStore(); val core=PersistentCore(); val s=EmulatorSession({core},Dispatchers.Unconfined,store)
        s.setForeground(true); s.load(rom,game); s.checkpoint()
        val safeStates=store.states.toMap()
        store.fail=true; core.battery=byteArrayOf(2)
        assertFalse(s.stop()); assertFalse(core.closed); assertEquals(safeStates,store.states)
        assertArrayEquals(byteArrayOf(1),store.batteries[game.gameId])
        assertTrue(s.load(rom,game) is LoadResult.PersistenceFailed); assertFalse(core.closed)
        store.fail=false; assertTrue(s.stop())
    }
    @Test fun periodicCheckpointRotatesAutosavesAndKeepsPriorGenerationOnBatteryFailure()=runBlocking {
        val store=MemoryStore();val core=PersistentCore()
        val s=EmulatorSession({core},Dispatchers.Unconfined,store)
        s.setForeground(true);s.load(rom,game)
        repeat(5) {assertTrue(s.checkpoint());assertEquals(SessionState.RUNNING,s.state.value)}
        assertEquals(listOf(3L,4L,5L),store.listStates(game.gameId).map {it.sequence}.sorted())
        assertEquals(setOf("auto-a","auto-b","auto-c"),store.states.keys.map {it.second}.toSet())
        val previous=store.states.toMap();store.fail=true
        assertFalse(s.checkpoint());assertEquals(previous,store.states)
        assertEquals(SessionState.PAUSED,s.state.value)
        assertTrue(core.events.indexOf("battery")<core.events.indexOf("state"))
        store.fail=false;s.close()
    }
    @Test fun manualQuickAndConcurrentCommandsSerialize()=runBlocking {
        val store=MemoryStore(); val core=PersistentCore(); val s=EmulatorSession({core},Dispatchers.Default,store)
        s.setForeground(true); s.load(rom,game)
        (1..4).map { slot->async { s.saveState(slot) } }.awaitAll().forEach { assertTrue(it) }
        assertTrue(s.saveState()); assertTrue(s.loadState()); assertTrue(s.loadState(3))
        assertEquals(5,store.states.size); s.close(); assertTrue(core.closed)
    }
    @Test fun newRomCannotPolluteOldSave()=runBlocking {
        val store=MemoryStore(); val old=PersistentCore(); val next=PersistentCore(); var n=0
        val s=EmulatorSession({if(n++==0) old else next},Dispatchers.Unconfined,store)
        s.load(rom,game)
        val other=game.copy(gameId=GameId.fromBytes(byteArrayOf(2)))
        s.load(rom,other); next.battery=byteArrayOf(9); s.stop()
        assertArrayEquals(byteArrayOf(1),store.batteries[game.gameId]); assertArrayEquals(byteArrayOf(9),store.batteries[other.gameId])
    }
}
