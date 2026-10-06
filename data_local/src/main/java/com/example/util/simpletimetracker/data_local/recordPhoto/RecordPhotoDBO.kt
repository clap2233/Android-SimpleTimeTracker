package com.example.util.simpletimetracker.data_local.recordPhoto

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.util.simpletimetracker.data_local.record.RecordDBO
import com.example.util.simpletimetracker.data_local.record.RunningRecordDBO

@Entity(
    tableName = "recordPhotos",
    foreignKeys = [
        ForeignKey(
            entity = RecordDBO::class,
            parentColumns = ["id"], childColumns = ["record_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RunningRecordDBO::class,
            parentColumns = ["id"], childColumns = ["running_record_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["record_id"]), Index(value = ["running_record_id"])],
)
data class RecordPhotoDBO(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "record_id")
    val recordId: Long? = null,

    @ColumnInfo(name = "running_record_id")
    val runningRecordId: Long? = null,

    @ColumnInfo(name = "relative_path")
    val relativePath: String,

    @ColumnInfo(name = "sha256")
    val sha256: String,

    @ColumnInfo(name = "position")
    val position: Int = 0,
) {
    init {
        require((recordId != null) xor (runningRecordId != null)) { "A photo must have exactly one owner." }
        require((recordId ?: runningRecordId ?: 0) > 0) { "Photo owner must exist." }
        require(position >= 0)
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid photo hash." }
        require(
            relativePath.startsWith("photos/") && '\\' !in relativePath && ':' !in relativePath &&
                relativePath.split('/').all { it.isNotEmpty() && it != "." && it != ".." },
        ) { "Photo path must stay in the managed photos directory." }
    }
}
