package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GrammaticalRuleDao {

    @Query("SELECT COUNT(*) FROM grammatical_rules")
    suspend fun getCount(): Int

    @Query("SELECT * FROM grammatical_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GrammaticalRule?

    @Query("SELECT * FROM grammatical_rules WHERE language = :language AND isEnabled = 1 ORDER BY priority DESC")
    suspend fun getActiveRulesForLanguage(language: String): List<GrammaticalRule>

    @Query("SELECT * FROM grammatical_rules WHERE language = :language AND ruleCategory = :category AND isEnabled = 1 ORDER BY priority DESC")
    suspend fun getRulesByCategory(language: String, category: String): List<GrammaticalRule>

    @Query("SELECT * FROM grammatical_rules WHERE ruleCode = :ruleCode LIMIT 1")
    suspend fun getRuleByCode(ruleCode: String): GrammaticalRule?

    @Query("SELECT * FROM grammatical_rules WHERE language = :language ORDER BY priority DESC")
    fun getAllRulesFlow(language: String): Flow<List<GrammaticalRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<GrammaticalRule>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: GrammaticalRule): Long

    @Update
    suspend fun updateRule(rule: GrammaticalRule): Int

    @Update
    suspend fun updateRules(rules: List<GrammaticalRule>): Int

    @Delete
    suspend fun deleteRule(rule: GrammaticalRule): Int

    @Delete
    suspend fun deleteRules(rules: List<GrammaticalRule>): Int

    @Query("DELETE FROM grammatical_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM grammatical_rules")
    suspend fun clearAll()
}
