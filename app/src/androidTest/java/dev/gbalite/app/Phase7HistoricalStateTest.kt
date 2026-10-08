package dev.gbalite.app

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import dev.gbalite.mgba.MgbaCoreAdapter
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** Core integration test against actual archived application APKs on an isolated AVD.
 * No UI or production layer bypass; this class is present only in the test APK.
 * Historical raw native states are distinguished from metadata/Room upgrade tests.
 */
class Phase7HistoricalStateTest {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private val args get()=InstrumentationRegistry.getArguments()
    @Suppress("DEPRECATION") private fun version()=i.targetContext.packageManager
        .getPackageInfo(i.targetContext.packageName,0).versionCode
    private fun directory(phase: Int): File {
        val run=args.getString("sourceRun","")!!
        require(run=="" || run=="settled")
        return File(i.targetContext.filesDir,"phase7-historical-state-$phase"+if(run.isEmpty()) "" else "-$run")
    }
    private fun withCore(block: (EmulatorCore)->Unit) {
        val rom=File(i.targetContext.cacheDir,"phase7-historical-state.gba")
        i.context.assets.open("phase7-upgrade.gba").use {input ->rom.outputStream().use {input.copyTo(it)}}
        val core=MgbaCoreAdapter()
        try {
            ParcelFileDescriptor.open(rom,ParcelFileDescriptor.MODE_READ_ONLY).use {fd ->
                assertEquals(LoadResult.Success,core.loadGame(GameSource.FileDescriptorSource(fd.fd,rom.length(),rom.name)))
            }
            block(core)
        } finally {core.close();rom.delete()}
    }
    private fun frame(core: EmulatorCore): ByteArray {
        val buffer=ByteBuffer.allocateDirect(240*160*4)
        assertTrue(core.frames.copyFrame(buffer))
        buffer.position(0);return ByteArray(buffer.capacity()).also {buffer.get(it)}
    }
    @Test fun captureStateFromActualArchivedPhase() {
        assumeTrue("Explicit archived APK capture",args.getString("phase7LegacyState")=="seed")
        val phase=args.getString("sourcePhase")!!.toInt();assertTrue(phase in 2..6);assertEquals(phase,version())
        val directory=directory(phase)
        assertFalse("Preserve prior historical evidence",directory.exists());assertTrue(directory.mkdir())
        withCore {core ->
            core.start();Thread.sleep(1200);core.setButton(GbaButton.START,true);Thread.sleep(100)
            core.setButton(GbaButton.START,false);Thread.sleep(250);core.pause()
            val bytes=(core.exportState() as BytesResult.Success).bytes
            File(directory,"state.bin").writeBytes(bytes)
            File(directory,"frame.rgba").writeBytes(frame(core))
            File(directory,"proof.json").writeText(JSONObject().put("createdByVersionCode",version())
                .put("stateSha256",sha256(bytes)).put("romSha256",i.context.assets.open("phase7-upgrade.gba").use {sha256(it.readBytes())})
                .put("kind","actual historical raw core state; no fabricated metadata").toString(2))
        }
    }
    @Test fun phaseSevenImportsActualArchivedNativeState() {
        assumeTrue("Explicit archived state verification",args.getString("phase7LegacyState")=="verify")
        assertEquals(7,version());val phase=args.getString("sourcePhase")!!.toInt()
        val directory=directory(phase)
        val proof=JSONObject(File(directory,"proof.json").readText())
        assertEquals(phase,proof.getInt("createdByVersionCode"))
        val bytes=File(directory,"state.bin").readBytes();assertEquals(proof.getString("stateSha256"),sha256(bytes))
        withCore {core ->
            core.start();Thread.sleep(800);core.pause();assertTrue(core.importState(bytes))
            // Import invalidates the published framebuffer until the next actual
            // emulated frame. The ROM's released-button color is now stable.
            core.resume();Thread.sleep(250);core.pause()
            assertArrayEquals(File(directory,"frame.rgba").readBytes(),frame(core))
            core.resume();Thread.sleep(500);core.pause();assertTrue(core.exportState() is BytesResult.Success)
        }
        File(directory,"phase7-read-result.json").writeText(JSONObject().put("result","PASS")
            .put("fromVersionCode",phase).put("toVersionCode",7).put("frameEqual",true).toString(2))
    }
}
