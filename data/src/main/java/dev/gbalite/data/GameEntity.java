package dev.gbalite.data;
import androidx.room.*;
import androidx.annotation.NonNull;
@Entity public class GameEntity {
    @PrimaryKey @NonNull public String gameId = "";
    @NonNull public String displayName = "";
    @NonNull public String romUri = "";
    @NonNull public String romHash = "";
    @NonNull public String lastPlayedAt = "";
    @ColumnInfo(defaultValue="-1") public long sourceSize = -1;
    @ColumnInfo(defaultValue="-1") public long sourceModified = -1;
    @NonNull @ColumnInfo(defaultValue="''") public String addedAt = "";
    @ColumnInfo(defaultValue="0") public long playTimeMs;
    @NonNull @ColumnInfo(defaultValue="''") public String localArtworkPath = "";
    @ColumnInfo(defaultValue="1") public boolean inLibrary = true;
    @ColumnInfo(defaultValue="0") public boolean unavailable;
}
