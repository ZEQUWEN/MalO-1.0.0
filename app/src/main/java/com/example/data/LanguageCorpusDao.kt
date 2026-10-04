package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * LanguageCorpusDao provides unified CRUD (Create, Read, Update, Delete)
 * operations supporting the local Russian and English language corpora schema:
 * 1. WordForm (word forms, inflections, lemmas, POS tags, grammatical features)
 * 2. GrammaticalRule (inflection transformations, gender/syntax agreements, style rules)
 * 3. ContextualAssociation (semantic connections, SCP-1471 lore triggers, emotional affinities)
 * 4. CorpusEntry (dialogue response templates, intent/sentiment mappings, follow-ups)
 */
@Dao
interface LanguageCorpusDao {

    // ========================================================================
    // 1. WORD FORMS (CRUD)
    // ========================================================================

    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordForm(form: WordForm): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordForms(forms: List<WordForm>): List<Long>

    // --- READ ---
    @Query("SELECT * FROM word_forms WHERE id = :id LIMIT 1")
    suspend fun getWordFormById(id: Long): WordForm?

    @Query("SELECT COUNT(*) FROM word_forms")
    suspend fun getWordFormsCount(): Int

    @Query("SELECT COUNT(*) FROM word_forms WHERE language = :language")
    suspend fun getWordFormsCountForLanguage(language: String): Int

    @Query("SELECT * FROM word_forms WHERE language = :language ORDER BY lemma ASC, wordForm ASC")
    fun getWordFormsFlow(language: String): Flow<List<WordForm>>

    @Query("SELECT * FROM word_forms WHERE language = :language ORDER BY lemma ASC")
    suspend fun getWordFormsByLanguage(language: String): List<WordForm>

    @Query("SELECT * FROM word_forms WHERE language = :language AND wordForm = :surfaceForm LIMIT 1")
    suspend fun findWordForm(language: String, surfaceForm: String): WordForm?

    @Query("SELECT * FROM word_forms WHERE language = :language AND lemma = :lemma")
    suspend fun findWordFormsByLemma(language: String, lemma: String): List<WordForm>

    @Query("SELECT * FROM word_forms WHERE language = :language AND wordForm IN (:tokens)")
    suspend fun lookupTokens(language: String, tokens: List<String>): List<WordForm>

    @Query("SELECT * FROM word_forms WHERE language = :language AND partOfSpeech = :pos")
    suspend fun getWordFormsByPartOfSpeech(language: String, pos: String): List<WordForm>

    @Query("SELECT * FROM word_forms WHERE language = :language AND sentiment = :sentiment")
    suspend fun getWordFormsBySentiment(language: String, sentiment: String): List<WordForm>

    // --- UPDATE ---
    @Update
    suspend fun updateWordForm(form: WordForm): Int

    @Update
    suspend fun updateWordForms(forms: List<WordForm>): Int

    // --- DELETE ---
    @Delete
    suspend fun deleteWordForm(form: WordForm): Int

    @Delete
    suspend fun deleteWordForms(forms: List<WordForm>): Int

    @Query("DELETE FROM word_forms WHERE id = :id")
    suspend fun deleteWordFormById(id: Long): Int

    @Query("DELETE FROM word_forms WHERE language = :language")
    suspend fun deleteWordFormsByLanguage(language: String): Int

    @Query("DELETE FROM word_forms")
    suspend fun clearAllWordForms(): Int


    // ========================================================================
    // 2. GRAMMATICAL RULES (CRUD)
    // ========================================================================

    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrammaticalRule(rule: GrammaticalRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrammaticalRules(rules: List<GrammaticalRule>): List<Long>

    // --- READ ---
    @Query("SELECT * FROM grammatical_rules WHERE id = :id LIMIT 1")
    suspend fun getGrammaticalRuleById(id: Long): GrammaticalRule?

    @Query("SELECT * FROM grammatical_rules WHERE ruleCode = :ruleCode LIMIT 1")
    suspend fun getGrammaticalRuleByCode(ruleCode: String): GrammaticalRule?

    @Query("SELECT COUNT(*) FROM grammatical_rules")
    suspend fun getGrammaticalRulesCount(): Int

    @Query("SELECT COUNT(*) FROM grammatical_rules WHERE language = :language")
    suspend fun getGrammaticalRulesCountForLanguage(language: String): Int

    @Query("SELECT * FROM grammatical_rules WHERE language = :language ORDER BY priority DESC")
    fun getGrammaticalRulesFlow(language: String): Flow<List<GrammaticalRule>>

    @Query("SELECT * FROM grammatical_rules WHERE language = :language AND isEnabled = 1 ORDER BY priority DESC")
    suspend fun getActiveGrammaticalRules(language: String): List<GrammaticalRule>

    @Query("SELECT * FROM grammatical_rules WHERE language = :language AND ruleCategory = :category AND isEnabled = 1 ORDER BY priority DESC")
    suspend fun getGrammaticalRulesByCategory(language: String, category: String): List<GrammaticalRule>

    // --- UPDATE ---
    @Update
    suspend fun updateGrammaticalRule(rule: GrammaticalRule): Int

    @Update
    suspend fun updateGrammaticalRules(rules: List<GrammaticalRule>): Int

    @Query("UPDATE grammatical_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, isEnabled: Boolean): Int

    // --- DELETE ---
    @Delete
    suspend fun deleteGrammaticalRule(rule: GrammaticalRule): Int

    @Delete
    suspend fun deleteGrammaticalRules(rules: List<GrammaticalRule>): Int

    @Query("DELETE FROM grammatical_rules WHERE id = :id")
    suspend fun deleteGrammaticalRuleById(id: Long): Int

    @Query("DELETE FROM grammatical_rules WHERE language = :language")
    suspend fun deleteGrammaticalRulesByLanguage(language: String): Int

    @Query("DELETE FROM grammatical_rules")
    suspend fun clearAllGrammaticalRules(): Int


    // ========================================================================
    // 3. CONTEXTUAL ASSOCIATIONS (CRUD)
    // ========================================================================

    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContextualAssociation(association: ContextualAssociation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContextualAssociations(associations: List<ContextualAssociation>): List<Long>

    // --- READ ---
    @Query("SELECT * FROM contextual_associations WHERE id = :id LIMIT 1")
    suspend fun getContextualAssociationById(id: Long): ContextualAssociation?

    @Query("SELECT COUNT(*) FROM contextual_associations")
    suspend fun getContextualAssociationsCount(): Int

    @Query("SELECT COUNT(*) FROM contextual_associations WHERE language = :language")
    suspend fun getContextualAssociationsCountForLanguage(language: String): Int

    @Query("SELECT * FROM contextual_associations WHERE language = :language ORDER BY weight DESC")
    fun getContextualAssociationsFlow(language: String): Flow<List<ContextualAssociation>>

    @Query("SELECT * FROM contextual_associations WHERE language = :language AND sourceConcept = :concept ORDER BY weight DESC")
    suspend fun getAssociationsForConcept(language: String, concept: String): List<ContextualAssociation>

    @Query("SELECT * FROM contextual_associations WHERE language = :language AND sourceConcept IN (:concepts) ORDER BY weight DESC")
    suspend fun getAssociationsForConcepts(language: String, concepts: List<String>): List<ContextualAssociation>

    @Query("SELECT * FROM contextual_associations WHERE language = :language AND associationType = :type ORDER BY weight DESC")
    suspend fun getAssociationsByType(language: String, type: String): List<ContextualAssociation>

    @Query("SELECT * FROM contextual_associations WHERE language = :language ORDER BY weight DESC LIMIT :limit")
    suspend fun getTopAssociations(language: String, limit: Int = 50): List<ContextualAssociation>

    // --- UPDATE ---
    @Update
    suspend fun updateContextualAssociation(association: ContextualAssociation): Int

    @Update
    suspend fun updateContextualAssociations(associations: List<ContextualAssociation>): Int

    @Query("UPDATE contextual_associations SET weight = :weight WHERE id = :id")
    suspend fun updateAssociationWeight(id: Long, weight: Float): Int

    // --- DELETE ---
    @Delete
    suspend fun deleteContextualAssociation(association: ContextualAssociation): Int

    @Delete
    suspend fun deleteContextualAssociations(associations: List<ContextualAssociation>): Int

    @Query("DELETE FROM contextual_associations WHERE id = :id")
    suspend fun deleteContextualAssociationById(id: Long): Int

    @Query("DELETE FROM contextual_associations WHERE language = :language")
    suspend fun deleteContextualAssociationsByLanguage(language: String): Int

    @Query("DELETE FROM contextual_associations")
    suspend fun clearAllContextualAssociations(): Int


    // ========================================================================
    // 4. CORPUS ENTRIES (CRUD)
    // ========================================================================

    // --- CREATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorpusEntry(entry: CorpusEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorpusEntries(entries: List<CorpusEntry>): List<Long>

    // --- READ ---
    @Query("SELECT * FROM corpus_entries WHERE id = :id LIMIT 1")
    suspend fun getCorpusEntryById(id: Long): CorpusEntry?

    @Query("SELECT COUNT(*) FROM corpus_entries")
    suspend fun getCorpusEntriesCount(): Int

    @Query("SELECT COUNT(*) FROM corpus_entries WHERE language = :language")
    suspend fun getCorpusEntriesCountForLanguage(language: String): Int

    @Query("SELECT * FROM corpus_entries WHERE language = :language")
    fun getCorpusEntriesFlow(language: String): Flow<List<CorpusEntry>>

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND intent = :intent")
    suspend fun getCorpusEntriesByIntent(language: String, intent: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND sentiment = :sentiment")
    suspend fun getCorpusEntriesBySentiment(language: String, sentiment: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language AND intent = :intent AND sentiment = :sentiment")
    suspend fun getCorpusEntriesByIntentAndSentiment(language: String, intent: String, sentiment: String): List<CorpusEntry>

    @Query("SELECT * FROM corpus_entries WHERE language = :language")
    suspend fun getAllCorpusEntriesForLanguage(language: String): List<CorpusEntry>

    // --- UPDATE ---
    @Update
    suspend fun updateCorpusEntry(entry: CorpusEntry): Int

    @Update
    suspend fun updateCorpusEntries(entries: List<CorpusEntry>): Int

    // --- DELETE ---
    @Delete
    suspend fun deleteCorpusEntry(entry: CorpusEntry): Int

    @Delete
    suspend fun deleteCorpusEntries(entries: List<CorpusEntry>): Int

    @Query("DELETE FROM corpus_entries WHERE id = :id")
    suspend fun deleteCorpusEntryById(id: Long): Int

    @Query("DELETE FROM corpus_entries WHERE language = :language")
    suspend fun deleteCorpusEntriesByLanguage(language: String): Int

    @Query("DELETE FROM corpus_entries")
    suspend fun clearAllCorpusEntries(): Int
}
