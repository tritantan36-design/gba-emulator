package dev.gbalite.storage

import com.google.gson.JsonParser
import dev.gbalite.core.*
import java.io.File
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Fixed, local fixtures only; every test owns its temporary directory. */
class Phase7LiteProtectionTest {
    private val id=GameId.fromBytes("lite-a".toByteArray())
    private fun root(body: (File)->Unit) {
        val root=Files.createTempDirectory("phase7-lite-").toFile()
        try {body(root)} finally {root.deleteRecursively()}
    }
    private fun snapshot(root: File)=root.walkTopDown().filter {it.isFile}
        .associate {it.relativeTo(root).path to sha256(it.readBytes())}
    private fun state(id: GameId=this.id,seq: Long=0): StoredState {
        val bytes=byteArrayOf(7,0,0,1,seq.toByte())
        return StoredState(bytes,StateMetadata(gameId=id.value,createdAt="2026-10-08T00:00:00Z",
            slot=0,kind=if(seq==0L) "quick" else "auto",stateSize=bytes.size,stateSha256=sha256(bytes),sequence=seq))
    }
    private fun current(dir: File)=File(dir,JsonParser.parseString(File(dir,"current.json").readText()).asJsonObject.get("current").asString)
    @Test fun headerOnlyAndTruncatedMarkerRomsRejectedBeforeSnapshot()=root {root ->
        for(size in listOf(192,193,255)) {
            val bytes=ByteArray(size).also {it[0xB2]=0x96.toByte()}
            try {RomImport(root).import(bytes.inputStream(),"truncated.gba");fail("accepted marker-only size=$size")}
            catch(e: ImportFailure) {assertEquals(ImportError.INVALID_GBA,e.error)}
            assertTrue(root.listFiles()!!.isEmpty())
        }
    }
    @Test fun invalidStateWritesNeverReplaceGoodQuickOrOtherGame()=root {root ->
        val s=AtomicSaveStorage(root);s.writeState(id,"quick",state())
        val other=GameId.fromBytes("lite-b".toByteArray());s.writeState(other,"quick",state(other))
        val before=snapshot(root)
        for(bad in listOf(state().copy(bytes=byteArrayOf(7)),state(other),state().let {it.copy(metadata=it.metadata.copy(stateSha256="bad"))})) {
            try {s.writeState(id,"quick",bad);fail()} catch(e: PersistenceException) {assertEquals(PersistenceError.STATE_WRITE_FAILED,e.code)}
            assertEquals(before,snapshot(root));assertArrayEquals(state().bytes,s.readState(id,"quick")!!.bytes)
        }
    }
    @Test fun invalidAutosavePointerAndMissingMetadataLeaveOtherCandidatesAndFiles()=root {root ->
        val s=AtomicSaveStorage(root)
        for(seq in 1L..3L) s.writeState(id,"auto-${"abc"[(seq-1).toInt()]}",state(seq=seq))
        File(root,"${id.value}/auto-c/current.json").writeText("{invalid")
        File(current(File(root,"${id.value}/auto-b")),"metadata.json").delete()
        val before=snapshot(root)
        assertEquals(listOf(1L),s.listStates(id).map {it.sequence})
        assertEquals(before,snapshot(root))
    }
    @Test fun missingBackupKeepsValidBatteryAndCorruptBatteryFailsWithoutWriting()=root {root ->
        val s=AtomicSaveStorage(root);s.writeBattery(id,byteArrayOf(1));s.writeBattery(id,byteArrayOf(2))
        val dir=File(root,"${id.value}/battery")
        val manifest=JsonParser.parseString(File(dir,"current.json").readText()).asJsonObject
        File(dir,manifest.get("previous").asString).deleteRecursively()
        assertArrayEquals(byteArrayOf(2),s.readBattery(id))
        File(current(dir),"battery.sav").writeBytes(byteArrayOf(0))
        val before=snapshot(root)
        try {s.writeBattery(id,byteArrayOf(3));fail()} catch(e: PersistenceException) {assertEquals(PersistenceError.SAVE_RAM_WRITE_FAILED,e.code)}
        assertEquals(before,snapshot(root))
    }
    @Test fun invalidBatteryManifestAndUnknownVersionPreserveGenerations()=root {root ->
        val s=AtomicSaveStorage(root);s.writeBattery(id,byteArrayOf(1))
        val file=File(root,"${id.value}/battery/current.json");val valid=file.readText()
        for(bad in listOf("{",valid.replace("\"schemaVersion\":1","\"schemaVersion\":999"),"{\"schemaVersion\":1,\"current\":\"../other\"}")) {
            file.writeText(bad);val before=snapshot(root)
            try {s.readBattery(id);fail()} catch(e: PersistenceException) {assertEquals(PersistenceError.SAVE_RAM_READ_FAILED,e.code)}
            assertEquals(before,snapshot(root))
        }
        file.writeText(valid);assertArrayEquals(byteArrayOf(1),s.readBattery(id))
    }
    @Test fun zipTraversalCannotChangeOwnedSiblingAndMalformedRomKeepsSnapshot()=root {outer ->
        val root=File(outer,"imports").apply {mkdir()}
        val sibling=File(outer,"evil.gba").apply {writeText("keep")}
        val good=ByteArray(4096).also {java.util.Random(11).nextBytes(it);it[3]=0xEA.toByte();it[0xB2]=0x96.toByte()}
        RomImport(root).import(good.inputStream(),"good.gba")
        val before=snapshot(outer)
        for(path in listOf("../evil.gba","/evil.gba","C:/evil.gba","nested/../../evil.gba")) {
            val zip=ByteArrayOutputStream().also {out ->ZipOutputStream(out).use {z ->
                z.putNextEntry(ZipEntry(path));z.write(good);z.closeEntry()
            }}.toByteArray()
            try {RomImport(root).import(zip.inputStream(),"bad.zip");fail()} catch(e: ImportFailure) {assertEquals(ImportError.ZIP_UNSAFE,e.error)}
            assertEquals(before,snapshot(outer));assertEquals("keep",sibling.readText())
        }
        for(size in listOf(0,1,191,192,4096)) {
            val bad=ByteArray(size).also {java.util.Random(17).nextBytes(it);if(size>0xB2) it[0xB2]=0}
            try {RomImport(root).import(bad.inputStream(),"bad.gba");fail()} catch(e: ImportFailure) {assertEquals(ImportError.INVALID_GBA,e.error)}
            assertEquals(before,snapshot(outer))
        }
        val invalidBranch=good.copyOf().also {it[3]=0}
        try {RomImport(root).import(invalidBranch.inputStream(),"bad-header.gba");fail()} catch(e: ImportFailure) {assertEquals(ImportError.INVALID_GBA,e.error)}
        assertEquals(before,snapshot(outer))
    }
    @Test fun zipSourceLimitIsStreamingAndLeavesNoPartialImport()=root {root ->
        val source=object: java.io.InputStream() {
            var remaining=RomImport.INPUT_MAX+1
            override fun read(): Int=if(remaining-->0) 0 else -1
            override fun read(bytes: ByteArray,offset: Int,length: Int): Int {
                if(remaining<=0) return -1
                val count=minOf(remaining,length.toLong()).toInt()
                java.util.Arrays.fill(bytes,offset,offset+count,0.toByte());remaining-=count
                return count
            }
        }
        try {RomImport(root).import(source,"oversized.zip");fail()}
        catch(e: ImportFailure) {assertEquals(ImportError.TOO_LARGE,e.error)}
        assertTrue(root.listFiles()!!.isEmpty())
    }
}
