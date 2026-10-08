package dev.gbalite.storage
import dev.gbalite.core.*
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.io.File

class StorageTest {
    private val id=GameId.fromBytes(byteArrayOf(1))
    private fun root()=Files.createTempDirectory("gba-storage-test").toFile()
    private fun state(slot: Int=1,sequence: Long=0)=StoredState(byteArrayOf(7,0,0,1),StateMetadata(
        gameId=id.value,createdAt="2026-10-06T12:00:00Z",slot=slot,kind=if(slot==0) "auto" else "manual",
        stateSize=4,stateSha256=sha256(byteArrayOf(7,0,0,1)),sequence=sequence))
    @Test fun identityDependsOnlyOnContent() {
        assertEquals(GameId.fromBytes(byteArrayOf(1,2)),GameId.fromBytes(byteArrayOf(1,2)))
        assertNotEquals(GameId.fromBytes(byteArrayOf(1,2)),GameId.fromBytes(byteArrayOf(1,3)))
    }
    @Test fun batteryRestartAndBackupFallback() {
        val root=root(); val storage=AtomicSaveStorage(root)
        assertNull(storage.readBattery(id))
        storage.writeBattery(id,byteArrayOf(1,2)); storage.writeBattery(id,byteArrayOf(3,4))
        assertArrayEquals(byteArrayOf(3,4),AtomicSaveStorage(root).readBattery(id))
        val manifest=com.google.gson.JsonParser.parseString(File(root,"${id.value}/battery/current.json").readText()).asJsonObject
        File(root,"${id.value}/battery/${manifest.get("current").asString}/battery.sav").writeBytes(byteArrayOf(0))
        assertArrayEquals(byteArrayOf(1,2),storage.readBattery(id)); assertNotNull(storage.recoveryNotice)
        root.deleteRecursively()
    }
    @Test fun injectedFailuresPreserveOldSaveAndBackup() {
        for(point in listOf("after-write","before-replace")) {
            val root=root(); AtomicSaveStorage(root).apply { writeBattery(id,byteArrayOf(1)); writeBattery(id,byteArrayOf(2)) }
            val old=File(root,"${id.value}/battery/current.json").readBytes()
            val storage=AtomicSaveStorage(root,fault={ if(it==point) throw java.io.IOException("TEST ONLY disk error") })
            try { storage.writeBattery(id,byteArrayOf(3)); fail() } catch(e: PersistenceException) { assertEquals(PersistenceError.SAVE_RAM_WRITE_FAILED,e.code) }
            assertArrayEquals(old,File(root,"${id.value}/battery/current.json").readBytes())
            assertArrayEquals(byteArrayOf(2),AtomicSaveStorage(root).readBattery(id))
            root.deleteRecursively()
        }
    }
    @Test fun allSlotsQuickAndOverwrite() {
        val root=root(); val s=AtomicSaveStorage(root)
        for(slot in 1..4) { s.writeState(id,"slot-$slot",state(slot)); s.writeState(id,"slot-$slot",state(slot)); assertNotNull(s.readState(id,"slot-$slot")) }
        val q=state().let { it.copy(metadata=it.metadata.copy(slot=0,kind="quick")) }
        s.writeState(id,"quick",q); assertNotNull(s.readState(id,"quick")); assertEquals(5,s.listStates(id).size)
        root.deleteRecursively()
    }
    @Test fun rejectCorruptMissingMetadataWrongRomAndCore() {
        for(mode in listOf("checksum","missing","rom","core","schema","path","nested")) {
            val root=root(); val s=AtomicSaveStorage(root); s.writeState(id,"slot-1",state())
            val dir=File(root,"${id.value}/slot-1")
            val manifest=com.google.gson.JsonParser.parseString(File(dir,"current.json").readText()).asJsonObject
            val gen=File(dir,manifest.get("current").asString)
            val meta=File(gen,"metadata.json")
            when(mode) {
                "checksum" -> File(gen,"game.state").writeBytes(byteArrayOf(9))
                "missing" -> meta.delete()
                "path" -> { manifest.addProperty("current","../escape"); File(dir,"current.json").writeText(manifest.toString()) }
                "nested" -> meta.writeText("{\"schemaVersion\":1,\"nested\":"+"[".repeat(100)+"0"+"]".repeat(100)+"}")
                else -> { val m=com.google.gson.JsonParser.parseString(meta.readText()).asJsonObject
                    if(mode=="schema") m.addProperty("schemaVersion",2)
                    if(mode=="rom") m.addProperty("gameId",GameId.fromBytes(byteArrayOf(2)).value)
                    if(mode=="core") m.addProperty("coreVersion","fake-old")
                    meta.writeText(m.toString())
                }
            }
            try { s.readState(id,"slot-1"); fail(mode) } catch(e: PersistenceException) {
                assertEquals(when(mode) { "rom"->PersistenceError.STATE_ROM_MISMATCH; "core"->PersistenceError.STATE_INCOMPATIBLE; else->PersistenceError.STATE_CORRUPTED },e.code)
            }
            assertTrue(File(gen,"game.state").exists()); root.deleteRecursively()
        }
    }
    @Test fun autosaveRotationAndLatestCorruptFallbackCandidates() {
        val root=root(); val s=AtomicSaveStorage(root)
        for(seq in 1L..4L) s.writeState(id,"auto-${"abc"[((seq-1)%3).toInt()]}",state(0,seq))
        assertEquals(listOf(4L,3L,2L),s.listStates(id).sortedByDescending { it.sequence }.map { it.sequence })
        val dir=File(root,"${id.value}/auto-a")
        val m=com.google.gson.JsonParser.parseString(File(dir,"current.json").readText()).asJsonObject
        File(dir,"${m.get("current").asString}/game.state").writeBytes(byteArrayOf(0))
        assertEquals(3L,s.listStates(id).maxOf { it.sequence })
        val corrupt=File(dir,"${m.get("current").asString}/game.state")
        s.writeState(id,"auto-a",state(0,7)); assertEquals(7L,s.readState(id,"auto-a")!!.metadata.sequence)
        assertTrue("Corrupted old state is retained",corrupt.exists()); root.deleteRecursively()
    }
    @Test fun cleanupOnlyPrivateTemps() {
        val root=root(); val s=AtomicSaveStorage(root); s.writeBattery(id,byteArrayOf(1))
        val temp=File(root,"current.json.tmp.12345678").apply { writeText("incomplete") }
        val other=File(root,"unrelated.tmp").apply { writeText("keep") }
        s.cleanupTemps(); assertFalse(temp.exists()); assertTrue(other.exists()); assertNotNull(s.readBattery(id)); root.deleteRecursively()
    }
}
