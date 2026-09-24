package com.example.superkingsale.ui

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Translations {
    private var translator: TextTranslator? = null
    @Synchronized fun translator(context: Context): TextTranslator {
        return translator ?: run {
            fun read(name: String) = context.assets.open(name).bufferedReader(Charsets.UTF_8).use {
                Gson().fromJson<Map<String, String>>(it, object : TypeToken<Map<String, String>>() {}.type)
            }
            TextTranslator(read("myanmar.json") + read("native-myanmar.json"))
        }.also { translator = it }
    }
}
fun Context.tr(value: String): String {
    if (getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("language", "en") != "my") return value
    return Translations.translator(this).translate(value)
}
