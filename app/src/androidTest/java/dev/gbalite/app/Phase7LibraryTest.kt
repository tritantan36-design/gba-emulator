@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.gbalite.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.gbalite.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Ordinary QA with synthetic metadata and project-generated images only. */
class Phase7LibraryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>(effectContext=kotlinx.coroutines.Dispatchers.Main)
    @Test fun thousandSyntheticRoomRowsSurviveCloseAndReopen() {
        val context=compose.activity.applicationContext
        val name="save-metadata-test-phaseqa.db"
        context.deleteDatabase(name)
        val metrics=File(context.filesDir,"phase7-room-thousand-rows.jsonl");metrics.writeText("")
        try {
            dev.gbalite.data.SaveRepository(context,name).use {repository ->
                var added=0
                for(size in listOf(0,1,100,500,1000)) {
                    val started=android.os.SystemClock.elapsedRealtime()
                    while(added<size) {
                        val id=GameId.fromBytes("phase7-db-$added".toByteArray())
                        repository.index(GameRecord(id,"Synthetic DB %04d".format(added),"content://synthetic/$added"))
                        repository.addPlayTime(id,added*1000L);added++
                    }
                    val rows=repository.library()
                    assertEquals(size,rows.size);assertEquals(size,rows.map {it.gameId}.toSet().size)
                    metrics.appendText("{\"rows\":$size,\"elapsedMs\":${android.os.SystemClock.elapsedRealtime()-started},\"result\":\"PASS\"}\n")
                }
            }
            dev.gbalite.data.SaveRepository(context,name).use {repository ->
                val rows=repository.library();assertEquals(1000,rows.size)
                val last=rows.single {it.gameId==GameId.fromBytes("phase7-db-999".toByteArray()).value}
                assertEquals("Synthetic DB 0999",last.displayName);assertEquals(999000L,last.playTimeMs)
            }
        } finally {context.deleteDatabase(name)}
    }
    @Test fun zeroOneHundredFiveHundredAndThousandRowsScrollSearchAndNavigation() {
        val model=ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
        val rows=(0 until 1000).map {n -> LibraryGame(
            GameRecord(GameId.fromBytes("phase7-library-$n".toByteArray()),"QA Game %04d".format(n),"content://synthetic/$n"),
            "2026-10-07","2026-10-07",n*1000L,false)}
        val evidence=File(compose.activity.filesDir,"phase7-library-sizes.jsonl")
        evidence.writeText("")
        for(size in listOf(0,1,100,500,1000)) {
            val started=android.os.SystemClock.elapsedRealtime()
            compose.runOnUiThread {compose.activity.setContent {MaterialTheme {AppShell(model,rows.take(size))}}}
            compose.waitForIdle()
            if(size==0) compose.onNodeWithText("还没有游戏").assertExists()
            else {
                compose.onNodeWithTag("nav-游戏库").performClick()
                compose.onNodeWithTag("library-list").performScrollToIndex(size-1)
                val last="QA Game %04d".format(size-1)
                compose.onNodeWithText(last).assertIsDisplayed()
                compose.onNodeWithTag("library-search").performTextReplacement(last)
                compose.onNodeWithTag("game-${rows[size-1].record.gameId.value}").assertExists()
                compose.onNodeWithTag("library-search").performTextClearance()
                compose.onNodeWithTag("nav-首页").performClick()
                compose.onNodeWithTag("nav-游戏库").performClick()
            }
            evidence.appendText("{\"rows\":$size,\"elapsedMs\":${android.os.SystemClock.elapsedRealtime()-started},\"result\":\"PASS\"}\n")
        }
    }
    @Test fun hundredsOfThumbnailsStayBoundedAndLowMemoryTrimsWithoutInvalidatingOwners() {
        val directory=java.nio.file.Files.createTempDirectory(compose.activity.cacheDir.toPath(),"phase7-images-").toFile()
        val bitmap=android.graphics.Bitmap.createBitmap(160,160,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        var retained: android.graphics.Bitmap?=null
        try {
            repeat(300) {n ->
                val file=File(directory,"$n.webp")
                file.outputStream().use {out -> @Suppress("DEPRECATION") assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP,100,out))}
                val decoded=ArtworkCache.load(file.path);assertNotNull(decoded)
                if(n==0) retained=decoded
                assertTrue(ArtworkCache.images.size()<=2*1024*1024)
            }
            assertNull(ArtworkCache.load(File(directory,"missing.webp").path))
            val corrupt=File(directory,"corrupt.webp").apply {writeText("not an image")}
            assertNull(ArtworkCache.load(corrupt.path))
            val oversized=android.graphics.Bitmap.createBitmap(2048,16,android.graphics.Bitmap.Config.ARGB_8888)
            try {File(directory,"wide.webp").outputStream().use {out ->
                @Suppress("DEPRECATION") assertTrue(oversized.compress(android.graphics.Bitmap.CompressFormat.WEBP,100,out))
            }} finally {oversized.recycle()}
            assertNull(ArtworkCache.load(File(directory,"wide.webp").path))
            val retainedPixel=retained!!.getPixel(0,0)
            compose.runOnUiThread {compose.activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)}
            assertEquals(0,ArtworkCache.images.size())
            assertFalse(retained!!.isRecycled)
            assertEquals(retainedPixel,retained!!.getPixel(0,0))
        } finally {
            ArtworkCache.trim();bitmap.recycle()
            directory.listFiles()?.forEach {it.delete()};directory.delete()
        }
    }
}
