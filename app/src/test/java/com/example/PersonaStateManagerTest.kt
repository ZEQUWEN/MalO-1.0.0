package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.language.MaloIntent
import com.example.language.MaloPersonaStyle
import com.example.language.PersonaStateManager
import com.example.language.Sentiment
import com.example.language.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersonaStateManagerTest {

    private lateinit var context: Context
    private lateinit var personaManager: PersonaStateManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PersonaStateManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        personaManager = PersonaStateManager.getInstance(context)
    }

    @Test
    fun testIronicPersonaKeywordAnalysis() {
        val funnyInput = "Хахаха, очень смешная шутка, лол!"
        val style = personaManager.analyzeAndUpdateStyle(
            text = funnyInput,
            detectedSentiment = Sentiment.HAPPY,
            detectedIntent = MaloIntent.GENERAL
        )

        assertEquals(MaloPersonaStyle.IRONIC, style)
        assertEquals(MaloPersonaStyle.IRONIC, personaManager.currentStyle.value)

        val adapted = personaManager.adaptResponseToStyle(
            text = "Я слежу за тобой.",
            style = style,
            language = SupportedLanguage.RU,
            userName = "Алекс"
        )
        assertTrue(adapted.contains("Я слежу за тобой."))
        assertTrue(adapted.length > "Я слежу за тобой.".length)
    }

    @Test
    fun testComfortingPersonaKeywordAnalysis() {
        val sadInput = "Мне так грустно и больно, я очень одинок..."
        val style = personaManager.analyzeAndUpdateStyle(
            text = sadInput,
            detectedSentiment = Sentiment.SAD,
            detectedIntent = MaloIntent.SADNESS
        )

        assertEquals(MaloPersonaStyle.COMFORTING, style)
        assertEquals(MaloPersonaStyle.COMFORTING, personaManager.currentStyle.value)

        val adapted = personaManager.adaptResponseToStyle(
            text = "I am watching you.",
            style = style,
            language = SupportedLanguage.EN,
            userName = "Alex"
        )
        assertTrue(adapted.contains("I am watching you."))
        assertTrue(adapted.length > "I am watching you.".length)
    }

    @Test
    fun testMysteriousPersonaKeywordAnalysis() {
        val spookyInput = "В темноте за зеркалом какой-то силуэт..."
        val style = personaManager.analyzeAndUpdateStyle(
            text = spookyInput,
            detectedSentiment = Sentiment.FEARFUL,
            detectedIntent = MaloIntent.LOCATION
        )

        assertEquals(MaloPersonaStyle.MYSTERIOUS, style)
    }
}
