package dev.gbalite.core

enum class PeripheralMode { AUTO, MANUAL, DISABLED }
/** Values in the pinned callback's fixed-point scale, not Android SensorEvent objects. */
data class PeripheralSample(val tiltX: Int=0, val tiltY: Int=0, val gyroZ: Int=0, val luminance: Int=233)
data class PeripheralStatus(val detected: Int=0, val rumble: Boolean=false, val stopEpoch: Long=0)
data class PeripheralSettings(
    val tilt: PeripheralMode=PeripheralMode.AUTO, val gyro: PeripheralMode=PeripheralMode.AUTO,
    val solar: PeripheralMode=PeripheralMode.AUTO, val sunlight: Int=50, val rumble: Boolean=true,
    val tiltZeroX: Float=0f, val tiltZeroY: Float=0f, val gyroZero: Float=0f
) {
    fun encode(): String="1|${tilt.name}|${gyro.name}|${solar.name}|$sunlight|$rumble|$tiltZeroX|$tiltZeroY|$gyroZero"
    companion object {
        fun decode(text: String): PeripheralSettings = runCatching {
            require(text.length<=256)
            val p=text.trim().split('|'); require(p.size==9 && p[0]=="1")
            val x=p[6].toFloat(); val y=p[7].toFloat(); val z=p[8].toFloat()
            require(x.isFinite() && y.isFinite() && z.isFinite() && kotlin.math.abs(x)<=20 && kotlin.math.abs(y)<=20 && kotlin.math.abs(z)<=4)
            PeripheralSettings(PeripheralMode.valueOf(p[1]),PeripheralMode.valueOf(p[2]),PeripheralMode.valueOf(p[3]),
                p[4].toInt().also { require(it in 0..100) },p[5].toBooleanStrict(),x,y,z)
        }.getOrDefault(PeripheralSettings())
    }
}
/** A controlled application thread invokes this; never a core/audio callback. */
interface HapticOutput {
    val status: String
    fun update(enabled: Boolean)
    fun stop()
}
class DisabledHapticOutput: HapticOutput {
    override val status="BLOCKED_BY_PERMISSION_APPROVAL：保持零权限，真实震动未启用"
    override fun update(enabled: Boolean) {}
    override fun stop() {}
}
