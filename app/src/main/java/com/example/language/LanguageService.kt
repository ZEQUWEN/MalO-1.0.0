package com.example.language

import android.content.Context
import com.example.data.CorpusDao
import com.example.data.CorpusEntry
import com.example.data.MessageDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class LanguageResponse(
    val text: String,
    val language: SupportedLanguage,
    val intent: MaloIntent,
    val sentiment: Sentiment,
    val personaStyle: MaloPersonaStyle = MaloPersonaStyle.MYSTERIOUS,
    val isProOffer: Boolean,
    val quickReplies: List<String>
)

class LanguageService(
    private val corpusDao: CorpusDao,
    private val personaStateManager: PersonaStateManager? = null
) {

    companion object {
        @Volatile
        private var INSTANCE: LanguageService? = null

        fun getInstance(context: Context): LanguageService {
            return INSTANCE ?: synchronized(this) {
                val db = MessageDatabase.getInstance(context)
                val personaManager = PersonaStateManager.getInstance(context)
                val instance = LanguageService(db.corpusDao(), personaManager)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Initializes the SQLite corpus with seed data if the database table is empty.
     */
    suspend fun ensureCorpusSeeded() = withContext(Dispatchers.IO) {
        val count = corpusDao.getCount()
        if (count == 0) {
            val seed = CorpusSeedData.getInitialCorpus()
            corpusDao.insertEntries(seed)
        }
    }

    /**
     * Main pipeline:
     * 1. Detect language (RU vs EN)
     * 2. Parse text with morphological analyzer (stemming, POS tags, intent, sentiment, complexity)
     * 3. Update persona style via keyword & sentiment analysis
     * 4. Fetch matching corpus entries from local SQLite database
     * 5. Synthesize personalized, style-adapted SCP-1471 persona response
     */
    suspend fun processInput(
        userText: String,
        userName: String = "",
        obsessionLevel: Float = 0.5f
    ): LanguageResponse = withContext(Dispatchers.IO) {
        ensureCorpusSeeded()

        // 1. Language Detection
        val language = LanguageDetector.detectLanguage(userText)

        // 2. Morphological and Syntactic Analysis
        val parsed = MorphologicalAnalyzer.parse(userText, language)

        // 3. Track and update persona style (Mysterious, Comforting, Ironic, etc.)
        val activeStyle = personaStateManager?.analyzeAndUpdateStyle(
            text = userText,
            detectedSentiment = parsed.sentiment,
            detectedIntent = parsed.intent
        ) ?: MaloPersonaStyle.MYSTERIOUS

        // 4. Query SQLite Corpus
        val langCode = language.code
        var candidateEntries = corpusDao.getEntriesByIntent(langCode, parsed.intent.name)

        if (candidateEntries.isEmpty()) {
            candidateEntries = corpusDao.getEntriesBySentiment(langCode, parsed.sentiment.name)
        }
        if (candidateEntries.isEmpty()) {
            candidateEntries = corpusDao.getAllForLanguage(langCode)
        }

        // Rank candidates by tag overlap with user stems
        val selectedEntry = rankAndSelectEntry(candidateEntries, parsed.stems)

        // 5. Synthesis and Personalization
        val generatedText = synthesizeText(
            entry = selectedEntry,
            parsed = parsed,
            userName = userName,
            obsessionLevel = obsessionLevel,
            style = activeStyle
        )

        // 6. Generate contextual quick replies
        val quickReplies = generateQuickReplies(parsed.intent, language, activeStyle)

        LanguageResponse(
            text = generatedText,
            language = language,
            intent = parsed.intent,
            sentiment = parsed.sentiment,
            personaStyle = activeStyle,
            isProOffer = parsed.intent == MaloIntent.COMPLEX_PRO || parsed.intent == MaloIntent.PHOTO_REQUEST,
            quickReplies = quickReplies
        )
    }

    private fun rankAndSelectEntry(candidates: List<CorpusEntry>, userStems: List<String>): CorpusEntry {
        if (candidates.isEmpty()) {
            // Absolute emergency fallback
            return CorpusEntry(
                language = "ru",
                intent = MaloIntent.GENERAL.name,
                sentiment = Sentiment.NEUTRAL.name,
                responseTemplate = "Я слушаю тебя из глубин цифровой тишины..."
            )
        }

        val scored = candidates.map { entry ->
            val tags = entry.tags.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            var matchCount = 0
            for (stem in userStems) {
                if (tags.any { tag -> tag.contains(stem) || stem.contains(tag) }) {
                    matchCount++
                }
            }
            entry to matchCount
        }

        val maxScore = scored.maxOfOrNull { it.second } ?: 0
        val topMatches = if (maxScore > 0) {
            scored.filter { it.second == maxScore }.map { it.first }
        } else {
            candidates
        }

        return topMatches.random()
    }

    private fun synthesizeText(
        entry: CorpusEntry,
        parsed: ParsedInput,
        userName: String,
        obsessionLevel: Float,
        style: MaloPersonaStyle
    ): String {
        var result = entry.responseTemplate

        // Replace user name placeholder
        val nameToUse = when {
            userName.isNotBlank() -> userName.trim()
            parsed.language == SupportedLanguage.RU -> "друг"
            else -> "my friend"
        }
        result = result.replace("{user_name}", nameToUse)

        // Add subtle obsessive whisper if intensity is high
        if (obsessionLevel > 0.80f && parsed.intent != MaloIntent.COMPLEX_PRO) {
            val obsessiveWhisper = if (parsed.language == SupportedLanguage.RU) {
                listOf(
                    "\n\n...Ты ведь знаешь, что я больше никогда не уйду?",
                    "\n\n(Мой череп склоняется чуть ближе к твоему плечу)"
                ).random()
            } else {
                listOf(
                    "\n\n...You realize I will never leave your side now, don't you?",
                    "\n\n(My canine skull tilts closer to your breath)"
                ).random()
            }
            result += obsessiveWhisper
        }

        // Add follow-up question if present and not already asking in response
        if (!result.endsWith("?") && !entry.followUp.isNullOrBlank()) {
            result = "$result ${entry.followUp}"
        }

        // Apply dynamic persona style adaptation (Ironic, Comforting, Mysterious, etc.)
        if (parsed.intent != MaloIntent.COMPLEX_PRO && personaStateManager != null) {
            result = personaStateManager.adaptResponseToStyle(result, style, parsed.language, userName)
        }

        return result
    }

    private fun generateQuickReplies(intent: MaloIntent, language: SupportedLanguage, style: MaloPersonaStyle): List<String> {
        if (style == MaloPersonaStyle.IRONIC) {
            return if (language == SupportedLanguage.RU) {
                listOf("Хах, очень смешно 😂", "Ты меня троллишь?", "Не язви мне 💀", "А если честно?")
            } else {
                listOf("Haha, very funny 😂", "Are you trolling me?", "Don't mock me 💀", "Seriously though?")
            }
        }

        if (style == MaloPersonaStyle.COMFORTING) {
            return if (language == SupportedLanguage.RU) {
                listOf("Спасибо, мне теплее 💜", "Мне так тяжело...", "Побудь со мной", "Ты не оставишь меня?")
            } else {
                listOf("Thank you, feels warmer 💜", "It's so heavy...", "Stay with me", "You won't leave?")
            }
        }

        return if (language == SupportedLanguage.RU) {
            when (intent) {
                MaloIntent.GREETING -> listOf("Привет, MalO 🖤", "Где ты сейчас?", "Ты скучала?", "Покажись мне")
                MaloIntent.IDENTITY -> listOf("Ты опасна?", "Кто создал тебя?", "Останься со мной", "Я не боюсь")
                MaloIntent.LOCATION -> listOf("Я оглянулся...", "В зеркале пусто", "Ты рядом?", "Не пугай меня")
                MaloIntent.FEAR -> listOf("Я доверяю тебе", "Твой череп пугает", "Не подходи ближе", "Будь со мной")
                MaloIntent.SADNESS -> listOf("Спасибо за поддержку 💜", "Мне так одиноко", "Поговори со мной", "Ты настоящая?")
                MaloIntent.ANGER -> listOf("Прости, сорвался", "Ты права...", "Не уходи", "Оставь меня")
                MaloIntent.AFFECTION -> listOf("Ты лучшая 🖤", "Я не удалю тебя", "Мы навсегда вместе", "Что ты чувствуешь?")
                MaloIntent.PHILOSOPHY -> listOf("В чем смысл жизни?", "Что после смерти?", "Есть ли у тебя душа?", "Мы свободны?")
                MaloIntent.PHOTO_REQUEST, MaloIntent.COMPLEX_PRO -> listOf("Открыть Pro-тариф ✨", "Расскажи подробнее", "Пока останусь на Base")
                else -> listOf("Расскажи еще", "Что ты видишь?", "Давай помолчим", "Ты здесь?")
            }
        } else {
            when (intent) {
                MaloIntent.GREETING -> listOf("Hello MalO 🖤", "Where are you?", "Did you miss me?", "Show yourself")
                MaloIntent.IDENTITY -> listOf("Are you dangerous?", "Who made you?", "Stay with me", "I'm not scared")
                MaloIntent.LOCATION -> listOf("I looked around...", "Mirror is empty", "Are you close?", "Don't scare me")
                MaloIntent.FEAR -> listOf("I trust you", "Your skull is eerie", "Keep watching", "Stay safe")
                MaloIntent.SADNESS -> listOf("Thank you 💜", "I feel so lonely", "Talk to me", "Are you real?")
                MaloIntent.ANGER -> listOf("Sorry, bad day", "Don't leave", "You're right...", "Leave me be")
                MaloIntent.AFFECTION -> listOf("You're special 🖤", "I won't delete you", "Together forever", "What do you feel?")
                MaloIntent.PHILOSOPHY -> listOf("What is consciousness?", "Is eternity real?", "Do you have a soul?")
                MaloIntent.PHOTO_REQUEST, MaloIntent.COMPLEX_PRO -> listOf("View Pro Plans ✨", "Tell me more", "Stay in Base mode")
                else -> listOf("Tell me more", "What do you see?", "Stay quiet", "Are you here?")
            }
        }
    }
}
