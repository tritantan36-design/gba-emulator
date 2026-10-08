package dev.gbalite.mgba

import dev.gbalite.core.*
import java.nio.ByteBuffer

internal interface NativeBridge {
    fun create(): Long
    fun destroy(handle: Long)
    fun loadRom(handle: Long, fd: Int, length: Long): Int
    fun start(handle: Long): Boolean
    fun pause(handle: Long): Boolean
    fun reset(handle: Long): Boolean
    fun setButton(handle: Long, mask: Int, down: Boolean)
    fun copyFrame(handle: Long, target: ByteBuffer): Boolean
    fun exportBytes(handle: Long, state: Boolean): ByteArray? = null
    fun importBytes(handle: Long, bytes: ByteArray, state: Boolean): Boolean = false
    fun control(handle: Long, command: Int, value: Int): Boolean = false
    fun metrics(handle: Long): LongArray? = null
    fun updatePeripherals(handle: Long,x: Int,y: Int,z: Int,light: Int) {}
    fun configurePeripherals(handle: Long,mask: Int) {}
    fun peripheralStatus(handle: Long): LongArray? = null
}

class MgbaCoreAdapter internal constructor(private val bridge: NativeBridge) : EmulatorCore {
    constructor() : this(JniBridge())
    private enum class State { EMPTY, LOADED, RUNNING, PAUSED, STOPPED, CLOSED }
    private var state = State.EMPTY
    private var handle = bridge.create().also { check(it != 0L) { "Core creation failed" } }
    override val capabilities = EmulatorCapabilities()
    override val frames = FrameSource { target ->
        synchronized(this) {
            if (state == State.CLOSED || state == State.EMPTY) false
            else bridge.copyFrame(handle, target)
        }
    }
    @Synchronized override fun loadGame(source: GameSource): LoadResult {
        check(state == State.EMPTY) { "Create a new session before loading another ROM" }
        val fd = source as GameSource.FileDescriptorSource
        if (fd.displayName?.endsWith(".gba", ignoreCase = true) != true)
            return LoadResult.Unsupported("Only .gba is supported")
        if (fd.fd < 0) return LoadResult.IoError()
        if (fd.length != null && fd.length !in 256L..33554432L)
            return LoadResult.Corrupted("Invalid ROM length")
        return when (val code = bridge.loadRom(handle, fd.fd, fd.length ?: -1)) {
            0 -> { state = State.LOADED; LoadResult.Success }
            1 -> LoadResult.IoError()
            2 -> LoadResult.Corrupted("Invalid GBA ROM")
            else -> LoadResult.NativeError(code)
        }
    }
    @Synchronized override fun start() {
        check(state in setOf(State.LOADED, State.PAUSED, State.STOPPED))
        check(bridge.start(handle)) { "Audio/core start failed" }; state = State.RUNNING
    }
    @Synchronized override fun pause() {
        if (state == State.RUNNING) { check(bridge.pause(handle)); state = State.PAUSED }
    }
    @Synchronized override fun resume() {
        if (state in setOf(State.PAUSED, State.LOADED, State.STOPPED)) start()
    }
    @Synchronized override fun reset() {
        check(state in setOf(State.LOADED, State.RUNNING, State.PAUSED, State.STOPPED))
        check(bridge.reset(handle))
    }
    @Synchronized override fun stop() {
        if (state in setOf(State.RUNNING, State.PAUSED, State.LOADED)) {
            check(bridge.pause(handle)); state = State.STOPPED
        }
    }
    @Synchronized override fun setButton(button: GbaButton, pressed: Boolean) {
        if (state == State.RUNNING) bridge.setButton(handle, button.mask, pressed)
    }
    @Synchronized override fun close() {
        if (state != State.CLOSED) {
            bridge.destroy(handle); handle = 0; state = State.CLOSED
        }
    }
    @Synchronized override fun exportSaveRam(): BytesResult = export(false)
    @Synchronized override fun setSpeed(multiplier: Int): Boolean {
        require(multiplier in listOf(1,2,4,8))
        return state != State.CLOSED && bridge.control(handle,0,multiplier)
    }
    @Synchronized override fun setRewinding(active: Boolean): Boolean =
        state != State.CLOSED && bridge.control(handle,1,if(active) 1 else 0)
    @Synchronized override fun trimRewind() { if(state != State.CLOSED) bridge.control(handle,2,0) }
    @Synchronized override fun playerMetrics(): PlayerMetrics {
        val m=if(state != State.CLOSED) bridge.metrics(handle) else null
        return if(m?.size == 8) PlayerMetrics(m[0],m[1],m[2],m[3],m[4],m[5],m[6].toInt(),m[7]!=0L) else PlayerMetrics()
    }
    @Synchronized override fun exportState(): BytesResult = export(true)
    @Synchronized override fun updatePeripherals(sample: PeripheralSample) {
        if(state==State.RUNNING) bridge.updatePeripherals(handle,sample.tiltX,sample.tiltY,sample.gyroZ,sample.luminance)
    }
    @Synchronized override fun configurePeripherals(manualMask: Int) {
        if(state !in setOf(State.EMPTY,State.CLOSED)) bridge.configurePeripherals(handle,manualMask and 31)
    }
    @Synchronized override fun peripheralStatus(): PeripheralStatus {
        val s=if(state!=State.CLOSED) bridge.peripheralStatus(handle) else null
        return if(s?.size==3) PeripheralStatus(s[0].toInt(),s[1]!=0L,s[2]) else PeripheralStatus()
    }
    private fun export(isState: Boolean): BytesResult {
        val error = if (isState) PersistenceError.STATE_WRITE_FAILED else PersistenceError.SAVE_RAM_WRITE_FAILED
        if (state !in setOf(State.LOADED, State.PAUSED, State.STOPPED)) return BytesResult.Failure(error)
        return try {
            val bytes = bridge.exportBytes(handle, isState) ?: return BytesResult.Failure(error)
            if (bytes.isEmpty() && !isState) BytesResult.NoSave else BytesResult.Success(bytes)
        } catch (_: Exception) { BytesResult.Failure(error) }
    }
    @Synchronized override fun importSaveRam(bytes: ByteArray): Boolean = import(bytes, false)
    @Synchronized override fun importState(bytes: ByteArray): Boolean = import(bytes, true)
    private fun import(bytes: ByteArray, isState: Boolean): Boolean {
        if (state !in setOf(State.LOADED, State.PAUSED, State.STOPPED)) return false
        return bridge.importBytes(handle, bytes, isState)
    }
}
