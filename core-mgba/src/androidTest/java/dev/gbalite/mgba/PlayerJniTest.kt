package dev.gbalite.mgba
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import dev.gbalite.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class PlayerJniTest {
    private fun loaded(suffix: Boolean=false): MgbaCoreAdapter {
        val i=InstrumentationRegistry.getInstrumentation(); val f=File(i.targetContext.cacheDir,"player.gba")
        i.context.assets.open("persistence.gba").use { input->f.outputStream().use {
            input.copyTo(it); if(suffix) it.write("PHASE3_TEST_ONLY".toByteArray())
        } }
        return MgbaCoreAdapter().also { c->ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY).use {
            assertEquals(LoadResult.Success,c.loadGame(GameSource.FileDescriptorSource(it.fd,f.length(),f.name)))
        } }
    }
    @Test fun trailingLiteralLikeBytesCannotExpandRom() {
        loaded(true).use { c-> c.start();Thread.sleep(100);c.pause()
            assertTrue(c.playerMetrics().frames>0);assertTrue(c.exportSaveRam() is BytesResult.Success)
        }
    }
    @Test fun pacingActualAndReturnToNormal() {
        val log=StringBuilder()
        val evidence=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "phase7-pacing-${System.currentTimeMillis()}.txt")
        loaded().use { c->
            c.start(); Thread.sleep(200)
            for(speed in listOf(1,2,1,4,1,8,1)) {
                assertTrue(c.setSpeed(speed)); val start=c.playerMetrics().frames; val time=System.nanoTime()
                Thread.sleep(2000)
                val fps=(c.playerMetrics().frames-start)*1e9/(System.nanoTime()-time)
                log.append("request=$speed fps=$fps actual=${fps/59.7275} metrics=${c.playerMetrics()}\n")
                evidence.writeText(log.toString()) // Retain measured steps even when an assertion fails.
                assertTrue("1x/2x/4x must maintain pacing: $fps",fps>59.7275*minOf(speed,4)*.90)
                assertTrue("Pacing must not run uncontrolled: $fps",fps<59.7275*speed*1.10)
            }
            assertEquals(1,c.playerMetrics().speed); c.pause(); assertEquals(1,c.playerMetrics().speed)
        }
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"phase3-metrics.txt").writeText(log.toString())
    }
    @Test fun rewindFiveSecondsPreservesBatteryAndCloseOwnsEverything() {
        loaded().use { c->
            c.start(); c.setButton(GbaButton.START,true); Thread.sleep(6500); c.setButton(GbaButton.START,false)
            c.pause(); val saved=(c.exportSaveRam() as BytesResult.Success).bytes
            val before=(c.exportState() as BytesResult.Success).bytes
            c.resume(); val snapshots=c.playerMetrics().rewindSnapshots
            assertTrue(snapshots>=30)
            assertTrue(c.setRewinding(true)); val rewindStart=System.nanoTime()
            val deadline=System.currentTimeMillis()+5000
            while(snapshots-c.playerMetrics().rewindSnapshots<25 && System.currentTimeMillis()<deadline) Thread.sleep(50)
            val metrics=c.playerMetrics(); assertTrue("before=$snapshots after=$metrics",snapshots-metrics.rewindSnapshots>=25)
            File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"phase3-rewind-metrics.txt")
                .writeText("snapshots=${snapshots-metrics.rewindSnapshots} elapsedMs=${(System.nanoTime()-rewindStart)/1000000} metrics=$metrics")
            assertTrue(c.setRewinding(false)); c.pause()
            assertArrayEquals(saved,(c.exportSaveRam() as BytesResult.Success).bytes)
            assertFalse(before.contentEquals((c.exportState() as BytesResult.Success).bytes))
            c.resume(); Thread.sleep(100); c.pause(); assertArrayEquals(saved,(c.exportSaveRam() as BytesResult.Success).bytes)
            c.trimRewind(); assertEquals(0,c.playerMetrics().rewindBytes)
        }
        repeat(5) { loaded().use { c-> c.start(); c.setSpeed(8); Thread.sleep(300); c.setRewinding(true) } }
        assertEquals(0,JniBridge().activeHandlesForTest())
    }
    @Test fun ringCapAndPauseDuringFastForwardOrRewind() {
        loaded().use { c->
            c.start(); c.setSpeed(8); Thread.sleep(4500)
            val metrics=c.playerMetrics()
            assertTrue(metrics.rewindSnapshots>=75); assertTrue(metrics.rewindBytes<=64L*1024*1024)
            assertTrue(metrics.rewindSnapshots<=150)
            c.pause(); assertEquals(1,c.playerMetrics().speed); assertFalse(c.playerMetrics().rewinding)
            c.resume(); c.setRewinding(true); Thread.sleep(150); c.pause()
            assertFalse(c.playerMetrics().rewinding); c.resume(); Thread.sleep(100)
        }
    }
}
