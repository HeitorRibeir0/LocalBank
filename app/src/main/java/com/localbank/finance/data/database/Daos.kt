package com.localbank.finance.data.database

import androidx.room.*
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.Budget
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.SavingsGoal
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction as TransactionEntity
import com.localbank.finance.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────
// AccountDao
// ─────────────────────────────────────────────
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY createdAt DESC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): Account?

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countTransactions(accountId: String): Int

    @Query("SELECT COUNT(*) FROM scheduled_expenses WHERE accountId = :accountId")
    suspend fun countScheduledExpenses(accountId: String): Int

    // ABORT, não REPLACE: REPLACE apaga a conta antiga e o cascade apaga as transações dela
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("SELECT * FROM accounts")
    suspend fun getAllOnce(): List<Account>

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("UPDATE accounts SET balanceCents = 0")
    suspend fun resetAllBalances()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<Account>)

    // Saldo = soma das transações da conta. Recalcular em vez de aplicar deltas
    // evita divergência quando edições/remoções chegam de outro aparelho.
    @Query("""
        UPDATE accounts SET balanceCents = (
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
            FROM transactions WHERE transactions.accountId = accounts.id
        ) WHERE id = :accountId
    """)
    suspend fun recalculateBalance(accountId: String)

    @Query("""
        UPDATE accounts SET balanceCents = (
            SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
            FROM transactions WHERE transactions.accountId = accounts.id
        )
    """)
    suspend fun recalculateAllBalances()

    // Upsert que preserva o saldo local se a conta já existir
    @Query("""
        INSERT OR IGNORE INTO accounts (id, name, type, balanceCents, currency, archived, createdAt)
        VALUES (:id, :name, :type, 0, :currency, :archived, :createdAt)
    """)
    suspend fun insertIfNotExists(id: String, name: String, type: String, currency: String, archived: Boolean, createdAt: Long)

    @Query("UPDATE accounts SET name = :name, type = :type, currency = :currency, archived = :archived WHERE id = :id")
    suspend fun updateMetaOnly(id: String, name: String, type: String, currency: String, archived: Boolean)

    @Query("SELECT id FROM accounts")
    suspend fun getAllIds(): List<String>
}

// ─────────────────────────────────────────────
// CategoryDao
// ─────────────────────────────────────────────
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name ASC")
    fun getByType(type: TransactionType): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): Category?

    @Query("SELECT * FROM categories")
    suspend fun getAllOnce(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    // Para dados vindos do Firestore: REPLACE apaga a linha antes de reinserir,
    // o que dispara ON DELETE (budgets CASCADE, transactions SET NULL).
    // Insert-ignore + update preserva os filhos.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(categories: List<Category>)

    @Update
    suspend fun updateAll(categories: List<Category>)

    @Query("SELECT id FROM categories")
    suspend fun getAllIds(): List<String>
}

// ─────────────────────────────────────────────
// TransactionDao
// ─────────────────────────────────────────────
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun getByAccount(accountId: String): Flow<List<TransactionEntity>>

    // transações de um mês específico (ex: de 01/03 a 31/03)
    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun getByPeriod(from: Long, to: Long): Flow<List<TransactionEntity>>

    // últimas N transações (recentes)
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<TransactionEntity>>

    // despesa total por categoria num período (para orçamentos)
    data class CategoryTotal(
        val categoryId: String?,
        val total: Long
    )

    @Query("""
        SELECT categoryId, SUM(amountCents) AS total FROM transactions
        WHERE type = 'EXPENSE' AND date BETWEEN :from AND :to
        GROUP BY categoryId
    """)
    fun expenseByCategory(from: Long, to: Long): Flow<List<CategoryTotal>>

    // totais por mês (para relatório)
    data class MonthlyTotal(
        val monthYm: String,
        val type: String,
        val total: Long
    )

    @Query("""
        SELECT
            strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') AS monthYm,
            type,
            SUM(amountCents) AS total
        FROM transactions
        WHERE date BETWEEN :from AND :to
        GROUP BY monthYm, type
        ORDER BY monthYm DESC
    """)
    fun monthlyTotals(from: Long, to: Long): Flow<List<MonthlyTotal>>

    // soma de entradas num período
    @Query("""
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE type = 'INCOME' AND date BETWEEN :from AND :to
    """)
    suspend fun getTotalIncome(from: Long, to: Long): Long

    // soma de saídas num período
    @Query("""
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE type = 'EXPENSE' AND date BETWEEN :from AND :to
    """)
    suspend fun getTotalExpense(from: Long, to: Long): Long

    // soma de saídas de uma categoria específica num período (para orçamentos)
    @Query("""
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE type = 'EXPENSE' AND categoryId = :categoryId AND date BETWEEN :from AND :to
    """)
    suspend fun getTotalExpenseByCategory(categoryId: String, from: Long, to: Long): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE -amountCents END), 0)
        FROM transactions WHERE accountId = :accountId
    """)
    suspend fun getNetForAccount(accountId: String): Long

    @Query("SELECT * FROM transactions")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)
}

// ─────────────────────────────────────────────
// ScheduledExpenseDao
// ─────────────────────────────────────────────
@Dao
interface ScheduledExpenseDao {

    @Query("SELECT * FROM scheduled_expenses ORDER BY dueDate ASC")
    fun getAllScheduled(): Flow<List<ScheduledExpense>>

    @Query("SELECT * FROM scheduled_expenses")
    suspend fun getAllOnce(): List<ScheduledExpense>

    @Query("""
        SELECT * FROM scheduled_expenses
        WHERE isPaid = 0 AND dueDate <= :untilDate
        ORDER BY dueDate ASC
    """)
    suspend fun getUpcoming(untilDate: Long): List<ScheduledExpense>

    // Inclui as já vencidas: a agendada vence à meia-noite, então no próprio dia dueDate < agora
    @Query("""
        SELECT * FROM scheduled_expenses
        WHERE isPaid = 0 AND notifiedStage < 2 AND dueDate <= :until
    """)
    suspend fun getPendingNotification(until: Long): List<ScheduledExpense>

    @Query("SELECT * FROM scheduled_expenses WHERE id = :id")
    suspend fun getById(id: String): ScheduledExpense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ScheduledExpense): Long

    @Update
    suspend fun update(expense: ScheduledExpense)

    @Query("UPDATE scheduled_expenses SET isPaid = 1 WHERE id = :id")
    suspend fun markAsPaid(id: String)

    @Query("UPDATE scheduled_expenses SET notifiedStage = :stage WHERE id = :id")
    suspend fun setNotifiedStage(id: String, stage: Int)

    @Delete
    suspend fun delete(expense: ScheduledExpense)

    @Query("DELETE FROM scheduled_expenses")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ScheduledExpense>)
}

// ─────────────────────────────────────────────
// BudgetDao
// ─────────────────────────────────────────────
@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear ORDER BY limitCents DESC")
    fun getByMonth(monthYear: String): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear")
    suspend fun getByMonthOnce(monthYear: String): List<Budget>

    @Query("SELECT * FROM budgets ORDER BY monthYear DESC, limitCents DESC")
    fun getAllBudgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun getAllOnce(): List<Budget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<Budget>)
}

// ─────────────────────────────────────────────
// SavingsGoalDao
// ─────────────────────────────────────────────
@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SavingsGoal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: String): SavingsGoal?

    @Query("SELECT * FROM savings_goals")
    suspend fun getAllOnce(): List<SavingsGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<SavingsGoal>)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()

    // Depósito relativo: não depende de um savedCents possivelmente desatualizado na UI
    @Query("UPDATE savings_goals SET savedCents = MIN(savedCents + :cents, targetCents) WHERE id = :id")
    suspend fun addToSaved(id: String, cents: Long)

    // Edição sem tocar no valor guardado (que pode ter mudado por depósito do outro aparelho)
    @Query("""
        UPDATE savings_goals
        SET name = :name, targetCents = :targetCents, colorHex = :colorHex, deadline = :deadline
        WHERE id = :id
    """)
    suspend fun updateMeta(id: String, name: String, targetCents: Long, colorHex: String, deadline: Long?)
}