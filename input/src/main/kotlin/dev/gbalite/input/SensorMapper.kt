package dev.gbalite.input

import dev.gbalite.core.PeripheralSample
import kotlin.math.*

/** Pure mapping; no Android context, timestamps, native handles or persistent history. */
class SensorMapper {
    private var x=0f; private var y=0f; private var z=0f
    var rawX=0f; private set
    var rawY=0f; private set
    var rawZ=0f; private set
    fun reset() { x=0f;y=0f;z=0f;rawX=0f;rawY=0f;rawZ=0f }
    fun tilt(a: Float,b: Float,rotation: Int,zeroX: Float=0f,zeroY: Float=0f,alpha: Float=.25f): Pair<Int,Int> {
        rawX=safe(a,20f);rawY=safe(b,20f)
        val (u,v)=rotate(rawX-zeroX,rawY-zeroY,rotation)
        x+=alpha.coerceIn(0f,1f)*(u.coerceIn(-9.81f,9.81f)-x)
        y+=alpha.coerceIn(0f,1f)*(v.coerceIn(-9.81f,9.81f)-y)
        return scaled(dead(x,.15f),-2e8) to scaled(dead(y,.15f),2e8)
    }
    fun gyro(rate: Float,zero: Float=0f): Int {
        rawZ=safe(rate,4f)
        z+=.4f*((rawZ-zero).coerceIn(-3f,3f)-z)
        return scaled(dead(z,.025f),-5.5e8)
    }
    companion object {
        fun safe(v: Float,limit: Float): Float=if(v.isFinite()) v.coerceIn(-limit,limit) else 0f
        fun rotate(x: Float,y: Float,rotation: Int): Pair<Float,Float> = when(rotation) {
            1 -> -y to x; 2 -> -x to -y; 3 -> y to -x; else -> x to y
        }
        private fun dead(v: Float,zone: Float)=if(abs(v)<zone) 0f else v
        private fun scaled(v: Float,scale: Double)=(v*scale).coerceIn(Int.MIN_VALUE.toDouble(),Int.MAX_VALUE.toDouble()).toInt()
        fun luxPercent(lux: Float): Int=if(!lux.isFinite() || lux<=0) 0 else
            (ln(1+lux.coerceAtMost(100000f).toDouble())/ln(10001.0)*100).roundToInt().coerceIn(0,100)
        fun luminance(percent: Int): Int {
            val levels=intArrayOf(0,5,11,18,27,42,62,84,109,139,183)
            return 255-22-levels[(percent.coerceIn(0,100)+5)/10]
        }
        fun manual(x: Float,y: Float,gyro: Float=0f)=PeripheralSample(
            scaled(safe(x,1f)*9.81f,-2e8),scaled(safe(y,1f)*9.81f,2e8),scaled(safe(gyro,1f)*3f,-5.5e8))
    }
}
