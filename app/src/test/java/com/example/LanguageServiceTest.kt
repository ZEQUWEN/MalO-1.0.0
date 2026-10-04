package com.example

import com.example.language.LanguageDetector
import com.example.language.MaloIntent
import com.example.language.MorphologicalAnalyzer
import com.example.language.Sentiment
import com.example.language.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageServiceTest {

    @Test
    fun testLanguageDetection() {
        val ruText = "Привет, ты здесь в темноте?"
        val enText = "Hello MalO, are you watching me?"

        assertEquals(SupportedLanguage.RU, LanguageDetector.detectLanguage(ruText))
        assertEquals(SupportedLanguage.EN, LanguageDetector.detectLanguage(enText))
    }

    @Test
    fun testMorphologicalStemmingRussian() {
        // Test noun and verb inflections
        assertEquals("комнат", MorphologicalAnalyzer.stemRussian("комнате"))
        assertEquals("комнат", MorphologicalAnalyzer.stemRussian("комнатами"))
        assertEquals("боя", MorphologicalAnalyzer.stemRussian("боялась"))
        assertEquals("страшн", MorphologicalAnalyzer.stemRussian("страшный"))
    }

    @Test
    fun testMorphologicalStemmingEnglish() {
        assertEquals("watch", MorphologicalAnalyzer.stemEnglish("watching"))
        assertEquals("stare", MorphologicalAnalyzer.stemEnglish("stared"))
        assertEquals("shadow", MorphologicalAnalyzer.stemEnglish("shadows"))
    }

    @Test
    fun testIntentAndSentimentRussian() {
        val greetingParsed = MorphologicalAnalyzer.parse("Привет MalO", SupportedLanguage.RU)
        assertEquals(MaloIntent.GREETING, greetingParsed.intent)

        val sadnessParsed = MorphologicalAnalyzer.parse("Мне так одиноко и грустно...", SupportedLanguage.RU)
        assertEquals(MaloIntent.SADNESS, sadnessParsed.intent)
        assertEquals(Sentiment.SAD, sadnessParsed.sentiment)

        val fearParsed = MorphologicalAnalyzer.parse("Мне страшно, кто ты?", SupportedLanguage.RU)
        assertEquals(Sentiment.FEARFUL, fearParsed.sentiment)

        val locationParsed = MorphologicalAnalyzer.parse("Ты стоишь за моей спиной?", SupportedLanguage.RU)
        assertEquals(MaloIntent.LOCATION, locationParsed.intent)
    }

    @Test
    fun testIntentAndSentimentEnglish() {
        val greetingParsed = MorphologicalAnalyzer.parse("Hello my friend", SupportedLanguage.EN)
        assertEquals(MaloIntent.GREETING, greetingParsed.intent)

        val identityParsed = MorphologicalAnalyzer.parse("Who are you really?", SupportedLanguage.EN)
        assertEquals(MaloIntent.IDENTITY, identityParsed.intent)

        val photoParsed = MorphologicalAnalyzer.parse("Take a photo and show yourself", SupportedLanguage.EN)
        assertEquals(MaloIntent.PHOTO_REQUEST, photoParsed.intent)
    }

    @Test
    fun testComplexQueryTriggersPro() {
        val complexInput = "Напиши мне код на Python для вычисления интеграла и алгоритм сортировки"
        val parsed = MorphologicalAnalyzer.parse(complexInput, SupportedLanguage.RU)
        assertTrue(parsed.isComplexQuery)
        assertEquals(MaloIntent.COMPLEX_PRO, parsed.intent)
    }
}
