package dev.gbalite.core
import org.junit.Test
import org.junit.Assert.*
class LibraryTest {
    @Test fun searchSortAndIdentityStayIndependent() {
        val a=LibraryGame(GameRecord(GameId.fromBytes(byteArrayOf(1)),"Alpha","content://a"),"2026-02","2026-03",100,false)
        val b=LibraryGame(GameRecord(GameId.fromBytes(byteArrayOf(2)),"beta","content://b"),"2026-03","2026-02",200,false)
        assertEquals(listOf(a),librarySelection(listOf(a,b)," ALP ",LibrarySort.RECENT))
        assertEquals(listOf(a,b),librarySelection(listOf(b,a),"",LibrarySort.NAME))
        for(sort in listOf(LibrarySort.ADDED,LibrarySort.PLAY_TIME)) assertEquals(listOf(b,a),librarySelection(listOf(a,b),"",sort))
        assertEquals(a.record.gameId,a.copy(record=a.record.copy(displayName="Renamed",romUri="content://new")).record.gameId)
    }
}
