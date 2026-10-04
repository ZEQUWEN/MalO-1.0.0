package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CorpusDao {

    @Query("SELECT COUNT(*) FROM corpus_entries")
    suspend fun getCount(): Int

    @Query("SELECT * FROM corpus_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CorpusEntry?

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND intent = :intent")
    suspend fun getEntriesByIntent(language: String, intent: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND sentiment = :sentiment")
    suspend fun getEntriesBySentiment(language: String, sentiment: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language")
    suspend fun getAllForLanguage(language: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language")
    fun getAllForLanguageFlow(language: String): Flow<List<CorpusEntry>>

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND intent = :intent AND sentiment = :sentiment")
    suspend fun getEntriesByIntentAndSentiment(language: String, intent: String, sentiment: String): List<CorpusEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<CorpusEntry>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CorpusEntry): Long

    @Update
    suspend fun updateEntry(entry: CorpusEntry): Int

    @Update
    suspend fun updateEntries(entries: List<CorpusEntry>): Int

    @Delete
    suspend fun deleteEntry(entry: CorpusEntry): Int

    @Delete
    suspend fun deleteEntries(entries: List<CorpusEntry>): Int

    @Query("DELETE FROM corpus_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM corpus_entries")
    suspend fun clearCorpus()
}
