package com.example.language

enum class SupportedLanguage(val code: String) {
    RU("ru"),
    EN("en")
}

object LanguageDetector {

    /**
     * Determines whether user text is Russian or English.
     * Evaluates Cyrillic vs Latin character distribution and common language markers.
     */
    fun detectLanguage(text: String): SupportedLanguage {
        val clean = text.lowercase()
        var cyrillicCount = 0
        var latinCount = 0

        for (ch in clean) {
            when {
                ch in 'а'..'я' || ch == 'ё' -> cyrillicCount++
                ch in 'a'..'z' -> latinCount++
            }
        }

        return when {
            cyrillicCount > latinCount -> SupportedLanguage.RU
            latinCount > cyrillicCount -> SupportedLanguage.EN
            // Fallback heuristics based on common words if letter counts are tied
            clean.contains(Regex("\\b(the|is|you|are|what|who|hello|hi|i|am)\\b")) -> SupportedLanguage.EN
            clean.contains(Regex("\\b(и|в|не|на|я|что|ты|как|это|он)\\b")) -> SupportedLanguage.RU
            else -> SupportedLanguage.RU // Default to Russian as primary persona locale
        }
    }
}
