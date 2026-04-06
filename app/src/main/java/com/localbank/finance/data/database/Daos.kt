package com.localbank.finance.data.database

import androidx.room.*
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.Budget
import com.localbank.finance.data.model.Category
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

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :accountId")
    suspend fun updateBalance(accountId: String, delta: Double)

    @Delete
    suspend fun delete(account: Account)

    @Query("SELECT * FROM accounts")
    suspend fun getAllOnce(): List<Account>

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    @Query("UPDATE accounts SET balance = 0")
    suspend fun resetAllBalances()

    // Upsert que preserva o saldo local se a conta já existir
    @Query("""
        INSERT OR IGNORE INTO accounts (id, name, type, balance, currency, createdAt)
        VALUES (:id, :name, :type, :balance, :currency, :createdAt)
    """)
    suspend fun insertIfNotExists(id: String, name: String, type: String, balance: Double, currency: String, createdAt: Long)

    @Query("UPDATE accounts SET name = :name, type = :type, currency = :currency WHERE id = :id")
    suspend fun updateMetaOnly(id: String, name: String, type: String, currency: String)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
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

    // soma de entradas num período
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE type = 'INCOME' AND date BETWEEN :from AND :to
    """)
    suspend fun getTotalIncome(from: Long, to: Long): Double

    // soma de saídas num período
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE type = 'EXPENSE' AND date BETWEEN :from AND :to
    """)
    suspend fun getTotalExpense(from: Long, to: Long): Double

    // soma de saídas de uma categoria específica num período (para orçamentos)
    @Query("""
        SELECT COALESCE(SUM(amount), 0.0) FROM transactions
        WHERE type = 'EXPENSE' AND categoryId = :categoryId AND date BETWEEN :from AND :to
    """)
    suspend fun getTotalExpenseByCategory(categoryId: String, from: Long, to: Long): Double

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0.0)
        FROM transactions WHERE accountId = :accountId
    """)
    suspend fun getNetForAccount(accountId: String): Double

    @Query("SELECT * FROM transactions")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

// ─────────────────────────────────────────────
// ScheduledExpenseDao
// ─────────────────────────────────────────────
@Dao
interface ScheduledExpenseDao {

    @Query("SELECT * FROM scheduled_expenses ORDER BY dueDate ASC")
    fun getAllScheduled(): Flow<List<ScheduledExpense>>

    @Query("""
        SELECT * FROM scheduled_expenses
        WHERE isPaid = 0 AND dueDate <= :untilDate
        ORDER BY dueDate ASC
    """)
    suspend fun getUpcoming(untilDate: Long): List<ScheduledExpense>

    @Query("""
        SELECT * FROM scheduled_expenses
        WHERE isPaid = 0 AND notified = 0
          AND dueDate BETWEEN :now AND :in7Days
    """)
    suspend fun getPendingNotification(now: Long, in7Days: Long): List<ScheduledExpense>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ScheduledExpense): Long

    @Update
    suspend fun update(expense: ScheduledExpense)

    @Query("UPDATE scheduled_expenses SET isPaid = 1 WHERE id = :id")
    suspend fun markAsPaid(id: String)

    @Query("UPDATE scheduled_expenses SET notified = 1 WHERE id = :id")
    suspend fun markAsNotified(id: String)

    @Delete
    suspend fun delete(expense: ScheduledExpense)

    @Query("DELETE FROM scheduled_expenses")
    suspend fun deleteAll()
}

// ─────────────────────────────────────────────
// BudgetDao
// ─────────────────────────────────────────────
@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear ORDER BY limitAmount DESC")
    fun getByMonth(monthYear: String): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY monthYear DESC, limitAmount DESC")
    fun getAllBudgets(): Flow<List<Budget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
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
}