package dev.gbalite.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.gbalite.core.*
import dev.gbalite.storage.AtomicSaveStorage
import java.io.File
import java.time.Instant

class SaveRepository(context: Context, databaseName: String="save-metadata.db") : PersistenceStore, AutoCloseable {
    init { require(databaseName.matches(Regex("save-metadata(?:-test-[a-z]+)?\\.db"))) }
    private val applicationContext=context.applicationContext
    val storage by lazy { AtomicSaveStorage(File(applicationContext.filesDir,"persistence"), syncDirectory = { dir ->
        val fd=Os.open(dir.absolutePath,OsConstants.O_RDONLY,0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
    }) }
    private val db=Room.databaseBuilder(context.applicationContext,SaveDatabase::class.java,databaseName)
        .addMigrations(object: Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE GameEntity ADD COLUMN sourceSize INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE GameEntity ADD COLUMN sourceModified INTEGER NOT NULL DEFAULT -1")
            }
        },object: Migration(2,3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE GameEntity ADD COLUMN addedAt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE GameEntity ADD COLUMN playTimeMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE GameEntity ADD COLUMN localArtworkPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE GameEntity ADD COLUMN inLibrary INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE GameEntity ADD COLUMN unavailable INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE GameEntity SET addedAt=lastPlayedAt")
            }
        }).build()
    fun prepare() { storage.cleanupTemps() }
    override fun readBattery(id: GameId)=storage.readBattery(id)
    override fun writeBattery(id: GameId,bytes: ByteArray)=storage.writeBattery(id,bytes)
    override fun writeState(id: GameId,key: String,state: StoredState) {
        storage.writeState(id,key,state)
        db.saves().state(SaveStateEntity().apply {
            this.id="${id.value}:$key"; gameId=id.value; this.key=key; kind=state.metadata.kind
            slot=state.metadata.slot; sequence=state.metadata.sequence; createdAt=state.metadata.createdAt; coreVersion=state.metadata.coreVersion
        })
    }
    override fun readState(id: GameId,key: String)=storage.readState(id,key)
    override fun listStates(id: GameId)=storage.listStates(id)
    override fun markSession(game: GameRecord,clean: Boolean) {
        val time=Instant.now().toString()
        db.runInTransaction {
            index(game)
            db.saves().last(LastSessionEntity().apply {
                gameId=game.gameId.value; romUri=game.romUri; lastPlayedAt=time; sessionClosedCleanly=clean
            })
        }
    }
    fun lastGame(): GameRecord? {
        val last=db.saves().last() ?: return null
        val game=db.saves().game(last.gameId) ?: return null
        return game.takeIf {it.inLibrary}?.record()
    }
    fun GameEntity.record()=GameRecord(GameId(gameId),displayName,romUri,sourceSize,sourceModified)
    fun library(): List<GameEntity> = db.saves().library()
    fun game(id: GameId): GameEntity?=db.saves().game(id.value)
    /** Caller validates and commits the snapshot first. Never overwrites historical metadata. */
    fun index(game: GameRecord): Boolean {
        var duplicate=false
        db.runInTransaction {
            val row=db.saves().game(game.gameId.value)
            duplicate=row?.inLibrary==true
            db.saves().game((row ?: GameEntity().apply {
                gameId=game.gameId.value;romHash=game.gameId.value;displayName=game.displayName;addedAt=Instant.now().toString()
            }).apply {
                romUri=game.romUri;sourceSize=game.sourceSize;sourceModified=game.sourceModified;inLibrary=true;unavailable=false
            })
        }
        return duplicate
    }
    override fun addPlayTime(id: GameId,deltaMs: Long) {require(deltaMs>=0);db.saves().addTime(id.value,deltaMs)}
    override fun played(id: GameId) {db.saves().played(id.value,Instant.now().toString())}
    fun rename(id: GameId,name: String) {require(name.trim().length in 1..120);db.saves().rename(id.value,name.trim())}
    fun unavailable(id: GameId,value: Boolean) {db.saves().unavailable(id.value,value)}
    fun remove(id: GameId) {db.saves().hide(id.value)}
    fun recoveryNotice(): String? = storage.recoveryNotice
    override fun close() { db.close() }
}
