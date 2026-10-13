package com.example.util.simpletimetracker.data_local.snapshot

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.math.BigDecimal

internal object SnapshotJson {
    fun parse(text: String): JsonObject {
        JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            fun read(depth: Int): JsonElement {
                require(depth <= 32) { "JSON nesting exceeds the supported limit" }
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> JsonObject().also { result ->
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val name = reader.nextName()
                            require(!result.has(name)) { "Duplicate JSON key" }
                            result.add(name, read(depth + 1))
                        }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> JsonArray().also { result ->
                        reader.beginArray()
                        while (reader.hasNext()) result.add(read(depth + 1))
                        reader.endArray()
                    }
                    JsonToken.STRING -> JsonPrimitive(reader.nextString())
                    JsonToken.NUMBER -> JsonPrimitive(BigDecimal(reader.nextString()))
                    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                    JsonToken.NULL -> {
                        reader.nextNull()
                        JsonNull.INSTANCE
                    }
                    else -> error("Invalid JSON value")
                }
            }
            val root = read(0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
            require(root.isJsonObject)
            return root.asJsonObject
        }
    }

    fun value(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is JsonElement -> value.deepCopy()
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Iterable<*> -> JsonArray().also { result -> value.forEach { result.add(value(it)) } }
        else -> error("Unsupported snapshot value")
    }

    fun obj(vararg fields: Pair<String, Any?>): JsonObject = JsonObject().also { result -> fields.forEach { (key, value) -> result.add(key, value(value)) } }
    fun objects(array: JsonArray): List<JsonObject> = array.map {
        require(it.isJsonObject)
        it.asJsonObject
    }
    fun array(root: JsonObject, name: String): JsonArray {
        val value = root.get(name) ?: return JsonArray()
        require(value.isJsonArray) { "$name must be an array" }
        return value.asJsonArray
    }
    fun text(root: JsonObject, name: String, default: String? = null): String {
        val value = root.get(name) ?: return requireNotNull(default) { "Missing $name" }
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$name must be text" }
        return value.asString
    }
    fun number(root: JsonObject, name: String): Long {
        val value = requireNotNull(root.get(name))
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$name must be an integer" }
        return value.asBigDecimal.longValueExact()
    }
    fun id(value: JsonElement, zero: Boolean = false): Long {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        val text = value.asString
        require(text.matches(Regex(if (zero) "0|[1-9][0-9]*" else "[1-9][0-9]*"))) { "Invalid ID" }
        return text.toLong()
    }
    fun id(root: JsonObject, name: String, zero: Boolean = false): Long = id(requireNotNull(root.get(name)), zero)
    fun nullableId(root: JsonObject, name: String): Long? = root.get(name)?.takeUnless { it.isJsonNull }?.let(::id)
    fun bool(root: JsonObject, name: String, default: Boolean = false): Boolean {
        val value = root.get(name) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
        return value.asBoolean
    }
    fun safePath(path: String) {
        require(path.startsWith("photos/") && '\\' !in path && ':' !in path)
        require(path.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
    }
}
