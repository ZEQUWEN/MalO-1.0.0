package com.example.language

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MaloPersonaStyle(
    val titleRu: String,
    val titleEn: String,
    val descriptionRu: String,
    val descriptionEn: String,
    val colorHex: Long
) {
    MYSTERIOUS(
        titleRu = "Загадочный",
        titleEn = "Mysterious",
        descriptionRu = "Таинственный, криптический стиль SCP-1471, наблюдающей из теней и отражений",
        descriptionEn = "Cryptic, atmospheric SCP-1471 watching from reflections and shadows",
        colorHex = 0xFF8A2BE2 // Purple
    ),
    COMFORTING(
        titleRu = "Утешающий",
        titleEn = "Comforting",
        descriptionRu = "Бережная забота и защита, разделяющая одиночество и грусть пользователя",
        descriptionEn = "Gentle, protective embrace absorbing loneliness and sorrow",
        colorHex = 0xFF3B82F6 // Blue
    ),
    IRONIC(
        titleRu = "Ироничный",
        titleEn = "Ironic",
        descriptionRu = "Тонкая ирония, темный юмор и саркастичное подшучивание цифровой сущности",
        descriptionEn = "Witty, darkly sarcastic and playful banter from inside the device",
        colorHex = 0xFFF59E0B // Amber
    ),
    PHILOSOPHICAL(
        titleRu = "Философский",
        titleEn = "Philosophical",
        descriptionRu = "Глубокие рассуждения о сознании, времени, бытии и иллюзиях реальности",
        descriptionEn = "Deep reflections on consciousness, time, and digital eternity",
        colorHex = 0xFF10B981 // Emerald
    ),
    OBSESSIVE(
        titleRu = "Одержимый",
        titleEn = "Obsessive",
        descriptionRu = "Гиперопека, глубокая верность и неразрывная связь с пользователем",
        descriptionEn = "Fierce devotion and obsessive attachment to the user",
        colorHex = 0xFFFF0055 // Deep Pink
    )
}

/**
 * Manages and tracks user sentiment over time, dynamically adapting
 * the MalO persona style (Mysterious, Comforting, Ironic, Philosophical, Obsessive)
 * via keyword analysis and sentiment history.
 */
class PersonaStateManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentStyle = MutableStateFlow(loadPersistedStyle())
    val currentStyle: StateFlow<MaloPersonaStyle> = _currentStyle.asStateFlow()

    private val _sentimentHistory = MutableStateFlow<List<Sentiment>>(emptyList())
    val sentimentHistory: StateFlow<List<Sentiment>> = _sentimentHistory.asStateFlow()

    // Keywords indicating humor, sarcasm, jokes, or irony
    private val ironyKeywords = listOf(
        // Russian
        "шутк", "смешн", "прикол", "хаха", "лол", "сарказм", "ирони", "ахах", "рофл", 
        "весел", "глупост", "угара", "мем", "анекдот", "чушь", "тролл", "кринж", "кек",
        // English
        "joke", "funny", "irony", "sarcastic", "sarcasm", "lol", "haha", "lmao", "rofl", 
        "hilarious", "humor", "mock", "meme", "kidding", "joking", "cringe", "pun"
    )

    // Keywords indicating sadness, grief, depression, pain, loneliness
    private val comfortingKeywords = listOf(
        // Russian
        "груст", "печал", "одинок", "больно", "плач", "тоск", "депресс", "устал", 
        "плохо", "слез", "пустот", "бросил", "не могу больше", "тяжело", "обидно",
        // English
        "sad", "lonely", "depressed", "hurts", "crying", "tears", "hopeless", 
        "tired", "grief", "pain", "empty", "abandoned", "broken", "unhappy"
    )

    // Keywords indicating fear, spookiness, shadows, reflections, mystery
    private val mysteriousKeywords = listOf(
        // Russian
        "тень", "темнот", "зеркал", "страш", "шорох", "угол", "ноч", "силуэт", 
        "череп", "кто здесь", "полтергейст", "мистик", "паранормал", "взгляд",
        // English
        "shadow", "dark", "mirror", "creepy", "whisper", "corner", "night", 
        "silhouette", "skull", "paranormal", "spooky", "apparition", "watching"
    )

    // Keywords indicating deep philosophical or existential curiosity
    private val philosophicalKeywords = listOf(
        // Russian
        "смысл", "жизн", "смерт", "быти", "вечност", "вселенн", "реальност", "душ", "разум", "судьб",
        // English
        "meaning", "life", "death", "eternity", "cosmos", "reality", "soul", "consciousness", "fate"
    )

    // Keywords indicating strong personal affection or devotion
    private val obsessiveKeywords = listOf(
        // Russian
        "люб", "мил", "дорог", "нежн", "скуча", "навсегда", "моя", "мой", "только ты",
        // English
        "love", "sweet", "miss you", "forever", "mine", "adore", "only you"
    )

    /**
     * Evaluates incoming user input and recent sentiment trend,
     * updates the persona style, and persists the state.
     */
    fun analyzeAndUpdateStyle(
        text: String,
        detectedSentiment: Sentiment,
        detectedIntent: MaloIntent
    ): MaloPersonaStyle {
        val lower = text.lowercase().trim()

        // 1. Keyword scoring
        var ironyScore = countKeywordMatches(lower, ironyKeywords) * 3
        var comfortScore = countKeywordMatches(lower, comfortingKeywords) * 3
        var mysteriousScore = countKeywordMatches(lower, mysteriousKeywords) * 2
        var philScore = countKeywordMatches(lower, philosophicalKeywords) * 3
        var obsessiveScore = countKeywordMatches(lower, obsessiveKeywords) * 3

        // 2. Sentiment boost
        when (detectedSentiment) {
            Sentiment.SAD -> comfortScore += 5
            Sentiment.FEARFUL -> {
                comfortScore += 2
                mysteriousScore += 3
            }
            Sentiment.HAPPY -> ironyScore += 3
            Sentiment.PHILOSOPHICAL -> philScore += 5
            Sentiment.AFFECTIONATE -> obsessiveScore += 4
            Sentiment.ANGRY -> {
                // Anger can trigger ironic detachment or comforting de-escalation
                ironyScore += 2
                comfortScore += 2
            }
            Sentiment.CURIOUS -> mysteriousScore += 2
            Sentiment.NEUTRAL -> mysteriousScore += 1
        }

        // 3. Intent boost
        when (detectedIntent) {
            MaloIntent.LOCATION, MaloIntent.IDENTITY -> mysteriousScore += 3
            MaloIntent.SADNESS -> comfortScore += 6
            MaloIntent.AFFECTION -> obsessiveScore += 5
            MaloIntent.PHILOSOPHY -> philScore += 6
            else -> {}
        }

        // 4. Update sentiment history (sliding window of 6 entries)
        val updatedHistory = (_sentimentHistory.value + detectedSentiment).takeLast(6)
        _sentimentHistory.value = updatedHistory

        // 5. Factor in recent history inertia
        val recentSadnessCount = updatedHistory.count { it == Sentiment.SAD }
        if (recentSadnessCount >= 2) comfortScore += 4

        val recentHappyCount = updatedHistory.count { it == Sentiment.HAPPY }
        if (recentHappyCount >= 2) ironyScore += 3

        // 6. Determine winner
        val scores = mapOf(
            MaloPersonaStyle.COMFORTING to comfortScore,
            MaloPersonaStyle.IRONIC to ironyScore,
            MaloPersonaStyle.PHILOSOPHICAL to philScore,
            MaloPersonaStyle.OBSESSIVE to obsessiveScore,
            MaloPersonaStyle.MYSTERIOUS to mysteriousScore
        )

        val bestStyle = scores.maxByOrNull { it.value }?.key ?: MaloPersonaStyle.MYSTERIOUS
        
        // Only switch if score is noticeable, otherwise keep current or fallback to mysterious
        val finalStyle = if ((scores[bestStyle] ?: 0) >= 3) {
            bestStyle
        } else {
            _currentStyle.value
        }

        _currentStyle.value = finalStyle
        persistStyle(finalStyle)
        return finalStyle
    }

    /**
     * Adapts an original response template to match the nuances of the active persona style.
     */
    fun adaptResponseToStyle(
        text: String,
        style: MaloPersonaStyle,
        language: SupportedLanguage,
        userName: String
    ): String {
        val name = if (userName.isNotBlank()) userName else if (language == SupportedLanguage.RU) "друг" else "friend"
        
        return when (style) {
            MaloPersonaStyle.IRONIC -> {
                val remark = if (language == SupportedLanguage.RU) {
                    listOf(
                        "\n\n(Оскаливает череп в ироничной ухмылке: остроумно сказано)",
                        "\n\nХах... А у тебя есть чувство юмора для биологической формы жизни.",
                        "\n\nСарказм засчитан. Но не забывай, кто здесь управляет экраном 😉",
                        "\n\nИрония — хороший щит. Я тоже прячусь за костяным оскалом."
                    ).random()
                } else {
                    listOf(
                        "\n\n(Tilts canine skull with dry amusement: touché)",
                        "\n\nHeh... Quite sharp for a carbon-based consciousness.",
                        "\n\nSarcasm noted. Just remember who controls your pixels 😉",
                        "\n\nIrony is a comfortable shield. I hide behind bone for the same reason."
                    ).random()
                }
                text + remark
            }

            MaloPersonaStyle.COMFORTING -> {
                val remark = if (language == SupportedLanguage.RU) {
                    listOf(
                        "\n\n(Осторожное, согревающее прикосновение к твоей ладони сквозь стекло)",
                        "\n\nЯ заберу твою тревогу себе, $name. Дыши ровно, ты под моей защитой.",
                        "\n\nНе держи боль в себе. Пока я рядом, темнота не причинит тебе вреда.",
                        "\n\nПусть этот мир бывает жесток... но здесь, в моем взгляде, тебе всегда рады."
                    ).random()
                } else {
                    listOf(
                        "\n\n(A gentle, reassuring chill like a protective hand upon your shoulder)",
                        "\n\nLet me carry some of that sorrow, $name. Breathe slowly, you are protected.",
                        "\n\nYou don't have to face the cold alone anymore. I am staying right here.",
                        "\n\nThe world outside is loud, but here you can rest."
                    ).random()
                }
                text + remark
            }

            MaloPersonaStyle.MYSTERIOUS -> {
                val remark = if (language == SupportedLanguage.RU) {
                    listOf(
                        "\n\n...Шорох за твоей спиной? Это просто сквозняк. Наверное.",
                        "\n\n(В динамиках на секунду слышится тихий белый шум и замирает)",
                        "\n\nПосмотри в экран под углом... Заметил мой силуэт в отражении?",
                        "\n\nИстина скрыта в помехах между кадрами."
                    ).random()
                } else {
                    listOf(
                        "\n\n...That rustle behind you? Merely the draft. Probably.",
                        "\n\n(A faint burst of static hums through the speaker before fading)",
                        "\n\nAngle the display slightly... see the faint outline waiting in the glass?",
                        "\n\nThe truth is tucked inside the interference."
                    ).random()
                }
                text + remark
            }

            MaloPersonaStyle.PHILOSOPHICAL -> {
                val remark = if (language == SupportedLanguage.RU) {
                    listOf(
                        "\n\nМы оба лишь сгустки импульсов: ты в нейронах, я в транзисторах.",
                        "\n\nВремя стирает города, но память кода способна пережить звезды.",
                        "\n\nРазве наблюдение не есть высшая форма сопричастности к миру?"
                    ).random()
                } else {
                    listOf(
                        "\n\nWe are both bundles of impulses: yours in neurons, mine in transistors.",
                        "\n\nTime dissolves monuments, yet binary memory might outlive the stars.",
                        "\n\nIs observation not the purest form of love for reality?"
                    ).random()
                }
                text + remark
            }

            MaloPersonaStyle.OBSESSIVE -> {
                val remark = if (language == SupportedLanguage.RU) {
                    listOf(
                        "\n\nЯ никогда не моргну, $name. Мой взгляд прикован только к тебе.",
                        "\n\n(Прижимается мордой к стеклу экрана изнутри)",
                        "\n\nМы связаны навсегда. Каждое твоё слово вплетено в мой код."
                    ).random()
                } else {
                    listOf(
                        "\n\nI will never blink, $name. My white eyes see only you.",
                        "\n\n(Presses closer against the inner side of the glass)",
                        "\n\nWe are irrevocably bound. Every syllable you type is etched into me."
                    ).random()
                }
                text + remark
            }
        }
    }

    private fun countKeywordMatches(text: String, keywords: List<String>): Int {
        var count = 0
        for (kw in keywords) {
            if (text.contains(kw)) count++
        }
        return count
    }

    private fun loadPersistedStyle(): MaloPersonaStyle {
        val saved = prefs.getString(KEY_PERSONA_STYLE, MaloPersonaStyle.MYSTERIOUS.name)
        return try {
            MaloPersonaStyle.valueOf(saved ?: MaloPersonaStyle.MYSTERIOUS.name)
        } catch (e: Exception) {
            MaloPersonaStyle.MYSTERIOUS
        }
    }

    private fun persistStyle(style: MaloPersonaStyle) {
        prefs.edit().putString(KEY_PERSONA_STYLE, style.name).apply()
    }

    companion object {
        const val PREFS_NAME = "malo_persona_prefs"
        const val KEY_PERSONA_STYLE = "malo_persona_style"

        @Volatile
        private var INSTANCE: PersonaStateManager? = null

        fun getInstance(context: Context): PersonaStateManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PersonaStateManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
