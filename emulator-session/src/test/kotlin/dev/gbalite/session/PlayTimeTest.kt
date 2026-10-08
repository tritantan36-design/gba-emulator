package dev.gbalite.session
import org.junit.Assert.*
import org.junit.Test
class PlayTimeTest {
    @Test fun monotonicForegroundPauseBackgroundFfRewindAndRetry() {
        var now=0L;val time=PlayTime {now};var stored=0L
        now=1000;time.running(true);now=3000;time.running(false)
        now=6000;time.flush {stored+=it};assertEquals(2000L,stored)
        time.running(true);now=7000 // FF is still one second of wall time.
        time.running(true);now=8000 // Rewind neither subtracts nor multiplies time.
        try {time.flush {throw java.io.IOException()};fail()} catch(_: java.io.IOException) {}
        now=9000;time.running(false);time.flush {stored+=it};assertEquals(5000L,stored)
        now=15000;time.flush {stored+=it};assertEquals(5000L,stored)
        time.reset();time.running(true);now=16000;time.running(false);time.flush {stored+=it};assertEquals(6000L,stored)
    }
}
