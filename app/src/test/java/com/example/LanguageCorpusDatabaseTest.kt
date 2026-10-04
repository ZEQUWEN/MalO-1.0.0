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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LanguageCorpusDatabaseTest {

    private lateinit var db: MessageDatabase
    private lateinit var wordFormDao: WordFormDao
    private lateinit var grammaticalRuleDao: GrammaticalRuleDao
    private lateinit var contextualAssociationDao: ContextualAssociationDao
    private lateinit var corpusDao: CorpusDao
    private lateinit var languageCorpusDao: LanguageCorpusDao
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
        languageCorpusDao = db.languageCorpusDao()
        repository = LanguageCorpusRepository(corpusDao, wordFormDao, grammaticalRuleDao, contextualAssociationDao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun testWordFormCrudOperations() = runBlocking {
        // 1. Create
        val id = languageCorpusDao.insertWordForm(
            WordForm(
                language = "ru",
                lemma = "шептать",
                wordForm = "шепчу",
                partOfSpeech = "VERB",
                grammaticalFeatures = "Tense=Pres|Person=1",
                sentiment = "AFFECTIONATE"
            )
        )
        assertTrue(id > 0)

        // 2. Read
        val retrieved = languageCorpusDao.getWordFormById(id)
        assertNotNull(retrieved)
        assertEquals("шептать", retrieved?.lemma)
        assertEquals("шепчу", retrieved?.wordForm)

        // 3. Update
        val updated = retrieved!!.copy(sentiment = "FEARFUL", intentWeight = 0.9f)
        val rowsUpdated = languageCorpusDao.updateWordForm(updated)
        assertEquals(1, rowsUpdated)
        val afterUpdate = languageCorpusDao.getWordFormById(id)
        assertEquals("FEARFUL", afterUpdate?.sentiment)
        assertEquals(0.9f, afterUpdate?.intentWeight ?: 0f, 0.001f)

        // 4. Delete
        val rowsDeleted = languageCorpusDao.deleteWordFormById(id)
        assertEquals(1, rowsDeleted)
        assertNull(languageCorpusDao.getWordFormById(id))
    }

    @Test
    fun testGrammaticalRuleCrudOperations() = runBlocking {
        // 1. Create
        val id = languageCorpusDao.insertGrammaticalRule(
            GrammaticalRule(
                language = "ru",
                ruleCode = "RU_TEST_RULE",
                ruleCategory = "SYNTAX",
                patternRegex = "\\bтест\\b",
                replacementTemplate = "проверка",
                priority = 50
            )
        )
        assertTrue(id > 0)

        // 2. Read
        val rule = languageCorpusDao.getGrammaticalRuleById(id)
        assertNotNull(rule)
        assertEquals("RU_TEST_RULE", rule?.ruleCode)

        // 3. Update
        val rowsUpdated = languageCorpusDao.setRuleEnabled(id, false)
        assertEquals(1, rowsUpdated)
        assertFalse(languageCorpusDao.getGrammaticalRuleById(id)?.isEnabled ?: true)

        // 4. Delete
        languageCorpusDao.deleteGrammaticalRuleById(id)
        assertNull(languageCorpusDao.getGrammaticalRuleById(id))
    }

    @Test
    fun testContextualAssociationCrudOperations() = runBlocking {
        // 1. Create
        val id = languageCorpusDao.insertContextualAssociation(
            ContextualAssociation(
                language = "en",
                sourceConcept = "whisper",
                targetConcept = "static_noise",
                associationType = "SCP_LORE",
                weight = 0.85f
            )
        )
        assertTrue(id > 0)

        // 2. Read
        val assoc = languageCorpusDao.getContextualAssociationById(id)
        assertNotNull(assoc)
        assertEquals("whisper", assoc?.sourceConcept)

        // 3. Update
        languageCorpusDao.updateAssociationWeight(id, 0.99f)
        assertEquals(0.99f, languageCorpusDao.getContextualAssociationById(id)?.weight ?: 0f, 0.001f)

        // 4. Delete
        languageCorpusDao.deleteContextualAssociationById(id)
        assertNull(languageCorpusDao.getContextualAssociationById(id))
    }

    @Test
    fun testCorpusEntryCrudOperations() = runBlocking {
        // 1. Create
        val id = languageCorpusDao.insertCorpusEntry(
            CorpusEntry(
                language = "ru",
                intent = "GREETING",
                sentiment = "HAPPY",
                responseTemplate = "Привет из тестового набора, {user_name}!"
            )
        )
        assertTrue(id > 0)

        // 2. Read
        val entry = languageCorpusDao.getCorpusEntryById(id)
        assertNotNull(entry)
        assertEquals("GREETING", entry?.intent)

        // 3. Update
        val updated = entry!!.copy(followUp = "Ты рад меня слышать?")
        languageCorpusDao.updateCorpusEntry(updated)
        assertEquals("Ты рад меня слышать?", languageCorpusDao.getCorpusEntryById(id)?.followUp)

        // 4. Delete
        languageCorpusDao.deleteCorpusEntryById(id)
        assertNull(languageCorpusDao.getCorpusEntryById(id))
    }

    @Test
    fun testRepositorySeedingAndTokenLookup() = runBlocking {
        repository.ensureCorpusSeeded()

        assertTrue(languageCorpusDao.getWordFormsCount() > 0)
        assertTrue(languageCorpusDao.getGrammaticalRulesCount() > 0)
        assertTrue(languageCorpusDao.getContextualAssociationsCount() > 0)
        assertTrue(languageCorpusDao.getCorpusEntriesCount() > 0)

        // Russian token lookup via repository
        val tokensMap = repository.lookupTokens("ru", listOf("вижу", "боюсь", "страх"))
        assertTrue(tokensMap.containsKey("вижу"))
        assertTrue(tokensMap.containsKey("боюсь"))

        // Grammatical transformation via repository
        val transformed = repository.applyGrammarRules("ru", "я видел тебя в темноте")
        assertTrue(transformed.contains("я видела"))
    }
}
