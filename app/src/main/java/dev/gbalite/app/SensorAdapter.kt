package dev.gbalite.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import dev.gbalite.core.*
import dev.gbalite.input.SensorMapper

/** Application context only. Listener callbacks and snapshot() run on main. */
class SensorAdapter(context: Context): SensorEventListener {
    private val manager=context.applicationContext.getSystemService(SensorManager::class.java)
    private val gravity=manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro=manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val light=manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val mapper=SensorMapper()
    private var settings=PeripheralSettings()
    private var activeMask=0
    private var failedMask=0
    private var rotation=0
    private var x=0;private var y=0;private var z=0;private var lux=0
    private var tiltAt=0L; private var gyroAt=0L; private var lightAt=0L
    private var manualX=0f;private var manualY=0f;private var manualZ=0f
    private var calibrationX=0f;private var calibrationY=0f;private var calibrationZ=0f
    val hasTilt get()=gravity!=null && failedMask and 16==0
    val hasGyro get()=gyro!=null && failedMask and 8==0
    val hasLight get()=light!=null && failedMask and 4==0
    var listeners=0; private set
    fun configure(value: PeripheralSettings) { if(settings!=value) { stop(); settings=value } }
    fun orientation(value: Int) { if(value!=rotation) { rotation=value; stop();calibrationX=0f;calibrationY=0f;calibrationZ=0f } }
    fun stop(retryAvailability: Boolean=true) {
        manager?.unregisterListener(this); activeMask=0;listeners=0
        if(retryAvailability) failedMask=0
        mapper.reset();x=0;y=0;z=0;lux=0;tiltAt=0;gyroAt=0;lightAt=0
        manualX=0f;manualY=0f;manualZ=0f
    }
    fun activate(detected: Int,running: Boolean) {
        val needed=if(!running) 0 else
            (if(settings.tilt==PeripheralMode.AUTO && detected and 16!=0 && hasTilt) 16 else 0) or
            (if(settings.gyro==PeripheralMode.AUTO && detected and 8!=0 && hasGyro) 8 else 0) or
            (if(settings.solar==PeripheralMode.AUTO && detected and 4!=0 && hasLight) 4 else 0)
        if(needed==activeMask) return
        stop(false)
        fun register(sensor: Sensor?,flag: Int) {
            if(needed and flag!=0 && sensor!=null) {
                val registered=try {manager?.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME)==true}
                    catch(_: SecurityException) {false}
                if(registered) {activeMask=activeMask or flag;listeners++}
                else failedMask=failedMask or flag
            }
        }
        register(gravity,16);register(gyro,8);register(light,4)
    }
    override fun onSensorChanged(e: SensorEvent) {
        if(e.timestamp<SystemClock.elapsedRealtimeNanos()-500_000_000L) return
        when(e.sensor.type) {
            Sensor.TYPE_GRAVITY,Sensor.TYPE_ACCELEROMETER -> if(activeMask and 16!=0) {
                val p=mapper.tilt(e.values[0],e.values[1],rotation,settings.tiltZeroX,settings.tiltZeroY,
                    if(e.sensor.type==Sensor.TYPE_GRAVITY) .25f else .08f)
                x=p.first;y=p.second;tiltAt=SystemClock.elapsedRealtime()
                calibrationX=mapper.rawX;calibrationY=mapper.rawY
            }
            Sensor.TYPE_GYROSCOPE -> if(activeMask and 8!=0) {
                z=mapper.gyro(e.values[2],settings.gyroZero);gyroAt=SystemClock.elapsedRealtime()
                calibrationZ=mapper.rawZ
            }
            Sensor.TYPE_LIGHT -> if(activeMask and 4!=0) {
                lux=SensorMapper.luxPercent(e.values[0]);lightAt=SystemClock.elapsedRealtime()
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?,accuracy: Int) {}
    fun manual(x: Float,y: Float,z: Float) { manualX=x;manualY=y;manualZ=z }
    fun calibration(): PeripheralSettings=settings.copy(tiltZeroX=calibrationX,tiltZeroY=calibrationY,gyroZero=calibrationZ)
    fun snapshot(): PeripheralSample {
        val now=SystemClock.elapsedRealtime()
        val manual=SensorMapper.manual(manualX,manualY,manualZ)
        val tiltManual=settings.tilt==PeripheralMode.MANUAL || (settings.tilt==PeripheralMode.AUTO && !hasTilt)
        val gyroManual=settings.gyro==PeripheralMode.MANUAL || (settings.gyro==PeripheralMode.AUTO && !hasGyro)
        val sunlight=when(settings.solar) {
            PeripheralMode.DISABLED -> 0
            PeripheralMode.MANUAL -> settings.sunlight
            PeripheralMode.AUTO -> if(!hasLight) settings.sunlight else if(lightAt>0) lux else 0
        }
        return PeripheralSample(
            if(settings.tilt==PeripheralMode.DISABLED) 0 else if(tiltManual) manual.tiltX else if(tiltAt>0 && now-tiltAt<500) x else 0,
            if(settings.tilt==PeripheralMode.DISABLED) 0 else if(tiltManual) manual.tiltY else if(tiltAt>0 && now-tiltAt<500) y else 0,
            if(settings.gyro==PeripheralMode.DISABLED) 0 else if(gyroManual) manual.gyroZ else if(gyroAt>0 && now-gyroAt<500) z else 0,
            SensorMapper.luminance(sunlight))
    }
}
