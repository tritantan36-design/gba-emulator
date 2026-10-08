package dev.gbalite.storage

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import dev.gbalite.core.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.time.Instant
import java.util.UUID

/** Directory flush hook is supplied by Android; tests inject faults before commit. */
class AtomicSaveStorage(private val root: File,
    private val syncDirectory: (File) -> Unit = {},
    private val fault: (String) -> Unit = {}) {
    companion object { private val commitLock=Any() }
    var recoveryNotice: String? = null; private set
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    init { if(!root.exists()) root.mkdirs() } // Surface IO failure through the first structured storage operation.
    private fun directory(id: GameId, key: String): File {
        require(key == "battery" || key == "quick" || key.matches(Regex("slot-[1-4]|auto-[abc]")))
        return File(root, "${id.value}/$key").also { check(it.mkdirs() || it.isDirectory) }
    }
    private fun json(file: File): JsonObject {
        require(file.length() in 2..16384)
        val reader=JsonReader(StringReader(file.readText(Charsets.UTF_8))).apply {
            strictness=Strictness.STRICT; nestingLimit=8
        }
        return reader.use { JsonParser.parseReader(it).asJsonObject.also { obj ->
            require(it.peek()==JsonToken.END_DOCUMENT)
            require(obj.entrySet().all { entry -> entry.value.isJsonPrimitive })
            require(obj.get("schemaVersion").asInt == 1)
        } }.also {
            require(it.size()<=16)
        }
    }
    private fun manifest(dir: File): JsonObject? = File(dir,"current.json").takeIf { it.exists() }?.let(::json)
    private fun generation(dir: File, name: String): File {
        require(uuid.matches(name)); return File(dir,name)
    }
    private fun durable(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { it.write(bytes); it.flush(); it.fd.sync() }
        fault("after-write")
        check(file.length() == bytes.size.toLong())
        check(sha256(file.readBytes()) == sha256(bytes))
    }
    private fun commit(dir: File, bytes: ByteArray, metadata: JsonObject, battery: Boolean) = synchronized(commitLock) {
        val old = manifest(dir)
        // Never promote a corrupted generation into the backup slot.
        val previous = old?.let { m ->
            listOfNotNull(m.get("current")?.asString, m.get("previous")?.takeUnless { it.isJsonNull }?.asString)
                .firstOrNull { name -> runCatching { readGeneration(dir,name,battery) }.isSuccess }
                ?: if(battery) throw PersistenceException(PersistenceError.SAVE_RAM_READ_FAILED)
                    else m.get("current").asString.also { require(uuid.matches(it)) }
        }
        val name = UUID.randomUUID().toString()
        val gen = generation(dir,name); check(gen.mkdir())
        durable(File(gen,if(battery) "battery.sav" else "game.state"),bytes)
        durable(File(gen,"metadata.json"),metadata.toString().toByteArray(Charsets.UTF_8))
        syncDirectory(gen)
        val next = JsonObject().apply {
            addProperty("schemaVersion",1); addProperty("current",name)
            previous?.let { addProperty("previous",it) }
        }
        val temp = File(dir,"current.json.tmp.${UUID.randomUUID()}")
        try {
            durable(temp,next.toString().toByteArray(Charsets.UTF_8)); fault("before-replace")
            Files.move(temp.toPath(),File(dir,"current.json").toPath(),ATOMIC_MOVE,REPLACE_EXISTING)
            syncDirectory(dir)
        } finally { temp.delete() }
        // Garbage collection follows a successful commit; referenced generations always survive.
        dir.listFiles()?.filter { it.isDirectory && uuid.matches(it.name) && it.name != name && it.name != previous }
            ?.filter { candidate -> runCatching {
                val (data,meta)=readGeneration(dir,candidate.name,battery)
                if(!battery) validate(GameId(dir.parentFile.name),dir.name,data,meta)
            }.isSuccess }?.forEach { it.deleteRecursively() }
    }
    private fun readGeneration(dir: File, name: String, battery: Boolean): Pair<ByteArray,JsonObject> {
        val gen = generation(dir,name)
        val meta = json(File(gen,"metadata.json"))
        val file = File(gen,if(battery) "battery.sav" else "game.state")
        require(file.length() in 1..(if(battery) 131072L else 2097152L))
        val bytes = file.readBytes()
        require(meta.get("stateSize").asInt == bytes.size && meta.get("stateSha256").asString == sha256(bytes))
        return bytes to meta
    }
    @Synchronized fun readBattery(id: GameId): ByteArray? {
        try {
            val dir=directory(id,"battery"); val m=manifest(dir) ?: return null
            for ((i,name) in listOfNotNull(m.get("current")?.asString,m.get("previous")?.asString).withIndex()) {
                val data=runCatching { readGeneration(dir,name,true) }.getOrNull() ?: continue
                require(data.second.get("gameId").asString == id.value)
                if(i>0) recoveryNotice="正常存档已从备份恢复；原损坏文件已保留。"
                return data.first
            }
            throw PersistenceException(PersistenceError.SAVE_RAM_READ_FAILED)
        } catch(e: PersistenceException) { throw e }
        catch(e: Exception) { throw PersistenceException(PersistenceError.SAVE_RAM_READ_FAILED,e) }
    }
    @Synchronized fun writeBattery(id: GameId, bytes: ByteArray) {
        try {
            require(bytes.size in 1..131072)
            // Skip identical saves to avoid unnecessary fsync and backup rotation.
            if(readBattery(id)?.contentEquals(bytes)==true) return
            val meta=JsonObject().apply {
                addProperty("schemaVersion",1); addProperty("gameId",id.value)
                addProperty("stateSize",bytes.size); addProperty("stateSha256",sha256(bytes))
            }
            commit(directory(id,"battery"),bytes,meta,true)
        } catch(e: Exception) { throw PersistenceException(PersistenceError.SAVE_RAM_WRITE_FAILED,e) }
    }
    private fun metadata(state: StateMetadata): JsonObject = JsonObject().apply {
        addProperty("schemaVersion",state.schemaVersion); addProperty("gameId",state.gameId)
        addProperty("core",state.core); addProperty("coreVersion",state.coreVersion); addProperty("coreCommit",state.coreCommit)
        addProperty("stateVersion",state.stateVersion); addProperty("createdAt",state.createdAt); addProperty("appVersion",state.appVersion)
        addProperty("slot",state.slot); addProperty("kind",state.kind); addProperty("stateSize",state.stateSize)
        addProperty("stateSha256",state.stateSha256); addProperty("sequence",state.sequence)
    }
    @Synchronized fun writeState(id: GameId,key: String,state: StoredState) {
        try {
            require(state.bytes.size in 1..2097152)
            validate(id,key,state.bytes,metadata(state.metadata))
            commit(directory(id,key),state.bytes,metadata(state.metadata),false)
        } catch(e: Exception) { throw PersistenceException(PersistenceError.STATE_WRITE_FAILED,e) }
    }
    private fun validate(id: GameId,key: String,bytes: ByteArray,m: JsonObject): StateMetadata {
        if(m.get("gameId").asString != id.value) throw PersistenceException(PersistenceError.STATE_ROM_MISMATCH)
        val expected=StateMetadata(gameId=id.value,createdAt=Instant.now().toString(),slot=0,kind="quick",stateSize=0,stateSha256="")
        if(m.get("core").asString != expected.core || m.get("coreVersion").asString != expected.coreVersion ||
            m.get("coreCommit").asString != expected.coreCommit || m.get("stateVersion").asString != "7")
            throw PersistenceException(PersistenceError.STATE_INCOMPATIBLE)
        val slot=m.get("slot").asInt; val kind=m.get("kind").asString
        require((key=="slot-$slot" && slot in 1..4 && kind=="manual") ||
            (slot==0 && ((key=="quick" && kind=="quick") || (key.startsWith("auto-") && kind=="auto"))))
        require(m.get("stateSize").asInt==bytes.size && m.get("stateSha256").asString==sha256(bytes))
        val time=m.get("createdAt").asString; Instant.parse(time)
        val sequence=m.get("sequence").asLong; require(sequence>=0)
        if(kind=="auto") require(sequence>0 && key=="auto-${"abc"[((sequence-1)%3).toInt()]}")
        val app=m.get("appVersion").asString; require(app.length in 1..64)
        return StateMetadata(gameId=id.value,createdAt=time,slot=slot,kind=kind,stateSize=bytes.size,
            stateSha256=sha256(bytes),sequence=sequence,appVersion=app)
    }
    @Synchronized fun readState(id: GameId,key: String): StoredState? {
        try {
            val dir=directory(id,key); val m=manifest(dir) ?: return null
            // Manual/quick load reports corruption instead of silently picking an older overwrite.
            val (bytes,meta)=readGeneration(dir,m.get("current").asString,false)
            return StoredState(bytes,validate(id,key,bytes,meta))
        } catch(e: PersistenceException) { throw e }
        catch(e: Exception) { throw PersistenceException(PersistenceError.STATE_CORRUPTED,e) }
    }
    @Synchronized fun listStates(id: GameId): List<StateMetadata> =
        (listOf("quick","auto-a","auto-b","auto-c")+(1..4).map { "slot-$it" })
            .mapNotNull { runCatching { readState(id,it)?.metadata }.getOrNull() }
    fun cleanupTemps() {
        root.walkTopDown().filter { it.isFile && it.name.matches(Regex("current\\.json\\.tmp\\.[0-9a-f-]+")) }
            .forEach { it.delete() }
    }
}
