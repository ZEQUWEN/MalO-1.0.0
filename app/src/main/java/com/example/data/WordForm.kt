package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * WordForm represents an inflected or surface form of a word in the local
 * Russian or English language corpora, linked to its lemma, grammatical features,
 * and emotional/intent valence.
 */
@Entity(
    tableName = "word_forms",
    indices = [
        Index("language"),
        Index("lemma"),
        Index("wordForm"),
        Index(value = ["language", "wordForm"]),
        Index(value = ["language", "lemma"])
    ]
)
data class WordForm(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val language: String, // "ru" or "en"
    val lemma: String,    // Dictionary base form (e.g., "видеть", "watch", "страх")
    val wordForm: String, // Surface form (e.g., "вижу", "видела", "watching", "scared")
    val partOfSpeech: String, // "NOUN", "VERB", "ADJECTIVE", "PRONOUN", "ADVERB", "INTERJECTION"
    val grammaticalFeatures: String = "", // e.g., "Gender=Fem|Number=Sing|Case=Nom" or "Tense=Past|Person=1"
    val sentiment: String = "NEUTRAL", // "NEUTRAL", "FEARFUL", "AFFECTIONATE", "SAD", "ANGRY", etc.
    val intentWeight: Float = 0.5f,
    val primaryIntent: String? = null // "FEAR", "LOCATION", "AFFECTION", "IDENTITY", etc.
)
