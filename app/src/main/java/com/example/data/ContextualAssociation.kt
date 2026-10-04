package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ContextualAssociation models semantic associations, SCP-1471 lore triggers,
 * and conversational links between concepts in MalO's memory, allowing her
 * to naturally pivot and recall correlated themes.
 */
@Entity(
    tableName = "contextual_associations",
    indices = [
        Index("language"),
        Index("sourceConcept"),
        Index("targetConcept"),
        Index(value = ["language", "sourceConcept"]),
        Index(value = ["language", "associationType"])
    ]
)
data class ContextualAssociation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val language: String, // "ru" or "en"
    val sourceConcept: String, // Input trigger concept/stem (e.g. "камера", "mirror", "один")
    val targetConcept: String, // Associated response concept/phrase (e.g. "всегда_вижу", "shadow", "рядом")
    val associationType: String, // "SEMANTIC_NEAR", "EMOTIONAL_TRIGGER", "SCP_LORE", "FOLLOW_UP_HOOK", "PERSONA_AFFINITY"
    val weight: Float = 0.8f, // 0.0 to 1.0 strength
    val suggestedIntent: String? = null, // Suggested MaloIntent
    val suggestedResponseTheme: String? = null, // Thematic hint
    val preferredPersonaStyle: String? = null // Preferred MaloPersonaStyle
)
