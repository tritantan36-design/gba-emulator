package dev.gbalite.session

import dev.gbalite.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

enum class SessionState { EMPTY, LOADING, RUNNING, PAUSED, STOPPED, ERROR, CLOSED }
class EmulatorSession(private val factory: () -> EmulatorCore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val persistence: PersistenceStore? = null,
    private val screenshot: StateScreenshotWriter? = null,
    private val haptic: HapticOutput = DisabledHapticOutput(),
    monotonicMillis: ()->Long={System.nanoTime()/1_000_000}) {
    private val lock = Mutex()
    @Volatile private var core: EmulatorCore? = null
    private var foreground = false
    private var userPaused = false
    private val mutableUserPause=MutableStateFlow(false)
    val pausedByUser=mutableUserPause.asStateFlow()
    val input = dev.gbalite.input.InputRouter(::setButton)
    @Volatile var gameId: GameId? = null; private set
    private var game: GameRecord? = null
    private val mutableState = MutableStateFlow(SessionState.EMPTY)
    val state = mutableState.asStateFlow()
    private val playTime=PlayTime(monotonicMillis)
    private var firstFrame=false
    private fun transition(value: SessionState) {
        playTime.running(firstFrame && value==SessionState.RUNNING)
        mutableState.value=value
    }
    private fun flushPlayTime() {
        val id=gameId ?: return
        playTime.flush {persistence?.addPlayTime(id,it)}
    }
    private val mutableError = MutableStateFlow<PersistenceError?>(null)
    val persistenceError = mutableError.asStateFlow()
    private val mutableSlots = MutableStateFlow<List<StateMetadata>>(emptyList())
    val slots = mutableSlots.asStateFlow()
    val frames = FrameSource { target -> core?.frames?.copyFrame(target) ?: false }
    private var peripheralSettings=PeripheralSettings()
    val hapticStatus: String get()=haptic.status
    private fun manualMask()=(if(peripheralSettings.tilt==PeripheralMode.MANUAL) 16 else 0) or
        (if(peripheralSettings.gyro==PeripheralMode.MANUAL) 8 else 0) or
        (if(peripheralSettings.solar==PeripheralMode.MANUAL) 4 else 0)
    suspend fun configurePeripherals(settings: PeripheralSettings)=withContext(dispatcher) { lock.withLock {
        peripheralSettings=settings; haptic.stop(); core?.configurePeripherals(manualMask())
    } }
    suspend fun samplePeripherals(sample: PeripheralSample): PeripheralStatus=withContext(dispatcher) { lock.withLock {
        val active=core ?: return@withLock PeripheralStatus()
        val status=active.peripheralStatus()
        if(!firstFrame && mutableState.value==SessionState.RUNNING && active.playerMetrics().frames>0) {
            try {gameId?.let {persistence?.played(it)};firstFrame=true;playTime.running(true)}
            catch(e: Exception) {report(e,PersistenceError.STORAGE_IO_ERROR)}
        }
        if(mutableState.value==SessionState.RUNNING && !active.playerMetrics().rewinding) {
            active.updatePeripherals(sample); haptic.update(peripheralSettings.rumble && status.rumble)
        } else haptic.stop()
        status
    } }
    fun peripheralStatus()=core?.peripheralStatus() ?: PeripheralStatus()
    fun clearError() { mutableError.value=null }
    private fun report(e: Exception, fallback: PersistenceError): PersistenceError {
        val code=(e as? PersistenceException)?.code ?: fallback
        mutableError.value=code; return code
    }
    suspend fun load(source: GameSource, record: GameRecord? = null, resume: Boolean = false): LoadResult = withContext(dispatcher) {
        lock.withLock {
            if(mutableState.value==SessionState.CLOSED) return@withLock LoadResult.NativeError(-1)
            if(core!=null && persistence!=null && !checkpointLocked(true,true))
                return@withLock LoadResult.PersistenceFailed(mutableError.value ?: PersistenceError.SAVE_RAM_WRITE_FAILED)
            flushPlayTime();playTime.reset();firstFrame=false
            input.releaseAll(); core?.close(); core=null; game=null; gameId=null; userPaused=false; mutableUserPause.value=false; mutableError.value=null
            transition(SessionState.LOADING)
            var candidate: EmulatorCore?=null
            try {
                val created=factory(); candidate=created
                val result=created.loadGame(source)
                if(result!=LoadResult.Success) { created.close(); transition(SessionState.ERROR); return@withLock result }
                if(record!=null && persistence!=null) {
                    val battery=persistence.readBattery(record.gameId)
                    if(battery!=null && !created.importSaveRam(battery)) throw PersistenceException(PersistenceError.SAVE_RAM_READ_FAILED)
                    if(resume) {
                        val autos=persistence.listStates(record.gameId).filter { it.kind=="auto" }.sortedByDescending { it.sequence }
                        for(meta in autos) {
                            val key="auto-${"abc"[((meta.sequence-1)%3).toInt()]}"
                            val saved=runCatching { persistence.readState(record.gameId,key) }.getOrNull() ?: continue
                            if(created.importState(saved.bytes)) break
                        }
                    }
                    persistence.markSession(record,false)
                    mutableSlots.value=persistence.listStates(record.gameId)
                }
                game=record; gameId=record?.gameId; core=created
                created.configurePeripherals(manualMask())
                if(foreground) created.start()
                transition(if(foreground) SessionState.RUNNING else SessionState.PAUSED)
                LoadResult.Success
            } catch(e: Exception) {
                candidate?.close(); core=null; game=null; transition(SessionState.ERROR)
                if(e is PersistenceException) LoadResult.PersistenceFailed(report(e,e.code)) else LoadResult.IoError(e)
            } catch(e: LinkageError) {
                candidate?.close(); core=null; game=null; transition(SessionState.ERROR); LoadResult.NativeError(-2)
            }
        }
    }
    private fun checkpointLocked(autosave: Boolean, clean: Boolean): Boolean {
        val active=core ?: return true
        val record=game ?: return true
        val store=persistence ?: return true
        val wasRewinding=active.playerMetrics().rewinding
        try {
            haptic.stop(); active.pause(); transition(SessionState.PAUSED)
            input.releaseAll()
            when(val exported=active.exportSaveRam()) {
                is BytesResult.Success -> store.writeBattery(record.gameId,exported.bytes)
                is BytesResult.Failure -> throw PersistenceException(exported.code)
                BytesResult.NoSave -> Unit
            }
        } catch(e: Exception) { report(e,PersistenceError.SAVE_RAM_WRITE_FAILED); return false }
        if(autosave && !wasRewinding) {
            try {
                val seq=(store.listStates(record.gameId).filter { it.kind=="auto" }.maxOfOrNull { it.sequence } ?: 0)+1
                captureLocked("auto-${"abc"[((seq-1)%3).toInt()]}",0,"auto",seq)
            } catch(_: Exception) { mutableError.value=PersistenceError.AUTOSAVE_FAILED }
        }
        try { store.markSession(record,clean) } catch(e: Exception) { report(e,PersistenceError.STORAGE_IO_ERROR) }
        try {flushPlayTime()} catch(e: Exception) {report(e,PersistenceError.STORAGE_IO_ERROR);return false}
        return true
    }
    suspend fun checkpoint(): Boolean=withContext(dispatcher) {
        lock.withLock {
            val running=mutableState.value==SessionState.RUNNING
            val metrics=core?.playerMetrics()
            if(metrics?.rewinding==true) return@withLock true
            val ok=checkpointLocked(true,false)
            if(ok && running) {
                if((metrics?.speed ?: 1)>1) core?.setSpeed(metrics!!.speed)
                core?.resume(); transition(SessionState.RUNNING)
            }
            ok
        }
    }
    suspend fun setForeground(value: Boolean)=withContext(NonCancellable+dispatcher) {
        lock.withLock {
            foreground=value
            if(!value) haptic.stop()
            val active=core ?: return@withLock
            if(mutableState.value !in setOf(SessionState.RUNNING,SessionState.PAUSED)) return@withLock
            try {
                if(value && !userPaused) { active.resume(); transition(SessionState.RUNNING) }
                else if(!value) {
                    if(persistence!=null && game!=null) checkpointLocked(true,false)
                    else { input.releaseAll(); active.pause(); transition(SessionState.PAUSED) }
                }
            } catch(e: Exception) { report(e,PersistenceError.STORAGE_IO_ERROR); transition(SessionState.PAUSED) }
        }
    }
    fun setButton(button: GbaButton,down: Boolean) {
        if(!down || mutableState.value==SessionState.RUNNING) core?.setButton(button,down)
    }
    suspend fun setPaused(value: Boolean)=withContext(dispatcher) { lock.withLock {
        if(value) haptic.stop()
        userPaused=value; mutableUserPause.value=value
        if(value) { input.releaseAll(); core?.pause(); transition(SessionState.PAUSED) }
        else if(foreground && core!=null) { core?.resume(); transition(SessionState.RUNNING) }
    } }
    suspend fun setSpeed(value: Int): Boolean=withContext(dispatcher) { lock.withLock {
        if(mutableState.value!=SessionState.RUNNING) false else core?.setSpeed(value) ?: false
    } }
    suspend fun setRewinding(value: Boolean): Boolean=withContext(dispatcher) { lock.withLock {
        haptic.stop()
        input.releaseAll()
        if(mutableState.value!=SessionState.RUNNING) false else core?.setRewinding(value) ?: false
    } }
    suspend fun trimRewind()=withContext(dispatcher) { lock.withLock { core?.trimRewind() } }
    fun playerMetrics()=core?.playerMetrics() ?: PlayerMetrics()
    private fun captureLocked(key: String,slot: Int,kind: String,sequence: Long=0) {
        val active=core ?: throw PersistenceException(PersistenceError.STATE_WRITE_FAILED)
        val record=game ?: throw PersistenceException(PersistenceError.STATE_WRITE_FAILED)
        val store=persistence ?: throw PersistenceException(PersistenceError.STATE_WRITE_FAILED)
        val bytes=(active.exportState() as? BytesResult.Success)?.bytes ?: throw PersistenceException(PersistenceError.STATE_WRITE_FAILED)
        val meta=StateMetadata(gameId=record.gameId.value,createdAt=Instant.now().toString(),slot=slot,kind=kind,
            stateSize=bytes.size,stateSha256=sha256(bytes),sequence=sequence)
        store.writeState(record.gameId,key,StoredState(bytes,meta))
        runCatching { screenshot?.write(record.gameId,key,frames,meta.createdAt) }
        mutableSlots.value=store.listStates(record.gameId)
    }
    suspend fun saveState(slot: Int? = null): Boolean=stateCommand(false,slot)
    suspend fun loadState(slot: Int? = null): Boolean=stateCommand(true,slot)
    private suspend fun stateCommand(load: Boolean,slot: Int?): Boolean=withContext(dispatcher) {
        lock.withLock {
            require(slot==null || slot in 1..4)
            val active=core ?: return@withLock false
            val record=game ?: return@withLock false
            val running=mutableState.value==SessionState.RUNNING
            try {
                haptic.stop(); active.pause(); transition(SessionState.PAUSED)
                val key=slot?.let { "slot-$it" } ?: "quick"
                if(load) {
                    if(!checkpointLocked(false,false)) return@withLock false
                    val saved=persistence?.readState(record.gameId,key) ?: throw PersistenceException(PersistenceError.STATE_READ_FAILED)
                    if(!active.importState(saved.bytes)) throw PersistenceException(PersistenceError.STATE_CORRUPTED)
                } else captureLocked(key,slot ?: 0,if(slot==null) "quick" else "manual")
                mutableError.value=null; true
            } catch(e: Exception) { report(e,if(load) PersistenceError.STATE_READ_FAILED else PersistenceError.STATE_WRITE_FAILED); false }
            finally {
                if(running) { active.resume(); transition(SessionState.RUNNING) }
            }
        }
    }
    suspend fun stop(): Boolean=withContext(NonCancellable+dispatcher) {
        lock.withLock {
            haptic.stop()
            if(mutableState.value==SessionState.CLOSED) return@withLock true
            if(!checkpointLocked(true,true)) return@withLock false
            input.releaseAll(); core?.close(); core=null; game=null; gameId=null; transition(SessionState.STOPPED); true
        }
    }
    suspend fun close()=withContext(NonCancellable+dispatcher) {
        lock.withLock {
            haptic.stop()
            checkpointLocked(true,true)
            input.releaseAll(); core?.close(); core=null; game=null; gameId=null; transition(SessionState.CLOSED)
        }
    }
}
