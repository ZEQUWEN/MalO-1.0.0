package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContextualAssociationDao {

    @Query("SELECT COUNT(*) FROM contextual_associations")
    suspend fun getCount(): Int

    @Query("SELECT * FROM contextual_associations WHERE language = :language AND sourceConcept = :concept ORDER BY weight DESC")
    suspend fun getAssociationsForConcept(language: String, concept: String): List<ContextualAssociation>

    @Query("SELECT * FROM contextual_associations WHERE language = :language AND sourceConcept IN (:concepts) ORDER BY weight DESC")
    suspend fun getAssociationsForConcepts(language: String, concepts: List<String>): List<ContextualAssociation>

    @Query("SELECT * FROM contextual_associations WHERE language = :language AND associationType = :associationType ORDER BY weight DESC")
    suspend fun getAssociationsByType(language: String, associationType: String): List<ContextualAssociation>

    @Query("SELECT * FROM contextual_associations WHERE language = :language ORDER BY weight DESC LIMIT :limit")
    suspend fun getTopAssociations(language: String, limit: Int = 50): List<ContextualAssociation>

    @Query("SELECT * FROM contextual_associations WHERE language = :language")
    fun getAllAssociationsFlow(language: String): Flow<List<ContextualAssociation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssociations(associations: List<ContextualAssociation>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssociation(association: ContextualAssociation): Long

    @Query("DELETE FROM contextual_associations")
    suspend fun clearAll()
}
