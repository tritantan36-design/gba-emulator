package dev.gbalite.data;
import androidx.room.*;
@Database(entities={GameEntity.class,LastSessionEntity.class,SaveStateEntity.class},version=3,exportSchema=true)
public abstract class SaveDatabase extends RoomDatabase { public abstract SaveDao saves(); }
