package dev.gbalite.data;
import androidx.room.*;
@Dao public interface SaveDao {
    @Insert(onConflict=OnConflictStrategy.REPLACE) void game(GameEntity game);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void last(LastSessionEntity session);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void state(SaveStateEntity state);
    @Query("SELECT * FROM LastSessionEntity WHERE id=1") LastSessionEntity last();
    @Query("SELECT * FROM GameEntity WHERE gameId=:id") GameEntity game(String id);
    @Query("SELECT * FROM GameEntity WHERE inLibrary=1 ORDER BY lastPlayedAt DESC, addedAt DESC") java.util.List<GameEntity> library();
    @Query("UPDATE GameEntity SET playTimeMs=playTimeMs+:delta WHERE gameId=:id") void addTime(String id,long delta);
    @Query("UPDATE GameEntity SET lastPlayedAt=:time WHERE gameId=:id") void played(String id,String time);
    @Query("UPDATE GameEntity SET displayName=:name WHERE gameId=:id") void rename(String id,String name);
    @Query("UPDATE GameEntity SET unavailable=:value WHERE gameId=:id") void unavailable(String id,boolean value);
    @Query("UPDATE GameEntity SET inLibrary=0, localArtworkPath='' WHERE gameId=:id") void hide(String id);
}
