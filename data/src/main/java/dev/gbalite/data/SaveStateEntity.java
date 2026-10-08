package dev.gbalite.data;
import androidx.room.*;
import androidx.annotation.NonNull;
@Entity public class SaveStateEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String gameId = "";
    @NonNull public String key = "";
    @NonNull public String kind = "";
    @NonNull public String createdAt = "";
    @NonNull public String coreVersion = "";
    public int slot;
    public long sequence;
}
