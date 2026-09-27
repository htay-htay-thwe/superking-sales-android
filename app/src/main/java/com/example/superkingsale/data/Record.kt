package com.example.superkingsale.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal

/** Read-only snapshot. Nullable backend fields and numeric strings are normalized here. */
data class Record(private val source: JsonObject = JsonObject()) {
    fun text(key: String, fallback: String = ""): String =
        source[key]?.takeUnless { it.isJsonNull || !it.isJsonPrimitive }?.asString ?: fallback
    fun number(key: String): Long = text(key).toBigDecimalOrNull()?.toLong() ?: 0
    fun decimal(key: String): BigDecimal = text(key).toBigDecimalOrNull() ?: BigDecimal.ZERO
    fun flag(key: String): Boolean = text(key) in listOf("true", "1")
    fun obj(key: String): Record = Record(source[key]?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject())
    fun rows(key: String): List<Record> = source[key]?.takeIf { it.isJsonArray }?.asJsonArray
        ?.filter { it.isJsonObject }?.map { Record(it.asJsonObject) } ?: emptyList()
    fun regionRows(): List<Record> = rows("regions").ifEmpty {
        obj("representative").rows("regions").ifEmpty {
            obj("data").rows("regions").ifEmpty { obj("options").rows("regions") }
        }
    }
    fun strings(key: String): List<String> = source[key]?.takeIf { it.isJsonArray }?.asJsonArray
        ?.filter { it.isJsonPrimitive }?.map { it.asString } ?: emptyList()
    /** Appends a paginated response while retaining the first page summary and the newest metadata. */
    fun appendPage(next: Record, rowsKey: String = "data"): Record {
        val merged = source.deepCopy()
        val rows = merged[rowsKey]?.takeIf { it.isJsonArray }?.asJsonArray?.deepCopy()
            ?: com.google.gson.JsonArray()
        val seen = rows.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
        }.toMutableSet()
        next.source[rowsKey]?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
            val id = element.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
            if (id == null || seen.add(id)) rows.add(element.deepCopy())
        }
        merged.add(rowsKey, rows)
        next.source["meta"]?.let { merged.add("meta", it.deepCopy()) }
        return Record(merged)
    }
    val id: Long get() = number("id")
    val name: String get() = text("name", text("title", text("reference")))
    val empty: Boolean get() = source.size() == 0
    fun json(): String = source.toString()
    companion object {
        fun parse(value: String): Record = Record(JsonParser.parseString(value).asJsonObject)
    }
}

data class ApiFailure(
    override val message: String,
    val status: Int = 0,
    val code: String = "",
    val fields: Map<String, String> = emptyMap(),
    val uncertain: Boolean = false,
) : Exception(message)
