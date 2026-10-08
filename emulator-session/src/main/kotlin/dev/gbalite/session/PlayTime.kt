package dev.gbalite.session

/** Called only under Session's mutex. Failed persistence leaves pending time intact. */
class PlayTime(private val clock: ()->Long={System.nanoTime()/1_000_000}) {
    private var since: Long?=null
    private var pending=0L
    fun running(value: Boolean) {
        val now=clock();since?.let {pending+=maxOf(0,now-it)}
        since=if(value) now else null
    }
    fun flush(write: (Long)->Unit) {
        running(since!=null)
        if(pending>0) {write(pending);pending=0}
    }
    fun reset() {since=null;pending=0}
}
