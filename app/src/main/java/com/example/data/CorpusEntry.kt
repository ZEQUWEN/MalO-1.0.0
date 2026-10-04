package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "corpus_entries")
data class CorpusEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language: String, // "ru" or "en"
    val intent: String,   // MaloIntent name: GREETING, IDENTITY, LOCATION, etc.
    val sentiment: String,// Sentiment name: NEUTRAL, SAD, ANGRY, etc.
    val responseTemplate: String,
    val followUp: String? = null,
    val tags: String = "" // comma-separated stems/keywords
)
