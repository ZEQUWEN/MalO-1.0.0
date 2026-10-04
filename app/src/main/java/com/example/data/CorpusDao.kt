package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CorpusDao {

    @Query("SELECT COUNT(*) FROM corpus_entries")
    suspend fun getCount(): Int

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND intent = :intent")
    suspend fun getEntriesByIntent(language: String, intent: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND sentiment = :sentiment")
    suspend fun getEntriesBySentiment(language: String, sentiment: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language")
    suspend fun getAllForLanguage(language: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND intent = :intent AND sentiment = :sentiment")
    suspend fun getEntriesByIntentAndSentiment(language: String, intent: String, sentiment: String): List<CorpusEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<CorpusEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CorpusEntry): Long

    @Query("DELETE FROM corpus_entries")
    suspend fun clearCorpus()
}
