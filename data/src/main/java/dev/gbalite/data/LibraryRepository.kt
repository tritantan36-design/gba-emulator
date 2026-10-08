package dev.gbalite.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import dev.gbalite.core.*
import dev.gbalite.storage.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LibraryRepository(context: Context,private val saves: SaveRepository) {
    private val app=context.applicationContext
    private val root=File(app.filesDir,"roms")
    companion object {
        private val timeout=Executors.newSingleThreadScheduledExecutor {r->Thread(r,"RomReadDeadline").apply {isDaemon=true}}
    }
    data class Added(val game: GameRecord,val duplicate: Boolean)
    suspend fun import(uri: Uri,expected: GameId?=null): Added=withContext(Dispatchers.IO) {
        if(uri.scheme!="content") throw ImportFailure(ImportError.UNREADABLE)
        val job=currentCoroutineContext();val signal=CancellationSignal()
        val input=java.util.concurrent.atomic.AtomicReference<java.io.InputStream?>()
        val alarm=timeout.schedule({runCatching {signal.cancel()};runCatching {input.get()?.close()}},RomImport.TIMEOUT_MS,TimeUnit.MILLISECONDS)
        var result: ValidatedRom?=null;var existed=false
        try {
            val name=app.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null,signal)?.use {
                if(!it.moveToFirst()) throw ImportFailure(ImportError.UNREADABLE)
                Triple(it.getString(0),if(it.isNull(1)) -1L else it.getLong(1),if(it.isNull(2)) -1L else it.getLong(2))
            } ?: throw ImportFailure(ImportError.UNREADABLE)
            if(name.first.isNullOrEmpty() || name.first.length>512) throw ImportFailure(ImportError.UNREADABLE)
            if(name.second>RomImport.INPUT_MAX) throw ImportFailure(ImportError.TOO_LARGE)
            val descriptor=app.contentResolver.openAssetFileDescriptor(uri,"r",signal) ?: throw ImportFailure(ImportError.UNREADABLE)
            input.set(descriptor.createInputStream())
            descriptor.use {
                result=RomImport(root) {job.ensureActive();if(signal.isCanceled) throw ImportFailure(ImportError.UNREADABLE)}
                    .import(requireNotNull(input.get()),name.first,expected)
            }
            job.ensureActive();if(signal.isCanceled) throw ImportFailure(ImportError.UNREADABLE);val rom=result!!
            existed=saves.game(rom.id)!=null
            val previous=saves.game(rom.id)
            val record=GameRecord(rom.id,previous?.displayName ?: rom.title,uri.toString(),name.second,name.third)
            val duplicate=saves.index(record)
            try {app.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)} catch(_: SecurityException) {}
            Added(record,duplicate)
        } catch(e: CancellationException) {
            result?.takeIf {!existed && runCatching {saves.game(it.id)==null}.getOrDefault(false)}?.file?.delete();throw e
        } catch(e: ImportFailure) {
            result?.takeIf {!existed && runCatching {saves.game(it.id)==null}.getOrDefault(false)}?.file?.delete()
            throw e
        }
        catch(e: Exception) {
            result?.takeIf {!existed && runCatching {saves.game(it.id)==null}.getOrDefault(false)}?.file?.delete()
            throw ImportFailure(ImportError.UNREADABLE).apply {initCause(e)}
        }
        finally {alarm.cancel(false);runCatching {signal.cancel()};runCatching {input.get()?.close()}}
    }
    suspend fun resolve(record: GameRecord): Added=withContext(Dispatchers.IO) {
        val snapshot=File(root,"${record.gameId.value}.gba")
        if(snapshot.isFile && snapshot.length() in 192..RomImport.ROM_MAX) {
            // A changed known source token is the only reason to reread an existing snapshot.
            val tokenSignal=CancellationSignal()
            val tokenAlarm=timeout.schedule({runCatching {tokenSignal.cancel()}},2,TimeUnit.SECONDS)
            val changed=runCatching {
                app.contentResolver.query(Uri.parse(record.romUri),arrayOf(OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null,tokenSignal)?.use {
                    it.moveToFirst() && !it.isNull(0) && !it.isNull(1) && it.getLong(1)>0 && record.sourceModified>0 &&
                        (it.getLong(0)!=record.sourceSize || it.getLong(1)!=record.sourceModified)
                } ?: false
            }.getOrDefault(false)
            tokenAlarm.cancel(false);tokenSignal.cancel();currentCoroutineContext().ensureActive()
            if(changed) return@withContext import(Uri.parse(record.romUri))
            saves.unavailable(record.gameId,false);return@withContext Added(record,false)
        }
        try {import(Uri.parse(record.romUri)).also {if(it.game.gameId!=record.gameId) saves.unavailable(record.gameId,true)}}
        catch(e: Exception) {if(e !is CancellationException) saves.unavailable(record.gameId,true);throw e}
    }
    fun snapshot(id: GameId)=File(root,"${id.value}.gba")
    fun list(): List<LibraryGame> = saves.library().map {row ->
        val record=with(saves) {row.record()}
        LibraryGame(record,row.addedAt,row.lastPlayedAt,row.playTimeMs,row.unavailable,artwork(record.gameId)?.absolutePath)
    }
    private fun artwork(id: GameId): File? {
        val dir=File(app.filesDir,"thumbnails/${id.value}")
        fun thumbnail(kind: String)=saves.listStates(id).filter {it.kind==kind}.sortedByDescending {it.createdAt}.firstNotNullOfOrNull {meta ->
            val key=if(kind=="manual") "slot-${meta.slot}" else "auto-${"abc"[((meta.sequence-1)%3).toInt()]}"
            val f=File(dir,"$key.webp");val stamp=File(dir,"$key.time")
            f.takeIf {it.isFile && it.length() in 1..262144 && stamp.length() in 1..128 && runCatching {stamp.readText()==meta.createdAt}.getOrDefault(false)}
        }
        return thumbnail("manual") ?: File(app.filesDir,"screenshots/${id.value}").listFiles()?.filter {it.extension=="webp" && it.length() in 1..262144}?.maxByOrNull {it.lastModified()} ?: thumbnail("auto")
    }
    fun remove(id: GameId) {snapshot(id).let {if(it.exists()&&!it.delete()) throw java.io.IOException("snapshot removal failed")};saves.remove(id)}
}
