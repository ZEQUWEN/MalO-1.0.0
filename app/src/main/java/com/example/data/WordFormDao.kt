package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordFormDao {

    @Query("SELECT COUNT(*) FROM word_forms")
    suspend fun getCount(): Int

    @Query("SELECT * FROM word_forms WHERE language = :language AND wordForm = :surfaceForm LIMIT 1")
    suspend fun findByWordForm(language: String, surfaceForm: String): WordForm?

    @Query("SELECT * FROM word_forms WHERE language = :language AND lemma = :lemma")
    suspend fun findFormsByLemma(language: String, lemma: String): List<WordForm>

    @Query("SELECT * FROM word_forms WHERE language = :language AND wordForm IN (:tokens)")
    suspend fun lookupTokens(language: String, tokens: List<String>): List<WordForm>

    @Query("SELECT * FROM word_forms WHERE language = :language AND partOfSpeech = :partOfSpeech")
    suspend fun findByPartOfSpeech(language: String, partOfSpeech: String): List<WordForm>

    @Query("SELECT * FROM word_forms WHERE language = :language AND sentiment = :sentiment")
    suspend fun findBySentiment(language: String, sentiment: String): List<WordForm>

    @Query("SELECT * FROM word_forms WHERE language = :language")
    fun getAllForLanguageFlow(language: String): Flow<List<WordForm>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordForms(forms: List<WordForm>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordForm(form: WordForm): Long

    @Query("DELETE FROM word_forms")
    suspend fun clearAll()
}
