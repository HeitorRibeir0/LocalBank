package com.localbank.finance.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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
        Budget::class,
        SavingsGoal::class,
        PendingCapture::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun scheduledExpenseDao(): ScheduledExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun pendingCaptureDao(): PendingCaptureDao

    companion object {
        // Índice em transactions.date — todas as telas ordenam/filtram por data
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
            }
        }

        // Fila de compras capturadas das notificações (só neste aparelho)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_captures` (" +
                        "`id` TEXT NOT NULL, `amount` REAL NOT NULL, `merchant` TEXT NOT NULL, " +
                        "`sourceLabel` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, " +
                        "`detectedAt` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_captures_fingerprint` " +
                        "ON `pending_captures` (`fingerprint`)"
                )
            }
        }

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
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigrationFrom(1, 2, 3)
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

        /** Recria categorias e conta padrão (usado depois de limpar os dados no logout). */
        fun seedDefaults(db: SupportSQLiteDatabase) = prepopulateCallback.onCreate(db)

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
