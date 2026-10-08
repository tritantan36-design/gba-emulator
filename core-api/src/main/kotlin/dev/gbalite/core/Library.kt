package dev.gbalite.core

data class LibraryGame(val record: GameRecord,val addedAt: String,val lastPlayedAt: String,
    val playTimeMs: Long,val unavailable: Boolean,val artwork: String?=null)
enum class LibrarySort { RECENT,NAME,ADDED,PLAY_TIME }
fun librarySelection(games: List<LibraryGame>,query: String,sort: LibrarySort): List<LibraryGame> {
    val found=games.filter {it.record.displayName.contains(query.trim(),ignoreCase=true)}
    return when(sort) {
        LibrarySort.RECENT -> found.sortedByDescending {it.lastPlayedAt}
        LibrarySort.NAME -> found.sortedBy {it.record.displayName.lowercase(java.util.Locale.ROOT)}
        LibrarySort.ADDED -> found.sortedByDescending {it.addedAt}
        LibrarySort.PLAY_TIME -> found.sortedByDescending {it.playTimeMs}
    }
}
