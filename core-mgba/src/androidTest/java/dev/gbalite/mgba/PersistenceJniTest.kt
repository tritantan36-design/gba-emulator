package dev.gbalite.mgba
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) class PersistenceJniTest {
    private fun rom(): File {
        val i=InstrumentationRegistry.getInstrumentation()
        return File(i.targetContext.cacheDir,"persistence.gba").also { f->
            i.context.assets.open("persistence.gba").use { src->f.outputStream().use { src.copyTo(it) } }
        }
    }
    private fun loaded(file: File): MgbaCoreAdapter {
        val core=MgbaCoreAdapter()
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd->
            assertEquals(LoadResult.Success,core.loadGame(GameSource.FileDescriptorSource(fd.fd,file.length(),file.name)))
        }; return core
    }
    @Test fun realBatterySurvivesReopenAndStateCannotRollbackIt() {
        val file=rom()
        var battery: ByteArray
        var state: ByteArray
        loaded(file).use { core->
            core.start(); Thread.sleep(200); core.setButton(GbaButton.A,true); Thread.sleep(180); core.pause()
            battery=(core.exportSaveRam() as BytesResult.Success).bytes; assertTrue(battery.isNotEmpty())
            state=(core.exportState() as BytesResult.Success).bytes
            val updated=battery.copyOf().apply { this[0]=42 }
            assertTrue(core.importSaveRam(updated)); assertTrue(core.importState(state))
            assertArrayEquals(updated,(core.exportSaveRam() as BytesResult.Success).bytes)
            battery=updated
        }
        loaded(file).use { core->
            assertTrue(core.importSaveRam(battery)); core.start(); Thread.sleep(150); core.pause()
            assertArrayEquals(battery,(core.exportSaveRam() as BytesResult.Success).bytes)
            assertTrue(core.importState(state)); assertArrayEquals(battery,(core.exportSaveRam() as BytesResult.Success).bytes)
        }
        file.delete()
    }
    @Test fun corruptedAndOversizedStatesRejectedAndRepeatedSaveCloseNoHandles() {
        val file=rom(); val bridge=JniBridge()
        repeat(20) {
            loaded(file).use { core->
                core.start(); Thread.sleep(70); core.pause()
                val bytes=(core.exportState() as BytesResult.Success).bytes
                assertFalse(core.importState(bytes.copyOf(bytes.size-1)))
                assertFalse(core.importState(bytes.copyOf().apply { this[0]=99 }))
                assertFalse(core.importState(ByteArray(2097153)))
                assertTrue(core.importState(bytes))
                assertTrue(core.exportSaveRam() is BytesResult.Success)
            }
        }
        assertEquals(0,bridge.activeHandlesForTest()); file.delete()
    }
}
