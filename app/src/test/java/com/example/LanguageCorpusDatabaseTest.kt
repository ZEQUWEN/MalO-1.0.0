package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.language.CorpusSeedData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguageCorpusDatabaseTest {

    private lateinit var db: MessageDatabase
    private lateinit var wordFormDao: WordFormDao
    private lateinit var grammaticalRuleDao: GrammaticalRuleDao
    private lateinit var contextualAssociationDao: ContextualAssociationDao
    private lateinit var corpusDao: CorpusDao
    private lateinit var repository: LanguageCorpusRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MessageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        wordFormDao = db.wordFormDao()
        grammaticalRuleDao = db.grammaticalRuleDao()
        contextualAssociationDao = db.contextualAssociationDao()
        corpusDao = db.corpusDao()
        repository = LanguageCorpusRepository(corpusDao, wordFormDao, grammaticalRuleDao, contextualAssociationDao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testWordFormInsertionAndLookup() = runBlocking {
        val forms = CorpusSeedData.getInitialWordForms()
        wordFormDao.insertWordForms(forms)

        assertTrue(wordFormDao.getCount() > 0)

        // Russian lookup
        val ruForm = wordFormDao.findByWordForm("ru", "видела")
        assertNotNull(ruForm)
        assertEquals("видеть", ruForm?.lemma)
        assertEquals("VERB", ruForm?.partOfSpeech)
        assertTrue(ruForm?.grammaticalFeatures?.contains("Fem") == true)

        // English lookup
        val enForm = wordFormDao.findByWordForm("en", "watching")
        assertNotNull(enForm)
        assertEquals("watch", enForm?.lemma)
        assertEquals("VERB", enForm?.partOfSpeech)

        // Token batch lookup
        val batch = wordFormDao.lookupTokens("ru", listOf("вижу", "страх", "зеркало", "несуществующее"))
        assertEquals(3, batch.size)
    }

    @Test
    fun testGrammaticalRuleApplication() = runBlocking {
        val rules = CorpusSeedData.getInitialGrammaticalRules()
        grammaticalRuleDao.insertRules(rules)

        assertTrue(grammaticalRuleDao.getCount() > 0)

        val activeRuRules = grammaticalRuleDao.getActiveRulesForLanguage("ru")
        assertTrue(activeRuRules.isNotEmpty())

        // Test female past tense verb agreement rule: "я видел" -> "я видела"
        val transformedRu = repository.applyGrammarRules("ru", "я видел тебя через камеру")
        assertTrue(transformedRu.contains("я видела"))

        // Test English SCP casing rule: "scp-1471" -> "SCP-1471"
        val transformedEn = repository.applyGrammarRules("en", "I am scp-1471")
        assertTrue(transformedEn.contains("SCP-1471"))
    }

    @Test
    fun testContextualAssociationsQuery() = runBlocking {
        val associations = CorpusSeedData.getInitialContextualAssociations()
        contextualAssociationDao.insertAssociations(associations)

        assertTrue(contextualAssociationDao.getCount() > 0)

        // Query Russian concept "зеркало"
        val mirrorAssoc = contextualAssociationDao.getAssociationsForConcept("ru", "зеркало")
        assertFalse(mirrorAssoc.isEmpty())
        assertEquals("отражение_за_спиной", mirrorAssoc.first().targetConcept)
        assertEquals("SCP_LORE", mirrorAssoc.first().associationType)

        // Query English concept "alone"
        val aloneAssoc = contextualAssociationDao.getAssociationsForConcept("en", "alone")
        assertFalse(aloneAssoc.isEmpty())
        assertTrue(aloneAssoc.first().weight >= 0.9f)
    }

    @Test
    fun testRepositorySeedingPipeline() = runBlocking {
        assertEquals(0, corpusDao.getCount())
        assertEquals(0, wordFormDao.getCount())
        assertEquals(0, grammaticalRuleDao.getCount())
        assertEquals(0, contextualAssociationDao.getCount())

        repository.ensureCorpusSeeded()

        assertTrue(corpusDao.getCount() > 0)
        assertTrue(wordFormDao.getCount() > 0)
        assertTrue(grammaticalRuleDao.getCount() > 0)
        assertTrue(contextualAssociationDao.getCount() > 0)
    }
}
