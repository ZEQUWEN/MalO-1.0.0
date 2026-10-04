package com.example.language

enum class MaloIntent {
    GREETING,
    IDENTITY,
    LOCATION,
    FEAR,
    SADNESS,
    ANGER,
    AFFECTION,
    PHILOSOPHY,
    CURIOSITY,
    PHOTO_REQUEST,
    GOODBYE,
    COMPLEX_PRO,
    GENERAL
}

enum class Sentiment {
    NEUTRAL,
    SAD,
    ANGRY,
    FEARFUL,
    AFFECTIONATE,
    CURIOUS,
    HAPPY,
    PHILOSOPHICAL
}

data class ParsedInput(
    val rawText: String,
    val language: SupportedLanguage,
    val tokens: List<String>,
    val stems: List<String>,
    val posTags: Map<String, String>,
    val intent: MaloIntent,
    val sentiment: Sentiment,
    val extractedSubjects: List<String>,
    val isComplexQuery: Boolean
)

object MorphologicalAnalyzer {

    // Common Russian interrogatives and modals
    private val RU_QUESTION_WORDS = setOf("кто", "что", "где", "куда", "откуда", "когда", "почему", "зачем", "как", "какой", "какая", "какое", "какие", "чей", "сколько")
    private val EN_QUESTION_WORDS = setOf("who", "what", "where", "when", "why", "how", "which", "whose", "whom")

    // Russian pronouns
    private val RU_PRONOUNS_1ST = setOf("я", "меня", "мне", "мной", "мною", "мой", "моя", "мое", "мои")
    private val RU_PRONOUNS_2ND = setOf("ты", "тебя", "тебе", "тобой", "тобою", "твой", "твоя", "твое", "твои")

    // Russian sentiment roots/stems
    private val RU_SAD_STEMS = listOf("груст", "печал", "одинок", "слез", "плач", "тоск", "бол", "уны", "депресс", "плох", "тяжел")
    private val RU_ANGER_STEMS = listOf("зл", "ярост", "бес", "ненавид", "уйди", "отстан", "замолч", "убь", "мраз", "тварь", "бесит")
    private val RU_FEAR_STEMS = listOf("страш", "боя", "боит", "пуга", "жуть", "кошмар", "ужас", "жутк", "дрож")
    private val RU_AFFECTION_STEMS = listOf("люб", "мил", "дорог", "нежн", "обним", "родн", "сердц", "красив", "нравиш", "скуча")
    private val RU_HAPPY_STEMS = listOf("радост", "счаст", "весел", "смех", "хаха", "ура", "отличн", "прекрасн", "супер")
    private val RU_PHILOSOPHY_STEMS = listOf("смысл", "жизн", "смерт", "быти", "вечност", "вселенн", "существован", "бог", "реальност", "душ", "разум")

    // English sentiment roots/stems
    private val EN_SAD_STEMS = listOf("sad", "loneli", "lone", "depress", "sorrow", "cri", "cry", "tear", "hurt", "pain", "hopeless")
    private val EN_ANGER_STEMS = listOf("angri", "angry", "hate", "mad", "shut", "kill", "leave", "stop", "furious", "rage", "damn")
    private val EN_FEAR_STEMS = listOf("scared", "scari", "fear", "afraid", "horror", "creep", "spook", "terrifi", "fright", "dread")
    private val EN_AFFECTION_STEMS = listOf("love", "sweet", "dear", "hug", "kiss", "beauti", "miss", "care", "ador", "fond")
    private val EN_HAPPY_STEMS = listOf("happi", "happy", "joy", "glad", "great", "wonder", "awesom", "fun", "laugh", "smile", "lol")
    private val EN_PHILOSOPHY_STEMS = listOf("mean", "life", "death", "etern", "exist", "realiti", "soul", "mind", "truth", "purpos", "cosmo")

    fun parse(text: String, language: SupportedLanguage): ParsedInput {
        val tokens = tokenize(text)
        val stems = tokens.map { token ->
            if (language == SupportedLanguage.RU) stemRussian(token) else stemEnglish(token)
        }

        val posTags = mutableMapOf<String, String>()
        for (i in tokens.indices) {
            val token = tokens[i]
            val stem = stems[i]
            posTags[token] = tagPartOfSpeech(token, stem, language)
        }

        val subjects = extractSubjects(tokens, language)
        val isComplex = isComplexInput(text, tokens)
        val sentiment = detectSentiment(tokens, stems, language)
        val intent = detectIntent(text, tokens, stems, sentiment, isComplex, language)

        return ParsedInput(
            rawText = text,
            language = language,
            tokens = tokens,
            stems = stems,
            posTags = posTags,
            intent = intent,
            sentiment = sentiment,
            extractedSubjects = subjects,
            isComplexQuery = isComplex
        )
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zа-яё0-9\\s'-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    /**
     * Russian Porter-like stemmer stripping grammatical endings (inflections, conjugations, declensions).
     */
    fun stemRussian(word: String): String {
        var w = word
        if (w.length <= 3) return w

        // Remove reflexive suffixes
        if (w.endsWith("ся") || w.endsWith("сь")) {
            w = w.dropLast(2)
        }

        // Adjectival / Participle endings
        val adjSuffixes = listOf("ыми", "ими", "ого", "его", "ому", "ему", "ых", "их", "ую", "юю", "ая", "яя", "ое", "ее", "ый", "ий", "ой", "ым", "им")
        for (suffix in adjSuffixes) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 3) {
                w = w.dropLast(suffix.length)
                break
            }
        }

        // Verbal endings
        val verbSuffixes = listOf("ейте", "уйте", "ите", "ешь", "ете", "ут", "ют", "ат", "ят", "ила", "ыла", "ела", "ить", "ыть", "еть", "ал", "ял", "ил", "ла", "ло", "ли", "ть", "ти")
        for (suffix in verbSuffixes) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 3) {
                w = w.dropLast(suffix.length)
                break
            }
        }

        // Noun endings
        val nounSuffixes = listOf("ами", "ями", "ей", "ов", "ев", "ом", "ем", "ам", "ям", "ах", "ях", "а", "я", "у", "ю", "е", "и", "ы", "о")
        for (suffix in nounSuffixes) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 3) {
                w = w.dropLast(suffix.length)
                break
            }
        }

        return w
    }

    /**
     * English Porter-like stemmer stripping common inflectional suffixes.
     */
    fun stemEnglish(word: String): String {
        var w = word
        if (w.length <= 3) return w

        // Plural / 3rd person singular
        if (w.endsWith("sses")) w = w.dropLast(2)
        else if (w.endsWith("ies")) w = w.dropLast(3) + "i"
        else if (w.endsWith("ss")) { /* do nothing */ }
        else if (w.endsWith("s")) w = w.dropLast(1)

        // Past tense & progressive
        if (w.endsWith("eed")) {
            if (w.length > 4) w = w.dropLast(1)
        } else if (w.endsWith("ed") && w.length > 4) {
            w = w.dropLast(2)
        } else if (w.endsWith("ing") && w.length > 5) {
            w = w.dropLast(3)
        }

        // Adverbial / Adjectival
        if (w.endsWith("ly") && w.length > 4) w = w.dropLast(2)
        if (w.endsWith("ful") && w.length > 5) w = w.dropLast(3)
        if (w.endsWith("ness") && w.length > 6) w = w.dropLast(4)
        if (w.endsWith("tion") && w.length > 6) w = w.dropLast(3)

        return w
    }

    private fun tagPartOfSpeech(token: String, stem: String, language: SupportedLanguage): String {
        if (language == SupportedLanguage.RU) {
            return when {
                RU_QUESTION_WORDS.contains(token) -> "QUESTION_WORD"
                RU_PRONOUNS_1ST.contains(token) -> "PRONOUN_1ST"
                RU_PRONOUNS_2ND.contains(token) -> "PRONOUN_2ND"
                token in setOf("не", "нет", "никогда", "нигде") -> "NEGATION"
                token.endsWith("ть") || token.endsWith("ся") || token.endsWith("ли") || token.endsWith("ла") || token.endsWith("ет") -> "VERB"
                token.endsWith("ый") || token.endsWith("ий") || token.endsWith("ая") || token.endsWith("ое") -> "ADJECTIVE"
                else -> "NOUN_OR_OTHER"
            }
        } else {
            return when {
                EN_QUESTION_WORDS.contains(token) -> "QUESTION_WORD"
                token in setOf("i", "me", "my", "mine", "myself") -> "PRONOUN_1ST"
                token in setOf("you", "your", "yours", "yourself") -> "PRONOUN_2ND"
                token in setOf("not", "never", "no", "don't", "can't", "won't") -> "NEGATION"
                token in setOf("is", "are", "am", "was", "were", "be", "do", "have", "can", "will") -> "AUX_VERB"
                token.endsWith("ing") || token.endsWith("ed") -> "VERB"
                token.endsWith("ly") -> "ADVERB"
                else -> "NOUN_OR_OTHER"
            }
        }
    }

    private fun extractSubjects(tokens: List<String>, language: SupportedLanguage): List<String> {
        val subjects = mutableListOf<String>()
        val selfRefs = if (language == SupportedLanguage.RU) RU_PRONOUNS_1ST else setOf("i", "me", "my")
        val maloRefs = if (language == SupportedLanguage.RU) 
            setOf("ты", "тебя", "тебе", "тобой", "мало", "malo", "1471")
            else setOf("you", "your", "malo", "1471", "entity")

        if (tokens.any { selfRefs.contains(it) }) subjects.add("USER")
        if (tokens.any { maloRefs.contains(it) }) subjects.add("MALO")
        
        val worldEn = setOf("room", "dark", "mirror", "phone", "screen", "night", "photo", "corner")
        val worldRu = setOf("комнат", "темнот", "зеркал", "телефон", "экран", "ноч", "фото", "угол", "окн")
        
        for (t in tokens) {
            if (language == SupportedLanguage.RU) {
                if (worldRu.any { t.startsWith(it) }) subjects.add(t)
            } else {
                if (worldEn.contains(t)) subjects.add(t)
            }
        }
        return subjects
    }

    private fun detectSentiment(tokens: List<String>, stems: List<String>, language: SupportedLanguage): Sentiment {
        val sadList = if (language == SupportedLanguage.RU) RU_SAD_STEMS else EN_SAD_STEMS
        val angerList = if (language == SupportedLanguage.RU) RU_ANGER_STEMS else EN_ANGER_STEMS
        val fearList = if (language == SupportedLanguage.RU) RU_FEAR_STEMS else EN_FEAR_STEMS
        val affectionList = if (language == SupportedLanguage.RU) RU_AFFECTION_STEMS else EN_AFFECTION_STEMS
        val happyList = if (language == SupportedLanguage.RU) RU_HAPPY_STEMS else EN_HAPPY_STEMS
        val philList = if (language == SupportedLanguage.RU) RU_PHILOSOPHY_STEMS else EN_PHILOSOPHY_STEMS

        for (s in stems) {
            if (fearList.any { s.startsWith(it) || it.startsWith(s) }) return Sentiment.FEARFUL
            if (angerList.any { s.startsWith(it) || it.startsWith(s) }) return Sentiment.ANGRY
            if (sadList.any { s.startsWith(it) || it.startsWith(s) }) return Sentiment.SAD
            if (affectionList.any { s.startsWith(it) || it.startsWith(s) }) return Sentiment.AFFECTIONATE
            if (happyList.any { s.startsWith(it) || it.startsWith(s) }) return Sentiment.HAPPY
            if (philList.any { s.startsWith(it) || it.startsWith(s) }) return Sentiment.PHILOSOPHICAL
        }

        // Check if there are interrogatives
        val questionWords = if (language == SupportedLanguage.RU) RU_QUESTION_WORDS else EN_QUESTION_WORDS
        if (tokens.any { questionWords.contains(it) }) return Sentiment.CURIOUS

        return Sentiment.NEUTRAL
    }

    private fun detectIntent(
        rawText: String,
        tokens: List<String>,
        stems: List<String>,
        sentiment: Sentiment,
        isComplex: Boolean,
        language: SupportedLanguage
    ): MaloIntent {
        val lower = rawText.lowercase().trim()

        // 1. Complex query fallback to Pro
        if (isComplex) return MaloIntent.COMPLEX_PRO

        // 2. Photo request
        val photoKeywords = if (language == SupportedLanguage.RU) 
            listOf("фото", "картинк", "снимок", "покажись", "сфотографируй", "селфи", "изображени")
            else listOf("photo", "picture", "pic", "image", "selfie", "show yourself", "camera")
        if (photoKeywords.any { lower.contains(it) }) return MaloIntent.PHOTO_REQUEST

        // 3. Who are you (identity)
        val identityKeywords = if (language == SupportedLanguage.RU)
            listOf("кто ты", "что ты", "откуда ты", "как тебя зовут", "твое имя", "ты кто", "расскажи о себе", "что такое malo", "что такое мало", "scp-1471", "1471")
            else listOf("who are you", "what are you", "what is your name", "tell me about yourself", "what is malo", "scp 1471", "scp-1471")
        if (identityKeywords.any { lower.contains(it) }) return MaloIntent.IDENTITY

        // 4. Where are you (location & presence)
        val locationKeywords = if (language == SupportedLanguage.RU)
            listOf("где ты", "ты где", "куда ты", "ты здесь", "ты рядом", "за спиной", "в комнате")
            else listOf("where are you", "are you here", "behind me", "in my room", "where do you live")
        if (locationKeywords.any { lower.contains(it) }) return MaloIntent.LOCATION

        // 5. Greetings
        val greetingKeywords = if (language == SupportedLanguage.RU)
            listOf("привет", "здравствуй", "добрый день", "добрый вечер", "доброе утро", "салют", "хай", "ку")
            else listOf("hello", "hi", "hey", "greetings", "good morning", "good evening", "howdy")
        if (greetingKeywords.any { lower.contains(it) } && tokens.size <= 4) return MaloIntent.GREETING

        // 6. Goodbyes
        val goodbyeKeywords = if (language == SupportedLanguage.RU)
            listOf("пока", "до свидания", "прощай", "спокойной ночи", "до завтра", "ухожу", "спать")
            else listOf("bye", "goodbye", "good night", "see you", "leaving", "sleep", "farewell")
        if (goodbyeKeywords.any { lower.contains(it) } && tokens.size <= 5) return MaloIntent.GOODBYE

        // 7. Sentiment-driven intents
        when (sentiment) {
            Sentiment.FEARFUL -> return MaloIntent.FEAR
            Sentiment.ANGRY -> return MaloIntent.ANGER
            Sentiment.SAD -> return MaloIntent.SADNESS
            Sentiment.AFFECTIONATE -> return MaloIntent.AFFECTION
            Sentiment.PHILOSOPHICAL -> return MaloIntent.PHILOSOPHY
            Sentiment.CURIOUS -> return MaloIntent.CURIOSITY
            else -> {}
        }

        // 8. General question
        if (lower.contains("?") || tokens.any { (if (language == SupportedLanguage.RU) RU_QUESTION_WORDS else EN_QUESTION_WORDS).contains(it) }) {
            return MaloIntent.CURIOSITY
        }

        return MaloIntent.GENERAL
    }

    /**
     * Determines if a query requires deep LLM reasoning (Gemini Pro), such as:
     * code snippets, advanced academic or mathematical calculations, multi-clause logical reasoning,
     * translations, or texts longer than offline NLP corpus capacity.
     */
    private fun isComplexInput(rawText: String, tokens: List<String>): Boolean {
        val lower = rawText.lowercase()

        // Code / Technical indicators
        val technicalTerms = listOf(
            "function", "def ", "class ", "import ", "sql", "select ", "html", "css",
            "напиши код", "программирован", "реши уравнение", "интеграл", "переведи текст",
            "write code", "solve", "calculate", "algorithm", "python", "kotlin", "javascript"
        )
        if (technicalTerms.any { lower.contains(it) }) return true

        // Extremely long questions or complex subordinate clauses
        if (tokens.size >= 25) return true

        // Multiple question marks or nested sub-clauses
        if (rawText.count { it == '?' } >= 3) return true

        return false
    }
}
