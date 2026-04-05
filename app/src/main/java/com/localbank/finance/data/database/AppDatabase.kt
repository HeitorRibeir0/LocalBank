package com.localbank.finance.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localbank.finance.data.model.*
import net.sqlcipher.database.SupportFactory
import java.util.UUID

@Database(
    entities = [
        Account::class,
        Category::class,
        Transaction::class,
        ScheduledExpense::class,
        Budget::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun scheduledExpenseDao(): ScheduledExpenseDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = getOrCreatePassphrase(context)
                val factory = SupportFactory(passphrase)

                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_secure_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .addCallback(prepopulateCallback)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Gera ou recupera uma passphrase segura para o SQLCipher.
         * A passphrase é guardada em EncryptedSharedPreferences (protegida pelo Android Keystore).
         */
        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                "secure_db_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val existing = prefs.getString("db_passphrase", null)
            if (existing != null) {
                return existing.toByteArray()
            }

            val newPassphrase = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            prefs.edit().putString("db_passphrase", newPassphrase).apply()
            return newPassphrase.toByteArray()
        }

        // Usa SQL direto porque os DAOs não estão disponíveis durante onCreate
        private val prepopulateCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                // ── Categorias de entrada ──
                insertCategory(db, "Salário",      "work",          "#4CAF50", "INCOME")
                insertCategory(db, "Freelance",    "laptop",        "#8BC34A", "INCOME")
                insertCategory(db, "Investimento", "trending_up",   "#009688", "INCOME")
                insertCategory(db, "Presente",     "card_giftcard", "#00BCD4", "INCOME")

                // ── Categorias de saída ──
                insertCategory(db, "Alimentação",  "restaurant",     "#FF5722", "EXPENSE")
                insertCategory(db, "Transporte",   "directions_car", "#FF9800", "EXPENSE")
                insertCategory(db, "Moradia",      "home",           "#9C27B0", "EXPENSE")
                insertCategory(db, "Saúde",        "local_hospital", "#F44336", "EXPENSE")
                insertCategory(db, "Lazer",        "sports_esports", "#3F51B5", "EXPENSE")
                insertCategory(db, "Assinatura",   "subscriptions",  "#607D8B", "EXPENSE")
                insertCategory(db, "Educação",     "school",         "#795548", "EXPENSE")
                insertCategory(db, "Outros",       "category",       "#9E9E9E", "EXPENSE")

                // ── Conta padrão ──
                db.execSQL(
                    """INSERT INTO accounts (id, name, type, balance, currency, createdAt) 
                       VALUES (?, ?, ?, ?, ?, ?)""",
                    arrayOf(
                        UUID.randomUUID().toString(),
                        "Carteira",
                        "WALLET",
                        0.0,
                        "BRL",
                        System.currentTimeMillis()
                    )
                )
            }

            private fun insertCategory(
                db: SupportSQLiteDatabase,
                name: String, icon: String, colorHex: String, type: String
            ) {
                db.execSQL(
                    """INSERT INTO categories (id, name, icon, colorHex, type) 
                       VALUES (?, ?, ?, ?, ?)""",
                    arrayOf(UUID.randomUUID().toString(), name, icon, colorHex, type)
                )
            }
        }
    }
}
