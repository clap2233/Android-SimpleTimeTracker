package com.example.util.simpletimetracker.data_local.snapshot

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

internal class SnapshotSql {
    fun read(db: SupportSQLiteDatabase): Map<String, JsonArray> = SnapshotTables.names.associateWith { table ->
        JsonArray().also { rows ->
            db.query("SELECT * FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) rows.add(
                    JsonObject().apply {
                        cursor.columnNames.forEachIndexed { index, column ->
                            if (column != "restore_operation_id") {
                                add(
                                    column,
                                    when (cursor.getType(index)) {
                                        Cursor.FIELD_TYPE_NULL -> JsonNull.INSTANCE
                                        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(index).toString())
                                        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(cursor.getDouble(index))
                                        Cursor.FIELD_TYPE_STRING -> JsonPrimitive(cursor.getString(index))
                                        else -> error("Unsupported database field")
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    fun replace(db: SupportSQLiteDatabase, tables: Map<String, JsonArray>, operationId: String?) {
        SnapshotTables.names.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
        SnapshotTables.names.forEach { table ->
            data class Column(val name: String, val type: String, val required: Boolean, val default: String?)
            val columns = mutableMapOf<String, Column>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    columns[name] = Column(name, cursor.getString(2), cursor.getInt(3) == 1, if (cursor.isNull(4)) null else cursor.getString(4))
                }
            }
            SnapshotJson.objects(tables[table] ?: JsonArray()).forEach { row ->
                require(row.keySet().all { it in columns && it != "restore_operation_id" }) { "Unknown $table column" }
                val values = ContentValues()
                columns.values.filter { it.name != "restore_operation_id" }.forEach { column ->
                    val field = row.get(column.name)
                    if (field == null) {
                        require(!column.required || column.default != null) { "Missing ${column.name}" }
                    } else if (field.isJsonNull) {
                        require(!column.required)
                        values.putNull(column.name)
                    } else {
                        require(field.isJsonPrimitive)
                        when (column.type) {
                            "INTEGER" -> {
                                require(field.asJsonPrimitive.isString || field.asJsonPrimitive.isNumber)
                                values.put(column.name, field.asString.toBigDecimal().longValueExact())
                            }
                            "REAL" -> {
                                require(field.asJsonPrimitive.isNumber && field.asDouble.isFinite())
                                values.put(column.name, field.asDouble)
                            }
                            "TEXT" -> {
                                require(field.asJsonPrimitive.isString)
                                values.put(column.name, field.asString)
                            }
                            else -> error("Unsupported column type")
                        }
                    }
                }
                if (table == "lifeClockSettings") values.put("restore_operation_id", operationId)
                db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
        db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Invalid foreign key" } }
    }
}
