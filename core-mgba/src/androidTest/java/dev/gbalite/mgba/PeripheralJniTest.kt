package dev.gbalite.mgba
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PeripheralJniTest {
    private fun probe(name: String,body: (JniBridge,Long)->Unit) {
        val i=InstrumentationRegistry.getInstrumentation();val f=File(i.targetContext.cacheDir,"phase5-$name.gba")
        i.context.assets.open("$name.gba").use { input->f.outputStream().use { input.copyTo(it) } }
        val b=JniBridge();val h=b.create()
        try {
            ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY).use { assertEquals(0,b.loadRom(h,it.fd,f.length())) }
            assertTrue(b.start(h));await { b.probeReadForTest(h,0)==0x50423547 };body(b,h)
        } finally { b.destroy(h);f.delete() }
        b.updatePeripherals(h,Int.MAX_VALUE,Int.MIN_VALUE,0,0);assertNull(b.peripheralStatus(h))
    }
    private fun await(condition: ()->Boolean) {
        val limit=System.currentTimeMillis()+5000
        while(!condition() && System.currentTimeMillis()<limit) Thread.sleep(20)
        assertTrue(condition())
    }
    @Test fun tiltGyroAndSolarReachActualRom() {
        probe("tilt-probe") { b,h ->
            assertTrue(b.peripheralStatus(h)!![0].toInt() and 16!=0)
            b.updatePeripherals(h,1073741824,-1073741824,0,233)
            await { b.probeReadForTest(h,2)==0x2a0 && b.probeReadForTest(h,3)==0x4a0 }
            assertTrue(b.pause(h));assertTrue(b.start(h));await { b.probeReadForTest(h,2)==0x3a0 }
        }
        probe("rotation-probe") { b,h ->
            assertTrue(b.peripheralStatus(h)!![0].toInt() and 8!=0)
            b.updatePeripherals(h,0,0,1073741824,233);await { b.probeReadForTest(h,4)==0x900 }
            assertTrue(b.control(h,1,1));b.updatePeripherals(h,0,0,1073741824,233)
            assertTrue(b.control(h,1,0));await { b.probeReadForTest(h,4)==0x700 }
        }
        probe("solar-probe") { b,h ->
            b.updatePeripherals(h,0,0,0,50);await { b.probeReadForTest(h,2)==50 }
            b.updatePeripherals(h,0,0,0,233);await { b.probeReadForTest(h,2)==233 }
        }
    }
    @Test fun rtcUsesWallClockAcrossFastForwardAndState() {
        probe("rtc-probe") { b,h ->
            fun checkClock() {
                val now=java.time.ZonedDateTime.now()
                fun bcd(v: Int)=(v/10)*16+v%10
                assertEquals(bcd(now.monthValue),b.probeReadForTest(h,3))
                assertEquals(bcd(now.dayOfMonth),b.probeReadForTest(h,4))
                val sec=b.probeReadForTest(h,8);val decoded=(sec shr 4)*10+(sec and 15)
                assertTrue(kotlin.math.abs(decoded-now.second)<=2 || kotlin.math.abs(decoded-now.second)>=58)
            }
            for(speed in listOf(1,2,4,8)) { assertTrue(b.control(h,0,speed));Thread.sleep(250);checkClock() }
            assertTrue(b.pause(h));val state=b.exportBytes(h,true)!!
            Thread.sleep(1200);assertTrue(b.importBytes(h,state,true));assertTrue(b.start(h));Thread.sleep(100);checkClock()
        }
    }
    @Test fun cartridgeRumbleMailboxStopsWithoutPermission() {
        probe("rumble-probe") { b,h ->
            b.setButton(h,1,true);await { b.peripheralStatus(h)!![1]==1L }
            b.setButton(h,1,false);await { b.peripheralStatus(h)!![1]==0L }
            b.setButton(h,1,true);await { b.peripheralStatus(h)!![1]==1L }
            val before=b.peripheralStatus(h)!![2];assertTrue(b.pause(h))
            assertEquals(0L,b.peripheralStatus(h)!![1]);assertTrue(b.peripheralStatus(h)!![2]>before)
        }
    }
    @Test fun concurrentScalarUpdatesAndCloseCannotUseStaleCore() {
        probe("tilt-probe") { b,h ->
            val threads=List(3) { n->Thread { repeat(100) { b.updatePeripherals(h,n*50000000,it*10000,0,100) } }.apply { start() } }
            Thread.sleep(5);b.destroy(h);threads.forEach { it.join() };assertNull(b.peripheralStatus(h))
        }
    }
}
