package dev.gbalite.input
import dev.gbalite.core.*
import org.junit.Assert.*
import org.junit.Test
class SensorMapperTest {
    @Test fun axesCalibrationAndFiniteBounds() {
        assertEquals(-2f to 1f,SensorMapper.rotate(1f,2f,1))
        assertEquals(-1f to -2f,SensorMapper.rotate(1f,2f,2))
        assertEquals(2f to -1f,SensorMapper.rotate(1f,2f,3))
        val m=SensorMapper()
        assertEquals(0 to 0,m.tilt(2f,3f,0,2f,3f,1f))
        assertEquals(0 to 0,m.tilt(Float.NaN,Float.POSITIVE_INFINITY,0,alpha=1f))
        val xy=m.tilt(10000f,-10000f,0,alpha=1f)
        assertTrue(xy.first<0 && xy.second<0)
        assertEquals(0,m.gyro(Float.NaN)); assertTrue(m.gyro(10000f)<0)
        m.reset(); assertEquals(0,m.gyro(0f));assertEquals(0 to 0,m.tilt(0f,0f,0))
    }
    @Test fun solarIsMonotonicAndBounded() {
        var previous=256
        for(p in -10..110) { val v=SensorMapper.luminance(p);assertTrue(v in 50..233);assertTrue(v<=previous);previous=v }
        assertEquals(0,SensorMapper.luxPercent(Float.NaN))
        assertEquals(0,SensorMapper.luxPercent(-1f))
        assertEquals(100,SensorMapper.luxPercent(100000f))
        assertTrue(SensorMapper.luxPercent(100f)>SensorMapper.luxPercent(10f))
    }
    @Test fun settingsVersionAndCorruption() {
        val s=PeripheralSettings(PeripheralMode.MANUAL,PeripheralMode.DISABLED,PeripheralMode.MANUAL,100,false,2f,-2f,.2f)
        assertEquals(s,PeripheralSettings.decode(s.encode()))
        listOf("","2|x","1|AUTO|AUTO|AUTO|101|true|0|0|0","1|AUTO|AUTO|AUTO|50|true|NaN|0|0").forEach {
            assertEquals(PeripheralSettings(),PeripheralSettings.decode(it))
        }
    }
}
