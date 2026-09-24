package com.example.superkingsale.ui

/** Reuses source-portal translations, including its named interpolation templates. */
class TextTranslator(private val words: Map<String, String>) {
    private data class Template(val pattern: Regex, val names: List<String>, val value: String)
    private val placeholder = Regex("\\{[A-Za-z_][A-Za-z_0-9]*\\}")
    // Do not infer translations from catch-all source templates such as "{action} {name}".
    // They also match business names, paper sizes and amounts; only phrases with fixed words are safe.
    private val templates = words.filterKeys { placeholder.containsMatchIn(it) &&
        placeholder.replace(it, "").any { character -> character in 'A'..'Z' || character in 'a'..'z' }
    }.entries.sortedByDescending { placeholder.replace(it.key, "").length }.map { (key, value) ->
        val names = mutableListOf<String>()
        var cursor = 0
        val pattern = buildString {
            append("^")
            Regex("\\{([A-Za-z_][A-Za-z_0-9]*)\\}").findAll(key).forEach {
                append(Regex.escape(key.substring(cursor, it.range.first))); append("(.+?)")
                names += it.groupValues[1]; cursor = it.range.last + 1
            }
            append(Regex.escape(key.substring(cursor))); append("$")
        }
        Template(Regex(pattern), names, value)
    }
    fun translate(value: String): String {
        words[value]?.let { return it }
        val trimmed = value.trim()
        words[trimmed]?.let { return value.replace(trimmed, it) }
        if (value.contains('\n')) return value.split('\n').joinToString("\n", transform = ::translate)
        if (value.contains(" · ")) return value.split(" · ").joinToString(" · ", transform = ::translate)
        for (template in templates) {
            val match = template.pattern.matchEntire(value) ?: continue
            var translated = template.value
            template.names.forEachIndexed { index, name -> translated = translated.replace("{$name}", match.groupValues[index + 1]) }
            return translated
        }
        val colon = value.indexOf(": ")
        if (colon > 0 && words.containsKey(value.substring(0, colon)))
            return words.getValue(value.substring(0, colon)) + ": " + translate(value.substring(colon + 2))
        if (value == value.uppercase(java.util.Locale.ROOT)) words[value.lowercase(java.util.Locale.ROOT)]?.let { return it }
        return value
    }
}
