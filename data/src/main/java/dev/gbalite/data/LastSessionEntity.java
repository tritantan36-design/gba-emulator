package dev.gbalite.data;
import androidx.room.*;
import androidx.annotation.NonNull;
@Entity public class LastSessionEntity {
    @PrimaryKey public int id = 1;
    @NonNull public String gameId = "";
    @NonNull public String romUri = "";
    @NonNull public String lastPlayedAt = "";
    public boolean sessionClosedCleanly;
}
