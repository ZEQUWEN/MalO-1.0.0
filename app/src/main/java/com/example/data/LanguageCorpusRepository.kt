package com.example.data

import com.example.language.CorpusSeedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * LanguageCorpusRepository abstracts and coordinates all data access
 * and CRUD operations for the local linguistic corpus: corpus response templates,
 * word forms, grammatical rules, and contextual associations for Russian and English.
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

    // ========================================================================
    // WORD FORMS CRUD
    // ========================================================================
    suspend fun insertWordForm(form: WordForm): Long = withContext(Dispatchers.IO) {
        wordFormDao.insertWordForm(form)
    }

    suspend fun insertWordForms(forms: List<WordForm>): List<Long> = withContext(Dispatchers.IO) {
        wordFormDao.insertWordForms(forms)
    }

    suspend fun getWordFormById(id: Long): WordForm? = withContext(Dispatchers.IO) {
        wordFormDao.getById(id)
    }

    fun getWordFormsFlow(language: String): Flow<List<WordForm>> {
        return wordFormDao.getAllForLanguageFlow(language)
    }

    suspend fun updateWordForm(form: WordForm): Int = withContext(Dispatchers.IO) {
        wordFormDao.updateWordForm(form)
    }

    suspend fun deleteWordForm(form: WordForm): Int = withContext(Dispatchers.IO) {
        wordFormDao.deleteWordForm(form)
    }

    suspend fun deleteWordFormById(id: Long): Int = withContext(Dispatchers.IO) {
        wordFormDao.deleteById(id)
    }

    suspend fun lookupTokens(language: String, tokens: List<String>): Map<String, WordForm> = withContext(Dispatchers.IO) {
        val forms = wordFormDao.lookupTokens(language, tokens)
        forms.associateBy { it.wordForm.lowercase() }
    }

    // ========================================================================
    // GRAMMATICAL RULES CRUD
    // ========================================================================
    suspend fun insertGrammaticalRule(rule: GrammaticalRule): Long = withContext(Dispatchers.IO) {
        grammaticalRuleDao.insertRule(rule)
    }

    suspend fun insertGrammaticalRules(rules: List<GrammaticalRule>): List<Long> = withContext(Dispatchers.IO) {
        grammaticalRuleDao.insertRules(rules)
    }

    suspend fun getGrammaticalRuleById(id: Long): GrammaticalRule? = withContext(Dispatchers.IO) {
        grammaticalRuleDao.getById(id)
    }

    fun getGrammaticalRulesFlow(language: String): Flow<List<GrammaticalRule>> {
        return grammaticalRuleDao.getAllRulesFlow(language)
    }

    suspend fun updateGrammaticalRule(rule: GrammaticalRule): Int = withContext(Dispatchers.IO) {
        grammaticalRuleDao.updateRule(rule)
    }

    suspend fun deleteGrammaticalRule(rule: GrammaticalRule): Int = withContext(Dispatchers.IO) {
        grammaticalRuleDao.deleteRule(rule)
    }

    suspend fun deleteGrammaticalRuleById(id: Long): Int = withContext(Dispatchers.IO) {
        grammaticalRuleDao.deleteById(id)
    }

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

    // ========================================================================
    // CONTEXTUAL ASSOCIATIONS CRUD
    // ========================================================================
    suspend fun insertContextualAssociation(association: ContextualAssociation): Long = withContext(Dispatchers.IO) {
        contextualAssociationDao.insertAssociation(association)
    }

    suspend fun insertContextualAssociations(associations: List<ContextualAssociation>): List<Long> = withContext(Dispatchers.IO) {
        contextualAssociationDao.insertAssociations(associations)
    }

    suspend fun getContextualAssociationById(id: Long): ContextualAssociation? = withContext(Dispatchers.IO) {
        contextualAssociationDao.getById(id)
    }

    fun getContextualAssociationsFlow(language: String): Flow<List<ContextualAssociation>> {
        return contextualAssociationDao.getAllAssociationsFlow(language)
    }

    suspend fun updateContextualAssociation(association: ContextualAssociation): Int = withContext(Dispatchers.IO) {
        contextualAssociationDao.updateAssociation(association)
    }

    suspend fun deleteContextualAssociation(association: ContextualAssociation): Int = withContext(Dispatchers.IO) {
        contextualAssociationDao.deleteAssociation(association)
    }

    suspend fun deleteContextualAssociationById(id: Long): Int = withContext(Dispatchers.IO) {
        contextualAssociationDao.deleteById(id)
    }

    suspend fun findAssociations(language: String, concepts: List<String>): List<ContextualAssociation> = withContext(Dispatchers.IO) {
        contextualAssociationDao.getAssociationsForConcepts(language, concepts)
    }

    // ========================================================================
    // CORPUS ENTRIES CRUD
    // ========================================================================
    suspend fun insertCorpusEntry(entry: CorpusEntry): Long = withContext(Dispatchers.IO) {
        corpusDao.insertEntry(entry)
    }

    suspend fun insertCorpusEntries(entries: List<CorpusEntry>): List<Long> = withContext(Dispatchers.IO) {
        corpusDao.insertEntries(entries)
    }

    suspend fun getCorpusEntryById(id: Long): CorpusEntry? = withContext(Dispatchers.IO) {
        corpusDao.getById(id)
    }

    fun getCorpusEntriesFlow(language: String): Flow<List<CorpusEntry>> {
        return corpusDao.getAllForLanguageFlow(language)
    }

    suspend fun updateCorpusEntry(entry: CorpusEntry): Int = withContext(Dispatchers.IO) {
        corpusDao.updateEntry(entry)
    }

    suspend fun deleteCorpusEntry(entry: CorpusEntry): Int = withContext(Dispatchers.IO) {
        corpusDao.deleteEntry(entry)
    }

    suspend fun deleteCorpusEntryById(id: Long): Int = withContext(Dispatchers.IO) {
        corpusDao.deleteById(id)
    }

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
