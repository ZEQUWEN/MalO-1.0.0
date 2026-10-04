package com.example.data

import com.example.language.CorpusSeedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LanguageCorpusRepository abstracts and coordinates all data access
 * for the local linguistic corpus: corpus response templates, word forms,
 * grammatical rules, and contextual associations for Russian and English.
 */
class LanguageCorpusRepository(
    private val corpusDao: CorpusDao,
    private val wordFormDao: WordFormDao,
    private val grammaticalRuleDao: GrammaticalRuleDao,
    private val contextualAssociationDao: ContextualAssociationDao
) {

    /**
     * Seeds all linguistic tables if empty.
     */
    suspend fun ensureCorpusSeeded() = withContext(Dispatchers.IO) {
        if (corpusDao.getCount() == 0) {
            corpusDao.insertEntries(CorpusSeedData.getInitialCorpus())
        }
        if (wordFormDao.getCount() == 0) {
            wordFormDao.insertWordForms(CorpusSeedData.getInitialWordForms())
        }
        if (grammaticalRuleDao.getCount() == 0) {
            grammaticalRuleDao.insertRules(CorpusSeedData.getInitialGrammaticalRules())
        }
        if (contextualAssociationDao.getCount() == 0) {
            contextualAssociationDao.insertAssociations(CorpusSeedData.getInitialContextualAssociations())
        }
    }

    /**
     * Look up word forms and grammatical attributes for the given tokens in the corpus.
     */
    suspend fun lookupTokens(language: String, tokens: List<String>): Map<String, WordForm> = withContext(Dispatchers.IO) {
        val forms = wordFormDao.lookupTokens(language, tokens)
        forms.associateBy { it.wordForm.lowercase() }
    }

    /**
     * Retrieve contextual associations for a list of concept triggers.
     */
    suspend fun findAssociations(language: String, concepts: List<String>): List<ContextualAssociation> = withContext(Dispatchers.IO) {
        contextualAssociationDao.getAssociationsForConcepts(language, concepts)
    }

    /**
     * Apply grammatical rules and morphological transformation templates to synthesized text.
     */
    suspend fun applyGrammarRules(language: String, text: String): String = withContext(Dispatchers.IO) {
        val rules = grammaticalRuleDao.getActiveRulesForLanguage(language)
        var result = text
        for (rule in rules) {
            try {
                if (rule.patternRegex.isNotBlank()) {
                    val regex = Regex(rule.patternRegex)
                    result = regex.replace(result, rule.replacementTemplate)
                }
            } catch (e: Exception) {
                // Ignore invalid regex patterns gracefully
            }
        }
        result
    }

    /**
     * Query corpus candidates by intent and sentiment.
     */
    suspend fun getCorpusCandidates(language: String, intent: String, sentiment: String): List<CorpusEntry> = withContext(Dispatchers.IO) {
        var candidates = corpusDao.getEntriesByIntent(language, intent)
        if (candidates.isEmpty()) {
            candidates = corpusDao.getEntriesBySentiment(language, sentiment)
        }
        if (candidates.isEmpty()) {
            candidates = corpusDao.getAllForLanguage(language)
        }
        candidates
    }
}
