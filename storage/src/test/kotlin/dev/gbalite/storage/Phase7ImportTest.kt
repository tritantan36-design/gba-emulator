package dev.gbalite.storage

import java.io.*
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class Phase7ImportTest {
    @Test fun interruptedPrivateTempsRemovedButUnknownFilesAndSnapshotsKept() {
        val root=Files.createTempDirectory("phase7-import-cleanup").toFile()
        try {
            val owned=File(root,"rom.tmp.${UUID.randomUUID()}").apply {writeBytes(byteArrayOf(1))}
            val unknown=File(root,"rom.tmp.keep-user-data").apply {writeBytes(byteArrayOf(2))}
            val snapshot=File(root,"${"a".repeat(64)}.gba").apply {writeBytes(byteArrayOf(3))}
            val bytes=ByteArray(512).also {it[3]=0xEA.toByte();it[0xB2]=0x96.toByte()}
            RomImport(root).import(bytes.inputStream(),"test.gba")
            assertFalse(owned.exists());assertArrayEquals(byteArrayOf(2),unknown.readBytes());assertArrayEquals(byteArrayOf(3),snapshot.readBytes())
        } finally {root.deleteRecursively()}
    }
    @Test fun malformedGbaBoundaryAndStreamingOverLimitFailClosed() {
        for(size in listOf(0,1,191,192,193,4096)) {
            val root=Files.createTempDirectory("phase7-malformed").toFile()
            try {
                for(value in listOf(0,255)) {
                    try {RomImport(root).import(ByteArray(size){value.toByte()}.inputStream(),"bad.gba");fail("accepted size=$size value=$value")}
                    catch(e: ImportFailure) {assertEquals(ImportError.INVALID_GBA,e.error)}
                    assertTrue(root.listFiles()!!.isEmpty())
                }
            } finally {root.deleteRecursively()}
        }
        val root=Files.createTempDirectory("phase7-oversize").toFile()
        try {
            val source=object: InputStream() {
                var remaining=RomImport.INPUT_MAX+1
                override fun read(): Int=if(remaining-->0) 0 else -1
                override fun read(b: ByteArray,off: Int,len: Int): Int {
                    if(remaining<=0) return -1
                    val n=minOf(len.toLong(),remaining).toInt();java.util.Arrays.fill(b,off,off+n,0);remaining-=n;return n
                }
            }
            try {RomImport(root).import(source,"huge.gba");fail("oversize accepted")}
            catch(e: ImportFailure) {assertEquals(ImportError.TOO_LARGE,e.error)}
            assertTrue(root.listFiles()!!.isEmpty())
        } finally {root.deleteRecursively()}
    }
}
