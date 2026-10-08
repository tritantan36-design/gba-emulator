package dev.gbalite.mgba

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Fixed basic malformed inputs at the production native FD boundary. */
class Phase7LiteJniTest {
    @Test fun minimumPowerOfTwoRomHasSafeDetectorBackingAndCloses() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("lite-boundary-",".gba",context.cacheDir)
        try {
            // A fixed ARM self-loop plus the signatures accepted by the pinned core.
            // This is a tiny local synthetic image, not an upstream parser harness.
            file.writeBytes(ByteArray(256).also {
                it[0]=0xfe.toByte();it[1]=0xff.toByte();it[2]=0xff.toByte();it[3]=0xea.toByte();it[0xB2]=0x96.toByte()
            })
            for(length in listOf(256L,-1L)) {
                ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd ->
                    val bridge=JniBridge();val handle=bridge.create()
                    try {assertEquals(0,bridge.loadRom(handle,fd.fd,length))}
                    finally {bridge.destroy(handle)}
                    assertFalse(bridge.start(handle))
                }
            }
            assertEquals(256L,file.length())
        } finally {file.delete()}
    }
    @Test fun malformedFilesRejectForKnownAndUnknownLengthsAndReleaseHandles() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File.createTempFile("lite-rom-",".gba",context.cacheDir)
        try {
            val random=ByteArray(4096).also {java.util.Random(7).nextBytes(it);it[0xB2]=0}
            val fixtures=listOf(ByteArray(0),byteArrayOf(1),ByteArray(191),ByteArray(192),
                ByteArray(193).also {it[0xB2]=0x96.toByte()},ByteArray(255).also {it[0xB2]=0x96.toByte()},
                ByteArray(4096),ByteArray(4096){0xff.toByte()},random)
            for((index,bytes) in fixtures.withIndex()) {
                file.writeBytes(bytes)
                for(length in listOf(bytes.size.toLong(),-1L)) {
                    ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd ->
                        val bridge=JniBridge();val handle=bridge.create()
                        try {
                            assertEquals("fixture=$index length=$length",2,bridge.loadRom(handle,fd.fd,length))
                            assertFalse(bridge.start(handle))
                        } finally {bridge.destroy(handle)}
                        assertFalse(bridge.start(handle))
                    }
                }
            }
            // Sparse invalid input exercises size rejection without a large JVM allocation.
            java.io.RandomAccessFile(file,"rw").use {it.setLength(33554433)}
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {fd ->
                MgbaCoreAdapter().use {core ->
                    assertTrue(core.loadGame(GameSource.FileDescriptorSource(fd.fd,file.length(),file.name)) is LoadResult.Corrupted)
                }
            }
        } finally {file.delete()}
    }
}
