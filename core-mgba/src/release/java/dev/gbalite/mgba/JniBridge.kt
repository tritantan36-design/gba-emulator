package dev.gbalite.mgba

import java.nio.ByteBuffer

// Variant boundary: only Debug declares test probes; native production names stay identical.
internal class JniBridge : NativeBridge {
    companion object { init { System.loadLibrary("gba_bridge") } }
    external override fun create(): Long
    external override fun destroy(handle: Long)
    external override fun loadRom(handle: Long, fd: Int, length: Long): Int
    external override fun start(handle: Long): Boolean
    external override fun pause(handle: Long): Boolean
    external override fun reset(handle: Long): Boolean
    external override fun setButton(handle: Long, mask: Int, down: Boolean)
    external override fun copyFrame(handle: Long, target: ByteBuffer): Boolean
    external override fun exportBytes(handle: Long, state: Boolean): ByteArray?
    external override fun importBytes(handle: Long, bytes: ByteArray, state: Boolean): Boolean
    external override fun control(handle: Long, command: Int, value: Int): Boolean
    external override fun metrics(handle: Long): LongArray?
    external override fun updatePeripherals(handle: Long,x: Int,y: Int,z: Int,light: Int)
    external override fun configurePeripherals(handle: Long,mask: Int)
    external override fun peripheralStatus(handle: Long): LongArray?
}
