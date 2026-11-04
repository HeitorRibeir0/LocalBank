package com.localbank.finance.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `6 para 7 converte notified em notifiedStage e mantem os dados`() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL("INSERT INTO accounts (id, name, type, balance, currency, createdAt) VALUES ('a1', 'Conta', 'WALLET', 10.5, 'BRL', 0)")
            db.execSQL(
                "INSERT INTO scheduled_expenses (id, accountId, categoryId, amount, description, dueDate, isRecurring, recurrenceRule, isPaid, notified, createdBy) " +
                    "VALUES ('s1', 'a1', NULL, 99.9, 'Luz', 1000, 1, 'MONTHLY', 0, 1, 'Ana')"
            )
            db.execSQL(
                "INSERT INTO scheduled_expenses (id, accountId, categoryId, amount, description, dueDate, isRecurring, recurrenceRule, isPaid, notified, createdBy) " +
                    "VALUES ('s2', 'a1', NULL, 50.0, 'Água', 2000, 0, NULL, 1, 0, NULL)"
            )
        }

        helper.runMigrationsAndValidate(dbName, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT id, notifiedStage, amount, description, recurrenceRule, isPaid FROM scheduled_expenses ORDER BY id").use { c ->
                assertEquals(2, c.count)
                c.moveToFirst()
                assertEquals("s1", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertEquals(99.9, c.getDouble(2), 0.0)
                assertEquals("Luz", c.getString(3))
                assertEquals("MONTHLY", c.getString(4))
                c.moveToNext()
                assertEquals("s2", c.getString(0))
                assertEquals(0, c.getInt(1))
                assertEquals(1, c.getInt(5))
            }
        }
    }

    @Test
    fun `7 para 8 converte valores para centavos sem apagar transacoes`() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL("INSERT INTO accounts (id, name, type, balance, currency, createdAt) VALUES ('a1', 'Conta', 'WALLET', 1234.5699999, 'BRL', 0)")
            db.execSQL("INSERT INTO categories (id, name, icon, colorHex, type) VALUES ('c1', 'Mercado', 'x', '#000', 'EXPENSE')")
            db.execSQL("INSERT INTO transactions (id, accountId, categoryId, amount, type, description, date, scheduledId, createdBy) VALUES ('t1', 'a1', 'c1', 0.30000000000000004, 'EXPENSE', '', 0, NULL, NULL)")
            db.execSQL("INSERT INTO scheduled_expenses (id, accountId, categoryId, amount, description, dueDate, isRecurring, recurrenceRule, isPaid, notifiedStage, createdBy) VALUES ('s1', 'a1', NULL, 99.99, 'Luz', 0, 0, NULL, 0, 1, NULL)")
            db.execSQL("INSERT INTO budgets (id, categoryId, monthYear, limitAmount, createdBy, createdAt) VALUES ('b1', 'c1', '2026-11', 500.5, NULL, 0)")
            db.execSQL("INSERT INTO savings_goals (id, name, targetAmount, savedAmount, colorHex, deadline, createdAt, createdBy) VALUES ('g1', 'Viagem', 1000.0, 333.33, '#fff', NULL, 0, NULL)")
            db.execSQL("INSERT INTO pending_captures (id, amount, merchant, sourceLabel, sourcePackage, detectedAt, fingerprint) VALUES ('p1', 12.5, 'Padaria', 'Nubank', 'x', 0, 'f')")
        }

        helper.runMigrationsAndValidate(dbName, 8, true, AppDatabase.MIGRATION_7_8).use { db ->
            fun long(sql: String): Long = db.query(sql).use { c -> c.moveToFirst(); c.getLong(0) }
            assertEquals(123457L, long("SELECT balanceCents FROM accounts"))
            assertEquals(0L, long("SELECT archived FROM accounts"))
            assertEquals(30L, long("SELECT amountCents FROM transactions WHERE id = 't1'"))
            assertEquals(1L, long("SELECT COUNT(*) FROM transactions"))
            assertEquals(9999L, long("SELECT amountCents FROM scheduled_expenses"))
            assertEquals(1L, long("SELECT notifiedStage FROM scheduled_expenses"))
            assertEquals(50050L, long("SELECT limitCents FROM budgets"))
            assertEquals(100000L, long("SELECT targetCents FROM savings_goals"))
            assertEquals(33333L, long("SELECT savedCents FROM savings_goals"))
            assertEquals(1250L, long("SELECT amountCents FROM pending_captures"))
        }
    }

    @Test
    fun `cadeia completa a partir da versao 5`() {
        helper.createDatabase(dbName, 5).close()
        helper.runMigrationsAndValidate(
            dbName, 8, true,
            AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8
        ).close()
    }
}
