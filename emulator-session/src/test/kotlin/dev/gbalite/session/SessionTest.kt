package dev.gbalite.session
import dev.gbalite.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

private class FakeCore : EmulatorCore {
    val events = mutableListOf<String>()
    var result: LoadResult = LoadResult.Success
    override val capabilities = EmulatorCapabilities()
    override val frames = FrameSource { false }
    override fun loadGame(source: GameSource): LoadResult { events += "load"; return result }
    override fun start() { events += "start" }
    override fun pause() { events += "pause" }
    override fun resume() { events += "resume" }
    override fun reset() {}
    override fun stop() {}
    override fun setButton(button: GbaButton, pressed: Boolean) { events += "button" }
    override fun close() { events += "close" }
}
class SessionTest {
    private val rom = GameSource.FileDescriptorSource(1,1024,"test.gba")
    @Test fun loadingInBackgroundNeverStartsThenForegroundResumes() = runBlocking {
        val core=FakeCore(); val session=EmulatorSession({core},Dispatchers.Unconfined)
        session.load(rom); assertEquals(SessionState.PAUSED,session.state.value)
        assertFalse(core.events.contains("start"))
        session.setForeground(true); session.setButton(GbaButton.A,true)
        session.setForeground(false); session.close(); session.close()
        assertEquals(listOf("load","resume","button","pause","close"),core.events)
    }
    @Test fun failedLoadClosesCandidateAndClosedSessionRejectsLoad() = runBlocking {
        val core=FakeCore().apply { result=LoadResult.Corrupted("bad") }
        val session=EmulatorSession({core},Dispatchers.Unconfined)
        session.load(rom); assertEquals(SessionState.ERROR,session.state.value)
        assertEquals(listOf("load","close"),core.events)
        session.close(); assertTrue(session.load(rom) is LoadResult.NativeError)
    }
    @Test fun replacementClosesOldInstanceBeforeLoadingNew() = runBlocking {
        val old=FakeCore(); val fresh=FakeCore(); var calls=0
        val session=EmulatorSession({if(calls++==0)old else fresh},Dispatchers.Unconfined)
        session.setForeground(true); session.load(rom); session.load(rom)
        assertEquals(listOf("load","start","close"),old.events)
        assertEquals(listOf("load","start"),fresh.events)
        session.stop(); assertEquals(SessionState.STOPPED,session.state.value)
        assertEquals("close",fresh.events.last())
    }
    @Test fun nativeLibraryFailureBecomesErrorResult() = runBlocking {
        val session=EmulatorSession({throw UnsatisfiedLinkError("test")},Dispatchers.Unconfined)
        assertTrue(session.load(rom) is LoadResult.NativeError)
        assertEquals(SessionState.ERROR,session.state.value)
    }
}
