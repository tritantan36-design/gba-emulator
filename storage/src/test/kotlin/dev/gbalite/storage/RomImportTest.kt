package dev.gbalite.storage

import dev.gbalite.core.GameId
import java.io.*
import java.nio.file.Files
import java.util.zip.*
import org.junit.Assert.*
import org.junit.Test

class RomImportTest {
    private fun gba(seed: Int=0)=ByteArray(4096).also {java.util.Random(seed.toLong()).nextBytes(it);it[3]=0xEA.toByte();it[0xB2]=0x96.toByte()}
    private fun zip(vararg entries: Pair<String,ByteArray>): ByteArray=ByteArrayOutputStream().also {out ->
        ZipOutputStream(out).use {z->entries.forEach {(name,bytes)->z.putNextEntry(ZipEntry(name));z.write(bytes);z.closeEntry()}}
    }.toByteArray()
    private fun test(block: (File)->Unit) {val root=Files.createTempDirectory("rom-import-test").toFile();try {block(root)} finally {root.deleteRecursively()}}
    private fun rejected(bytes: ByteArray,error: ImportError=ImportError.ZIP_UNSAFE) = test {root ->
        val good=RomImport(root).import(gba(42).inputStream(),"prior-valid.gba")
        val before=root.listFiles()!!.associate {it.name to it.readBytes().toList()}
        try {RomImport(root).import(bytes.inputStream(),"test.zip");fail("unsafe ZIP accepted")}
        catch(e: ImportFailure) {assertEquals(error,e.error)}
        assertEquals(before,root.listFiles()!!.associate {it.name to it.readBytes().toList()})
        assertArrayEquals(gba(42),good.file.readBytes())
    }
    private fun central(bytes: ByteArray): Int=(0..bytes.size-4).first {i->bytes[i]==0x50.toByte()&&bytes[i+1]==0x4b.toByte()&&bytes[i+2]==1.toByte()&&bytes[i+3]==2.toByte()}
    @Test fun directZipAndRenameHaveIdenticalContentIdentity()=test {root->
        val b=gba();val one=RomImport(root).import(b.inputStream(),"My_Game.gba")
        val two=RomImport(root).import(zip("game.gba" to b,"README.txt" to "original Apache2 test".toByteArray()).inputStream(),"bundle.zip")
        val three=RomImport(root).import(b.inputStream(),"renamed.gba")
        assertEquals(GameId.fromBytes(b),one.id);assertEquals(one.id,two.id);assertEquals(one.id,three.id)
        assertEquals("My Game",one.title);assertEquals(1,root.listFiles()!!.size)
        assertNotEquals(one.id,RomImport(root).import(gba(1).inputStream(),"My_Game.gba").id)
    }
    @Test fun exactlyOneGba() {rejected(zip("README.txt" to byteArrayOf(1)),ImportError.ZIP_EMPTY);rejected(zip("a.gba" to gba(),"b.gba" to gba(1)),ImportError.ZIP_MULTIPLE)}
    @Test fun traversalAndAbsoluteDriveAndBackslashRejected() {
        listOf("../evil.gba","/absolute.gba","nested/../../evil.gba","C:/evil.gba","nested\\evil.gba","a//b.gba").forEach {rejected(zip(it to gba()))}
    }
    @Test fun nestedArchiveAndUnknownExtraRejected() {rejected(zip("a.gba" to gba(),"nested.zip" to zip("b.gba" to gba())));rejected(zip("a.gba" to gba(),"plugin.so" to byteArrayOf(1)))}
    @Test fun hugeDeclaredSizesAndRatioRejected() {
        val bytes=zip("a.gba" to gba());val at=central(bytes)
        val huge=bytes.copyOf();repeat(4) {huge[at+24+it]=0x7f};rejected(huge)
        rejected(zip("a.gba" to ByteArray(1024*1024).also {it[0xB2]=0x96.toByte()}))
    }
    @Test fun tooManyAndLongPathsRejected() {
        rejected(zip(*((0..64).map {"d$it/README.txt" to byteArrayOf(1)}+listOf("a.gba" to gba())).toTypedArray()))
        rejected(zip(("a".repeat(241)+".gba") to gba()))
    }
    @Test fun corruptDirectoryTruncationAndEncryptionRejected() {
        val bytes=zip("a.gba" to gba());val at=central(bytes)
        rejected(bytes.copyOf().also {it[at]=0})
        rejected(bytes.copyOf(bytes.size-3))
        rejected(bytes.copyOf().also {it[at+8]=(it[at+8].toInt() or 1).toByte()})
    }
    @Test fun invalidUtf8DuplicateCanonicalNamesAndCrcRejected() {
        val bytes=zip("a.gba" to gba());val at=central(bytes)
        rejected(bytes.copyOf().also {it[at+46]=0xff.toByte()})
        rejected(zip("A.gba" to gba(),"a.gba" to gba()))
        val duplicate=zip("a.gba" to gba(),"b.gba" to gba(1))
        val second=central(duplicate)+46+5
        val local=(0..3).fold(0) {value,i ->value or ((duplicate[second+42+i].toInt() and 255) shl (i*8))}
        duplicate[second+46]='a'.code.toByte();duplicate[local+30]='a'.code.toByte()
        rejected(duplicate)
        rejected(zip("é.gba" to gba(),"e\u0301.gba" to gba()))
        rejected(bytes.copyOf().also {it[at+16]=(it[at+16].toInt() xor 1).toByte()})
        test {root ->assertEquals(GameId.fromBytes(gba()),RomImport(root).import(zip("游戏.gba" to gba()).inputStream(),"ok.zip").id)}
    }
    @Test fun relinkMismatchAndCancellationLeaveOldSnapshotUntouched()=test {root->
        val old=RomImport(root).import(gba().inputStream(),"a.gba")
        try {RomImport(root).import(gba(1).inputStream(),"a.gba",old.id);fail()} catch(e: ImportFailure) {assertEquals(ImportError.MISMATCH,e.error)}
        var checks=0
        try {RomImport(root) {if(++checks>2) throw java.util.concurrent.CancellationException()}.import(zip("a.gba" to gba(1)).inputStream(),"a.zip");fail()} catch(_: java.util.concurrent.CancellationException) {}
        assertEquals(1,root.listFiles()!!.size);assertArrayEquals(gba(),old.file.readBytes())
    }
    @Test fun boundedReadAndInvalidHeaderRejectWithoutSnapshot()=test {root->
        try {RomImport(root).import(ByteArray(200).inputStream(),"bad.gba");fail()} catch(e: ImportFailure) {assertEquals(ImportError.INVALID_GBA,e.error)}
        val stream=object: InputStream() {override fun read()=0;override fun read(b: ByteArray,off: Int,len: Int): Int {b.fill(0,off,off+len);return len}}
        try {RomImport(root).import(stream,"large.gba");fail()} catch(e: ImportFailure) {assertEquals(ImportError.TOO_LARGE,e.error)}
        assertTrue(root.listFiles()!!.isEmpty())
    }
}
