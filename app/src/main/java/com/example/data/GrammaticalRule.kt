package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GrammaticalRule represents a syntax, inflection, or morphological rule
 * for Russian and English text generation, governing MalO's distinctive speech
 * patterns (female past tense agreement in Russian, vocative handling, pronoun concord).
 */
@Entity(
    tableName = "grammatical_rules",
    indices = [
        Index("language"),
        Index("ruleCode"),
        Index("ruleCategory"),
        Index(value = ["language", "ruleCategory"])
    ]
)
data class GrammaticalRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val language: String, // "ru" or "en"
    val ruleCode: String, // e.g. "RU_FEM_PAST_AGREEMENT", "RU_VOCATIVE_USER", "EN_PRESENT_CONT"
    val ruleCategory: String, // "INFLECTION", "AGREEMENT", "SYNTAX", "PERSONA_STYLE", "NEGATION"
    val patternRegex: String, // Regex to match in token or response template
    val replacementTemplate: String, // Transformation replacement or output template
    val description: String = "",
    val priority: Int = 10, // Higher numbers indicate higher precedence
    val isEnabled: Boolean = true
)
