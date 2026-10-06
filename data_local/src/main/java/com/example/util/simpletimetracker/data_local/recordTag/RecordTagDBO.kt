package com.example.util.simpletimetracker.data_local.recordTag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordTags")
data class RecordTagDBO(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long,

    @Deprecated("storing type id moved to a separate database")
    @ColumnInfo(name = "type_id")
    val typeId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "icon")
    val icon: String,

    @ColumnInfo(name = "color")
    val color: Int,

    // If not empty - take color from here, custom colorInt stored as text.
    @ColumnInfo(name = "color_int")
    val colorInt: String,

    @ColumnInfo(name = "icon_color_source")
    val iconColorSource: Long,

    @ColumnInfo(name = "archived")
    val archived: Boolean,

    @ColumnInfo(name = "note")
    val note: String,

    @ColumnInfo(name = "value_type")
    val valueType: Int?,

    @ColumnInfo(name = "value_suffix")
    val valueSuffix: String?,

    @ColumnInfo(name = "purpose", defaultValue = "0")
    val purpose: Int = 0,
)
