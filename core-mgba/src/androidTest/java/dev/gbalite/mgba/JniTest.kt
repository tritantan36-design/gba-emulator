package dev.gbalite.mgba
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) class JniTest {
    @Test fun staleAndInvalidHandleCannotAccessCore() {
        val bridge=JniBridge(); val handle=bridge.create()
        assertTrue(handle>0); bridge.destroy(handle); bridge.destroy(handle); bridge.destroy(0)
        assertFalse(bridge.start(handle)); assertFalse(bridge.pause(0)); assertFalse(bridge.reset(-7))
        assertEquals(4,bridge.loadRom(handle,-1,0))
        assertFalse(bridge.copyFrame(handle,ByteBuffer.allocateDirect(153600)))
    }
    @Test fun invalidRomIsRejectedWithoutNativeCrash() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"invalid.gba").apply { writeBytes(ByteArray(256)) }
        try {
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                MgbaCoreAdapter().use { core ->
                    assertTrue(core.loadGame(GameSource.FileDescriptorSource(fd.fd,256,"invalid.gba")) is LoadResult.Corrupted)
                }
            }
        } finally { file.delete() }
    }
    @Test fun homebrewProducesFramesAndInputAndReleasesSafely() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.cacheDir,"bringup.gba")
        instrumentation.context.assets.open("bringup.gba").use { input -> file.outputStream().use { input.copyTo(it) } }
        try {
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                MgbaCoreAdapter().use { core ->
                    assertEquals(LoadResult.Success,core.loadGame(GameSource.FileDescriptorSource(fd.fd,file.length(),file.name)))
                    core.start()
                    val frame=ByteBuffer.allocateDirect(153600)
                    val evidence=File(instrumentation.targetContext.filesDir,"phase7-input-${System.currentTimeMillis()}.txt")
                    fun pixels()=ByteArray(4).also { frame.position(0); frame.get(it) }
                    fun record(label: String) {
                        evidence.appendText("$label rgba=${pixels().map { it.toInt() and 255 }} metrics=${core.playerMetrics()}\n")
                    }
                    val deadline=System.currentTimeMillis()+5000
                    while(!core.frames.copyFrame(frame) && System.currentTimeMillis()<deadline) Thread.sleep(20)
                    assertTrue(core.frames.copyFrame(frame))
                    Thread.sleep(100); core.frames.copyFrame(frame)
                    record("startup")
                    // The HLE startup/partially filled first frame is not the ROM's
                    // released-key baseline. bringup.s specifies opaque pure red.
                    val ready=System.currentTimeMillis()+5000
                    val released=byteArrayOf(255.toByte(),0,0,255.toByte())
                    while(!pixels().contentEquals(released) && System.currentTimeMillis()<ready) {
                        Thread.sleep(20); core.frames.copyFrame(frame)
                    }
                    record("ready")
                    assertArrayEquals("ROM must publish its known no-key red frame",released,pixels())
                    assertEquals(255,frame.get(0).toInt() and 255)
                    val initial=ByteArray(4).also { frame.position(0); frame.get(it) }
                    fun waitPixels(changed: Boolean): Boolean {
                        val limit=System.currentTimeMillis()+2000
                        do {
                            core.frames.copyFrame(frame)
                            val pixels=ByteArray(4).also { frame.position(0); frame.get(it) }
                            if(initial.contentEquals(pixels)!=changed) return true
                            Thread.sleep(20)
                        } while(System.currentTimeMillis()<limit)
                        return false
                    }
                    for(button in GbaButton.entries) {
                        core.setButton(button,true)
                        val pressed=waitPixels(true);record("${button.name} pressed=$pressed")
                        assertTrue("${button.name} must change pixels within two seconds",pressed)
                        core.setButton(button,false)
                        val restored=waitPixels(false);record("${button.name} restored=$restored")
                        assertTrue("${button.name} release must restore pixels within two seconds",restored)
                    }
                    core.pause(); core.resume(); core.reset(); core.stop(); core.close(); core.close()
                    assertFalse(core.frames.copyFrame(frame))
                }
            }
        } finally { file.delete() }
    }
    @Test fun homebrewProducesNonSilentPcm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "audio-test.gba")
        instrumentation.context.assets.open("bringup.gba").use { input -> file.outputStream().use { input.copyTo(it) } }
        val bridge = JniBridge()
        val handle = bridge.create()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                assertEquals(0, bridge.loadRom(handle, fd.fd, file.length()))
            }
            assertTrue(bridge.start(handle))
            val deadline = System.currentTimeMillis() + 5000
            while (bridge.nonzeroSamplesForTest(handle) == 0L && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertTrue("mGBA must generate audible PCM", bridge.nonzeroSamplesForTest(handle) > 0)
            val audioDeadline = System.currentTimeMillis() + 5000
            while (bridge.playedForTest(handle) == 0L && System.currentTimeMillis() < audioDeadline) Thread.sleep(20)
            assertTrue("Oboe must consume non-silent PCM", bridge.playedForTest(handle) > 0)
            assertTrue(bridge.pause(handle))
            val pausedFrames = bridge.framesForTest(handle)
            Thread.sleep(100)
            assertEquals(pausedFrames, bridge.framesForTest(handle))
            assertTrue(bridge.start(handle)); Thread.sleep(100)
            assertTrue(bridge.framesForTest(handle) > pausedFrames)
        } finally { bridge.destroy(handle); file.delete() }
    }
    @Test fun repeatedCreateDestroyLeavesNoLiveHandles() {
        val bridge = JniBridge()
        val before = bridge.activeHandlesForTest()
        repeat(30) { val handle = bridge.create(); bridge.destroy(handle); bridge.destroy(handle) }
        assertEquals(before, bridge.activeHandlesForTest())
    }
}
