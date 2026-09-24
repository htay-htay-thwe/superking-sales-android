package com.example.superkingsale

import com.example.superkingsale.ui.TextTranslator
import org.junit.Assert.assertEquals
import org.junit.Test

class TextTranslatorTest {
    @org.junit.Test fun genericSourceTemplatesCannotRewritePaperAmountsOrNames() {
        val translator = com.example.superkingsale.ui.TextTranslator(mapOf("{action} {name}" to "{name} ကို {action}", "Hello, {name}" to "မင်္ဂလာပါ {name}"))
        org.junit.Assert.assertEquals("58 mm", translator.translate("58 mm"))
        org.junit.Assert.assertEquals("539,102 MMK", translator.translate("539,102 MMK"))
        org.junit.Assert.assertEquals("Main Warehouse", translator.translate("Main Warehouse"))
        org.junit.Assert.assertEquals("မင်္ဂလာပါ Way8", translator.translate("Hello, Way8"))
    }
    private val translator = TextTranslator(mapOf("Hello, {name}" to "မင်္ဂလာပါ၊ {name}",
        "Cash" to "ငွေသား", "posted" to "စာရင်းတင်ပြီး", "Step {step} of 4" to "အဆင့် {step}/၄"))
    @Test fun interpolatesWithoutChangingBusinessData() {
        assertEquals("မင်္ဂလာပါ၊ Test Shop", translator.translate("Hello, Test Shop"))
        assertEquals("ငွေသား: 1,000 MMK", translator.translate("Cash: 1,000 MMK"))
    }
    @Test fun translatesMultilineAndStatusLabels() {
        assertEquals("အဆင့် 2/၄ · ငွေသား\nစာရင်းတင်ပြီး", translator.translate("Step 2 of 4 · Cash\nPOSTED"))
        assertEquals("Customer's own note", translator.translate("Customer's own note"))
    }
}
