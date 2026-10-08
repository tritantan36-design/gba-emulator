package dev.gbalite.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.*
import dev.gbalite.core.*
import dev.gbalite.data.SaveRepository
import dev.gbalite.mgba.MgbaCoreAdapter
import dev.gbalite.session.EmulatorSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest
import java.util.UUID

class PlayerViewModel(application: Application): AndroidViewModel(application) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private data class LifecycleCommand(val foreground: Boolean,val applied: CompletableDeferred<Unit>)
    private val lifecycleCommands=Channel<LifecycleCommand>(Channel.UNLIMITED)
    // Session PAUSED can also mean an in-flight periodic checkpoint. This
    // acknowledgement identifies completion of the actual lifecycle command.
    @Volatile internal var lifecycleApplied: Deferred<Unit> = CompletableDeferred(Unit); private set
    private val repository=SaveRepository(application)
    private val libraryRepository=dev.gbalite.data.LibraryRepository(application,repository)
    var library by mutableStateOf<List<LibraryGame>>(emptyList()); private set
    var imported by mutableStateOf<GameRecord?>(null); private set
    private var importJob: Job?=null
    private suspend fun refreshLibrary() {
        try {library=withContext(Dispatchers.IO) {libraryRepository.list()};lastGame=withContext(Dispatchers.IO) {repository.lastGame()}}
        catch(e: CancellationException) {throw e}
        catch(_: Exception) {message=errorText(PersistenceError.STORAGE_IO_ERROR)}
    }
    fun dismissMessage() {message=null;imported=null}
    private val displayPreferences=dev.gbalite.data.DisplayPreferences(application)
    private val displayCommands=Channel<DisplaySettings>(Channel.UNLIMITED)
    var displaySettings by mutableStateOf(DisplaySettings()); private set
    fun display(value: DisplaySettings) { displaySettings=value; displayCommands.trySend(value) }
    fun displayFallback(text: String) { message=text; display(displaySettings.copy(displayMode=DisplayMode.ORIGINAL)) }
    val images=PlayerImages(application.filesDir)
    val profiles=PrivateInputProfiles(application.filesDir)
    val session=EmulatorSession(factory={ MgbaCoreAdapter() },persistence=repository,screenshot=images)
    val sensors=SensorAdapter(application)
    private val peripheralPreferences=dev.gbalite.data.PeripheralPreferences(application)
    private val peripheralCommands=Channel<PeripheralSettings>(Channel.CONFLATED)
    var peripheralSettings by mutableStateOf(PeripheralSettings()); private set
    var peripheralDetected by mutableIntStateOf(0); private set
    private var sensorForeground=false
    private var sensorStopEpoch=-1L
    private var sensorGameId: GameId?=null
    fun peripherals(value: PeripheralSettings) { peripheralSettings=value; sensors.configure(value); peripheralCommands.trySend(value) }
    fun calibrate() { peripherals(sensors.calibration()) }
    var appSettings by mutableStateOf(false); private set
    var startManual by mutableStateOf(false); private set
    var startMenu by mutableStateOf(false); private set
    fun openAppSettings() {scope.launch {session.setPaused(true);startManual=false;appSettings=true}}
    fun closeAppSettings() {appSettings=false}
    fun returnToManual() {startManual=true;appSettings=false}
    fun consumeRequests() {startManual=false;startMenu=false}
    fun openSaves(game: GameRecord) {startMenu=true;play(game)}
    init {
        scope.launch { try { peripheralPreferences.settings.collect {
            peripheralSettings=it;sensors.configure(it);session.configurePeripherals(it)
        } } catch(_: java.io.IOException) { message="外设设置读取失败，使用安全默认值。" } }
        scope.launch { for(value in peripheralCommands) try { peripheralPreferences.write(value) }
            catch(_: java.io.IOException) { message="外设设置保存失败，请重试。" } }
        scope.launch { while(isActive) {
            val state=session.state.value
            val metrics=session.playerMetrics()
            val status=session.peripheralStatus();peripheralDetected=status.detected
            if(sensorStopEpoch!=status.stopEpoch || sensorGameId!=session.gameId) {
                sensors.stop();sensorStopEpoch=status.stopEpoch;sensorGameId=session.gameId
            }
            sensors.activate(status.detected,sensorForeground && state==dev.gbalite.session.SessionState.RUNNING && !metrics.rewinding)
            session.samplePeripherals(sensors.snapshot())
            delay(20)
        } }
    }
    fun screenshot() { scope.launch { message=try {
        withContext(Dispatchers.IO) { images.screenshot(session.frames,session.gameId) }; "截图已保存到应用私有 screenshots 目录。"
    } catch(_: Exception) { "截图保存失败；游戏和存档不受影响。" } } }
    var playing by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var importing by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var lastGame by mutableStateOf<GameRecord?>(null); private set
    init {
        scope.launch {
            try { displayPreferences.settings.collect { displaySettings=it } }
            catch(_: java.io.IOException) { displaySettings=DisplaySettings.FALLBACK; message="显示设置读取失败，已恢复 Original。" }
        }
        scope.launch { for(value in displayCommands) {
            try { displayPreferences.write(value) }
            catch(_: java.io.IOException) { message="显示设置未能保存，请重试。" }
        } }
        scope.launch { for(command in lifecycleCommands) {
            try { session.setForeground(command.foreground);command.applied.complete(Unit) }
            catch(e: Throwable) {command.applied.completeExceptionally(e);throw e}
        } }
        scope.launch {
            try { lastGame=withContext(Dispatchers.IO) { repository.prepare(); repository.lastGame() } }
            catch(_: Exception) { message=errorText(PersistenceError.STORAGE_IO_ERROR) }
            refreshLibrary()
        }
        scope.launch {
            while(isActive) { delay(45000); if(playing) session.checkpoint() }
        }
    }
    fun add(uri: Uri,relink: GameId?=null) {
        if(loading) return
        loading=true;importing=true;message=null;imported=null
        importJob=scope.launch {
            try {
                val result=libraryRepository.import(uri,relink)
                refreshLibrary()
                imported=result.game;message=if(result.duplicate) "已在游戏库中" else "游戏已添加"
            } catch(e: CancellationException) {message="已取消添加";throw e}
            catch(e: Exception) {message=importMessage(e);refreshLibrary()}
            finally {loading=false;importing=false}
        }
    }
    fun cancelImport() {importJob?.cancel()}
    fun open(uri: Uri,resume: Boolean=false,expected: GameRecord?=null) {
        if(loading) return
        sensors.stop();loading=true;message=null;imported=null
        scope.launch {
            try {
                val added=if(expected!=null) libraryRepository.resolve(expected) else libraryRepository.import(uri)
                val changed=expected!=null && expected.gameId!=added.game.gameId
                if(changed) message="ROM 内容已变化，将作为新游戏打开；旧存档已保留。"
                val result=withContext(Dispatchers.IO) {
                    val rom=libraryRepository.snapshot(added.game.gameId)
                    ParcelFileDescriptor.open(rom,ParcelFileDescriptor.MODE_READ_ONLY).use {pfd ->
                        session.load(GameSource.FileDescriptorSource(pfd.fd,rom.length(),rom.name),added.game,resume && !changed)
                    }
                }
                if(result==LoadResult.Success) {
                    playing=true;lastGame=added.game
                    repository.recoveryNotice()?.let {message=it}
                } else message=when(result) {is LoadResult.PersistenceFailed -> errorText(result.code);else -> "无法加载该 GBA 文件。"}
            } catch(e: Exception) {message=if(e is dev.gbalite.storage.ImportFailure && e.error==dev.gbalite.storage.ImportError.UNREADABLE)
                errorText(PersistenceError.ROM_URI_REVOKED) else importMessage(e)}
            finally {loading=false;refreshLibrary()}
        }
    }
    fun play(game: GameRecord,resume: Boolean=true) {open(Uri.parse(game.romUri),resume,game)}
    fun rename(game: GameId,name: String) {scope.launch {try {withContext(Dispatchers.IO) {repository.rename(game,name)};refreshLibrary()} catch(_: Exception) {message="名称保存失败，请重试。"}}}
    fun remove(game: GameId) {scope.launch {try {withContext(Dispatchers.IO) {libraryRepository.remove(game)};refreshLibrary()} catch(_: Exception) {message="移除失败；存档仍保留。"}}}
    fun resetProfile(landscape: Boolean) {scope.launch {try {withContext(Dispatchers.IO) {profiles.write(dev.gbalite.input.InputProfile.default(landscape))};message="布局已恢复默认"} catch(_: Exception) {message="布局保存失败"}}}
    fun saveProfile(profile: dev.gbalite.input.InputProfile) {scope.launch {try {withContext(Dispatchers.IO) {profiles.write(profile)};message="布局已保存"} catch(_: Exception) {message="布局保存失败，请重试。"}}}
    fun resumeLast() { lastGame?.let { open(Uri.parse(it.romUri),true,it) } }
    fun exit() { scope.launch { if(session.stop()) { playing=false;refreshLibrary() } } }
    fun foreground(value: Boolean) {
        sensorForeground=value;if(!value) sensors.stop()
        val applied=CompletableDeferred<Unit>();lifecycleApplied=applied
        if(lifecycleCommands.trySend(LifecycleCommand(value,applied)).isFailure) applied.cancel()
    }
    fun trimRewind() { scope.launch { session.trimRewind() } }
    override fun onCleared() {
        sensors.stop();peripheralCommands.close()
        lifecycleCommands.close()
        displayCommands.close()
        scope.cancel()
        CoroutineScope(Dispatchers.IO).launch { try { session.close() } finally { repository.close() } }
    }
}
fun errorText(code: PersistenceError): String=when(code) {
    PersistenceError.SAVE_RAM_WRITE_FAILED -> "正常存档保存失败，旧有效存档已保留。请释放空间后重试。"
    PersistenceError.SAVE_RAM_READ_FAILED -> "正常存档和备份无法安全读取，已保留文件并停止加载。"
    PersistenceError.STATE_WRITE_FAILED -> "即时存档保存失败，旧版本已保留。"
    PersistenceError.STATE_READ_FAILED -> "没有可读取的即时存档。"
    PersistenceError.STATE_INCOMPATIBLE -> "该即时存档由不同的模拟核心或版本创建，当前版本无法安全读取。"
    PersistenceError.STATE_ROM_MISMATCH -> "即时存档属于其他 ROM，已拒绝加载。"
    PersistenceError.STATE_CORRUPTED -> "即时存档损坏或元数据无效，已拒绝加载并保留文件。"
    PersistenceError.AUTOSAVE_FAILED -> "自动即时存档失败；正常游戏存档已优先保存。"
    PersistenceError.ROM_URI_REVOKED -> "上次 ROM 已无法访问，请重新选择文件；存档仍保留。"
    PersistenceError.STORAGE_IO_ERROR -> "存储操作失败，请检查空间并重试。"
}

fun importMessage(error: Exception): String=when((error as? dev.gbalite.storage.ImportFailure)?.error) {
    dev.gbalite.storage.ImportError.TOO_LARGE -> "文件过大"
    dev.gbalite.storage.ImportError.INVALID_GBA -> "请选择有效的 GBA 游戏文件"
    dev.gbalite.storage.ImportError.ZIP_EMPTY -> "ZIP 中没有 GBA 游戏"
    dev.gbalite.storage.ImportError.ZIP_MULTIPLE -> "ZIP 中包含多个 GBA 游戏"
    dev.gbalite.storage.ImportError.ZIP_UNSAFE -> "压缩包不安全或已损坏"
    dev.gbalite.storage.ImportError.MISMATCH -> "选择的文件与原游戏不匹配"
    else -> "无法读取游戏文件；文件可能已失效，请重新选择"
}
