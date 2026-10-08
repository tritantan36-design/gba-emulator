package dev.gbalite.core

import java.nio.ByteBuffer

/** A borrowed descriptor: loadGame must finish reading before the caller closes it. */
sealed interface GameSource {
    data class FileDescriptorSource(val fd: Int, val length: Long?, val displayName: String?) : GameSource
}
sealed interface LoadResult {
    data object Success : LoadResult
    data class Unsupported(val reason: String) : LoadResult
    data class Corrupted(val reason: String) : LoadResult
    data class IoError(val cause: Throwable? = null) : LoadResult
    data class NativeError(val code: Int) : LoadResult
    data class PersistenceFailed(val code: PersistenceError) : LoadResult
}
enum class GbaButton(val mask: Int) {
    A(1), B(2), SELECT(4), START(8), RIGHT(16), LEFT(32), UP(64), DOWN(128), R(256), L(512)
}
data class EmulatorCapabilities(val width: Int = 240, val height: Int = 160, val audio: Boolean = true)
/** Copies RGBA8888 into a caller-owned direct buffer. Safe concurrently with close. */
fun interface FrameSource { fun copyFrame(target: ByteBuffer): Boolean }
interface EmulatorCore : AutoCloseable {
    val capabilities: EmulatorCapabilities
    val frames: FrameSource
    fun loadGame(source: GameSource): LoadResult
    fun start()
    fun pause()
    fun resume()
    fun reset()
    fun stop()
    fun setButton(button: GbaButton, pressed: Boolean)
    fun exportSaveRam(): BytesResult = BytesResult.NoSave
    fun importSaveRam(bytes: ByteArray): Boolean = false
    fun exportState(): BytesResult = BytesResult.Failure(PersistenceError.STATE_WRITE_FAILED)
    fun importState(bytes: ByteArray): Boolean = false
    fun setSpeed(multiplier: Int): Boolean = multiplier == 1
    fun setRewinding(active: Boolean): Boolean = !active
    fun trimRewind() {}
    fun playerMetrics(): PlayerMetrics = PlayerMetrics()
    fun peripheralStatus(): PeripheralStatus = PeripheralStatus()
    fun updatePeripherals(sample: PeripheralSample) {}
    fun configurePeripherals(manualMask: Int) {}
    override fun close()
}
data class PlayerMetrics(val frames: Long=0, val lateFrames: Long=0, val rewindSnapshots: Long=0,
    val rewindBytes: Long=0, val stateBytes: Long=0, val audioUnderruns: Long=0,
    val speed: Int=1, val rewinding: Boolean=false)
