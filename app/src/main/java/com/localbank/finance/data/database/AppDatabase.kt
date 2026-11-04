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
import com.localbank.finance.ui.util.normalizeCategoryName
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
    version = 8,
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
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
            }
        }

        // Fila de compras capturadas das notificações (só neste aparelho)
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
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

        // notified (bool) → notifiedStage (0 nada, 1 avisou "faltam até 3 dias", 2 avisou "vence hoje").
        // Recria a tabela: o Room recusa o banco se a coluna antiga continuar lá.
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scheduled_expenses_new` (`id` TEXT NOT NULL, " +
                        "`accountId` TEXT NOT NULL, `categoryId` TEXT, `amount` REAL NOT NULL, " +
                        "`description` TEXT NOT NULL, `dueDate` INTEGER NOT NULL, `isRecurring` INTEGER NOT NULL, " +
                        "`recurrenceRule` TEXT, `isPaid` INTEGER NOT NULL, `notifiedStage` INTEGER NOT NULL, " +
                        "`createdBy` TEXT, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL(
                    "INSERT INTO `scheduled_expenses_new` (id, accountId, categoryId, amount, description, dueDate, " +
                        "isRecurring, recurrenceRule, isPaid, notifiedStage, createdBy) " +
                        "SELECT id, accountId, categoryId, amount, description, dueDate, isRecurring, recurrenceRule, " +
                        "isPaid, CASE WHEN notified = 1 THEN 1 ELSE 0 END, createdBy FROM `scheduled_expenses`"
                )
                db.execSQL("DROP TABLE `scheduled_expenses`")
                db.execSQL("ALTER TABLE `scheduled_expenses_new` RENAME TO `scheduled_expenses`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_expenses_accountId` ON `scheduled_expenses` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_expenses_categoryId` ON `scheduled_expenses` (`categoryId`)")
            }
        }

        // Dinheiro em centavos (Long) + contas arquiváveis. Recria as tabelas sem usar
        // DROP COLUMN (o SQLite de aparelhos antigos não tem). Ordem pensada para não disparar
        // o ON DELETE CASCADE: as filhas de `accounts` saem antes e voltam depois.
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            private fun cents(col: String) = "CAST(ROUND(`$col` * 100) AS INTEGER)"

            private fun recreate(db: SupportSQLiteDatabase, table: String, createSql: String, select: String) {
                db.execSQL(createSql.replace("`$table`", "`${table}_new`"))
                db.execSQL("INSERT INTO `${table}_new` $select FROM `$table`")
                db.execSQL("DROP TABLE `$table`")
                db.execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
            }

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TEMP TABLE tx_tmp AS SELECT id, accountId, categoryId, ${cents("amount")} AS amountCents, " +
                        "type, description, date, scheduledId, createdBy FROM transactions"
                )
                db.execSQL(
                    "CREATE TEMP TABLE sched_tmp AS SELECT id, accountId, categoryId, ${cents("amount")} AS amountCents, " +
                        "description, dueDate, isRecurring, recurrenceRule, isPaid, notifiedStage, createdBy FROM scheduled_expenses"
                )
                db.execSQL("DROP TABLE transactions")
                db.execSQL("DROP TABLE scheduled_expenses")

                recreate(
                    db, "accounts",
                    "CREATE TABLE `accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`balanceCents` INTEGER NOT NULL, `currency` TEXT NOT NULL, `archived` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    "SELECT id, name, type, ${cents("balance")}, currency, 0, createdAt"
                )

                db.execSQL(
                    "CREATE TABLE `transactions` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `categoryId` TEXT, " +
                        "`amountCents` INTEGER NOT NULL, `type` TEXT NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                        "`scheduledId` TEXT, `createdBy` TEXT, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("INSERT INTO transactions SELECT * FROM tx_tmp")
                db.execSQL("DROP TABLE tx_tmp")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_scheduledId` ON `transactions` (`scheduledId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")

                db.execSQL(
                    "CREATE TABLE `scheduled_expenses` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `categoryId` TEXT, " +
                        "`amountCents` INTEGER NOT NULL, `description` TEXT NOT NULL, `dueDate` INTEGER NOT NULL, " +
                        "`isRecurring` INTEGER NOT NULL, `recurrenceRule` TEXT, `isPaid` INTEGER NOT NULL, " +
                        "`notifiedStage` INTEGER NOT NULL, `createdBy` TEXT, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("INSERT INTO scheduled_expenses SELECT * FROM sched_tmp")
                db.execSQL("DROP TABLE sched_tmp")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_expenses_accountId` ON `scheduled_expenses` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_expenses_categoryId` ON `scheduled_expenses` (`categoryId`)")

                recreate(
                    db, "budgets",
                    "CREATE TABLE `budgets` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `monthYear` TEXT NOT NULL, " +
                        "`limitCents` INTEGER NOT NULL, `createdBy` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    "SELECT id, categoryId, monthYear, ${cents("limitAmount")}, createdBy, createdAt"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")

                recreate(
                    db, "savings_goals",
                    "CREATE TABLE `savings_goals` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `targetCents` INTEGER NOT NULL, " +
                        "`savedCents` INTEGER NOT NULL, `colorHex` TEXT NOT NULL, `deadline` INTEGER, `createdAt` INTEGER NOT NULL, " +
                        "`createdBy` TEXT, PRIMARY KEY(`id`))",
                    "SELECT id, name, ${cents("targetAmount")}, ${cents("savedAmount")}, colorHex, deadline, createdAt, createdBy"
                )

                recreate(
                    db, "pending_captures",
                    "CREATE TABLE `pending_captures` (`id` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `merchant` TEXT NOT NULL, " +
                        "`sourceLabel` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `detectedAt` INTEGER NOT NULL, " +
                        "`fingerprint` TEXT NOT NULL, PRIMARY KEY(`id`))",
                    "SELECT id, ${cents("amount")}, merchant, sourceLabel, sourcePackage, detectedAt, fingerprint"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_captures_fingerprint` ON `pending_captures` (`fingerprint`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                System.loadLibrary("sqlcipher")
                val passphrase = getOrCreatePassphrase(context)
                val factory = SupportOpenHelperFactory(passphrase)

                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_secure_db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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
            try {
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
            } catch (e: Exception) {
                // Falha ao ler passphrase criptografada (ex.: troca de celular).
                // Limpar e recriar para que o app se recupere via recoverHousehold.
                try {
                    context.deleteSharedPreferences("secure_db_prefs")
                    context.deleteDatabase("finance_secure_db")
                } catch (_: Exception) {}

                val newPassphrase = UUID.randomUUID().toString() + UUID.randomUUID().toString()
                try {
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
                    prefs.edit().putString("db_passphrase", newPassphrase).apply()
                } catch (_: Exception) {}

                return newPassphrase.toByteArray()
            }
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
                    """INSERT INTO accounts (id, name, type, balanceCents, currency, archived, createdAt)
                       VALUES (?, ?, ?, 0, ?, 0, ?)""",
                    arrayOf<Any>(
                        UUID.randomUUID().toString(),
                        "Carteira",
                        "WALLET",
                        "BRL",
                        System.currentTimeMillis()
                    )
                )
            }

            private fun insertCategory(
                db: SupportSQLiteDatabase,
                name: String, icon: String, colorHex: String, type: String
            ) {
                val canonical = normalizeCategoryName(name)
                val categoryId = UUID.nameUUIDFromBytes("$type:${canonical.lowercase()}".toByteArray()).toString()
                db.execSQL(
                    """INSERT INTO categories (id, name, icon, colorHex, type)
                       VALUES (?, ?, ?, ?, ?)""",
                    arrayOf(categoryId, canonical, icon, colorHex, type)
                )
            }
        }
    }
}
