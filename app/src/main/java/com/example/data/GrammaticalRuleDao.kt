package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GrammaticalRuleDao {

    @Query("SELECT COUNT(*) FROM grammatical_rules")
    suspend fun getCount(): Int

    @Query("SELECT * FROM grammatical_rules WHERE language = :language AND isEnabled = 1 ORDER BY priority DESC")
    suspend fun getActiveRulesForLanguage(language: String): List<GrammaticalRule>

    @Query("SELECT * FROM grammatical_rules WHERE language = :language AND ruleCategory = :category AND isEnabled = 1 ORDER BY priority DESC")
    suspend fun getRulesByCategory(language: String, category: String): List<GrammaticalRule>

    @Query("SELECT * FROM grammatical_rules WHERE ruleCode = :ruleCode LIMIT 1")
    suspend fun getRuleByCode(ruleCode: String): GrammaticalRule?

    @Query("SELECT * FROM grammatical_rules WHERE language = :language ORDER BY priority DESC")
    fun getAllRulesFlow(language: String): Flow<List<GrammaticalRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<GrammaticalRule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: GrammaticalRule): Long

    @Query("DELETE FROM grammatical_rules")
    suspend fun clearAll()
}
