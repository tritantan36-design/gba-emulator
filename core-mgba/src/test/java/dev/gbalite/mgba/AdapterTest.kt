package dev.gbalite.mgba
import dev.gbalite.core.*
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

private class FakeBridge : NativeBridge {
    var destroyed = 0; var loads = 0; var starts = 0; var pauses = 0
    var id = 7L; var result = 0; var audioWorks = true
    override fun create() = id
    override fun destroy(handle: Long) { destroyed++ }
    override fun loadRom(handle: Long, fd: Int, length: Long): Int { loads++; return result }
    override fun start(handle: Long): Boolean { starts++; return audioWorks }
    override fun pause(handle: Long): Boolean { pauses++; return true }
    override fun reset(handle: Long) = true
    override fun setButton(handle: Long, mask: Int, down: Boolean) {}
    override fun copyFrame(handle: Long, target: ByteBuffer) = true
}
class AdapterTest {
    private val rom = GameSource.FileDescriptorSource(1,1024,"test.gba")
    @Test fun doubleCloseDestroysExactlyOnceAndFrameAfterCloseIsSafe() {
        val bridge = FakeBridge(); val adapter = MgbaCoreAdapter(bridge)
        adapter.close(); adapter.close()
        assertEquals(1,bridge.destroyed)
        assertFalse(adapter.frames.copyFrame(ByteBuffer.allocateDirect(153600)))
    }
    @Test(expected = IllegalStateException::class) fun startBeforeLoadIsRejected() { MgbaCoreAdapter(FakeBridge()).start() }
    @Test(expected = IllegalStateException::class) fun createFailureIsStructuredAtSessionBoundary() {
        MgbaCoreAdapter(FakeBridge().apply { id = 0 })
    }
    @Test fun lifecycleAndClosedState() {
        val bridge = FakeBridge(); val adapter = MgbaCoreAdapter(bridge)
        assertEquals(LoadResult.Success,adapter.loadGame(rom))
        adapter.start(); adapter.pause(); adapter.pause(); adapter.resume(); adapter.stop(); adapter.close()
        assertEquals(2,bridge.starts); assertEquals(2,bridge.pauses)
    }
    @Test fun rejectsExtensionAndSizeBeforeNativeRead() {
        val bridge = FakeBridge(); val adapter = MgbaCoreAdapter(bridge)
        assertTrue(adapter.loadGame(rom.copy(displayName="a.zip")) is LoadResult.Unsupported)
        assertTrue(adapter.loadGame(rom.copy(length=33554433)) is LoadResult.Corrupted)
        for(size in listOf(0L,1L,191L,192L,255L))
            assertTrue(adapter.loadGame(rom.copy(length=size)) is LoadResult.Corrupted)
        assertTrue(adapter.loadGame(rom.copy(fd=-1)) is LoadResult.IoError)
        assertEquals(0,bridge.loads); adapter.close()
    }
    @Test fun corruptedRomDoesNotStart() {
        val bridge = FakeBridge().apply { result=2 }; val adapter = MgbaCoreAdapter(bridge)
        assertTrue(adapter.loadGame(rom) is LoadResult.Corrupted)
        assertEquals(0,bridge.starts); adapter.close()
    }
    @Test(expected=IllegalStateException::class) fun audioFailureIsNotReportedAsRunning() {
        val adapter = MgbaCoreAdapter(FakeBridge().apply { audioWorks=false })
        try { adapter.loadGame(rom); adapter.start() } finally { adapter.close() }
    }
}
