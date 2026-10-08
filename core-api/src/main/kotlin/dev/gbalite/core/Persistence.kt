package dev.gbalite.core

import java.security.MessageDigest

@JvmInline value class GameId(val value: String) {
    init { require(value.matches(Regex("[0-9a-f]{64}"))) }
    companion object {
        fun fromBytes(bytes: ByteArray) = GameId(sha256(bytes))
    }
}
fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
enum class PersistenceError {
    SAVE_RAM_WRITE_FAILED, SAVE_RAM_READ_FAILED, STATE_WRITE_FAILED, STATE_READ_FAILED,
    STATE_INCOMPATIBLE, STATE_ROM_MISMATCH, STATE_CORRUPTED, AUTOSAVE_FAILED,
    ROM_URI_REVOKED, STORAGE_IO_ERROR
}
class PersistenceException(val code: PersistenceError, cause: Throwable? = null) : Exception(code.name, cause)
sealed interface BytesResult {
    data class Success(val bytes: ByteArray) : BytesResult
    data object NoSave : BytesResult
    data class Failure(val code: PersistenceError) : BytesResult
}
data class GameRecord(val gameId: GameId, val displayName: String, val romUri: String,
    val sourceSize: Long = -1, val sourceModified: Long = -1)
data class StateMetadata(
    val schemaVersion: Int = 1, val gameId: String, val core: String = "mgba",
    val coreVersion: String = "0.10.5", val coreCommit: String = "26b7884bc25a5933960f3cdcd98bac1ae14d42e2",
    val stateVersion: String = "7", val createdAt: String, val appVersion: String = "0.7.0",
    val slot: Int, val kind: String, val stateSize: Int, val stateSha256: String, val sequence: Long = 0
)
data class StoredState(val bytes: ByteArray, val metadata: StateMetadata)
/** Implementations never expose their paths to native code. All calls run on Session's IO dispatcher. */
interface PersistenceStore {
    fun readBattery(id: GameId): ByteArray?
    fun writeBattery(id: GameId, bytes: ByteArray)
    fun writeState(id: GameId, key: String, state: StoredState)
    fun readState(id: GameId, key: String): StoredState?
    fun listStates(id: GameId): List<StateMetadata>
    fun markSession(game: GameRecord, clean: Boolean)
    fun addPlayTime(id: GameId,deltaMs: Long) {}
    fun played(id: GameId) {}
}
/** Optional screenshot hook; failure must never invalidate a committed state. */
fun interface StateScreenshotWriter { fun write(id: GameId, key: String, frames: FrameSource, createdAt: String) }
