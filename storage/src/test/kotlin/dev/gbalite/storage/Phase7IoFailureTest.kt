package dev.gbalite.storage

import dev.gbalite.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Controlled ordinary durability failures in newly created test directories. */
class Phase7IoFailureTest {
    private val id=GameId.fromBytes("phase7-io-only".toByteArray())
    private fun useRoot(body: (File)->Unit) {
        val root=Files.createTempDirectory("gba-phase7-io-").toFile()
        try {body(root)} finally {
            val boundary=root.canonicalFile.toPath()
            root.walkBottomUp().forEach {file ->
                check(file.canonicalFile.toPath().startsWith(boundary));check(file.delete())
            }
        }
    }
    private fun seed(root: File): ByteArray {
        AtomicSaveStorage(root).apply {writeBattery(id,byteArrayOf(1));writeBattery(id,byteArrayOf(2))}
        return File(root,"${id.value}/battery/current.json").readBytes()
    }
    private fun assertUnchanged(root: File,manifest: ByteArray) {
        assertArrayEquals(manifest,File(root,"${id.value}/battery/current.json").readBytes())
        assertArrayEquals(byteArrayOf(2),AtomicSaveStorage(root).readBattery(id))
    }
    @Test fun directoryFsyncFailurePreservesCommittedBatteryAndManifest()=useRoot {root ->
        val old=seed(root)
        val store=AtomicSaveStorage(root,syncDirectory={throw java.io.IOException("TEST ONLY directory fsync failed")})
        try {store.writeBattery(id,byteArrayOf(3));fail()} catch(e: PersistenceException) {
            assertEquals(PersistenceError.SAVE_RAM_WRITE_FAILED,e.code)
        }
        assertUnchanged(root,old)
    }
    @Test fun missingCommitTempCausesRealRenameFailureWithoutChangingPriorGeneration()=useRoot {root ->
        val old=seed(root)
        val dir=File(root,"${id.value}/battery")
        val store=AtomicSaveStorage(root,fault={point ->
            if(point=="before-replace") {
                val temporary=dir.listFiles()!!.single {it.name.startsWith("current.json.tmp.")}
                check(temporary.delete()) // Real Files.move then fails with missing source.
            }
        })
        try {store.writeBattery(id,byteArrayOf(3));fail()} catch(e: PersistenceException) {
            assertEquals(PersistenceError.SAVE_RAM_WRITE_FAILED,e.code)
        }
        assertUnchanged(root,old)
    }
    @Test fun injectedNoSpaceErrorAfterWritePreservesPriorValidGeneration()=useRoot {root ->
        val old=seed(root)
        val store=AtomicSaveStorage(root,fault={point ->
            if(point=="after-write") throw java.nio.file.FileSystemException("test-save",null,"TEST ONLY No space left on device")
        })
        try {store.writeBattery(id,byteArrayOf(3));fail()} catch(e: PersistenceException) {
            assertEquals(PersistenceError.SAVE_RAM_WRITE_FAILED,e.code)
        }
        assertUnchanged(root,old)
    }
}
