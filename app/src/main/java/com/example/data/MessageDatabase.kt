package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Message::class,
        CorpusEntry::class,
        WordForm::class,
        GrammaticalRule::class,
        ContextualAssociation::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun corpusDao(): CorpusDao
    abstract fun wordFormDao(): WordFormDao
    abstract fun grammaticalRuleDao(): GrammaticalRuleDao
    abstract fun contextualAssociationDao(): ContextualAssociationDao

    companion object {
        @Volatile
        private var INSTANCE: MessageDatabase? = null

        fun getInstance(context: Context): MessageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageDatabase::class.java,
                    "malo_chat_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
