package com.localbank.finance.data.repository

import com.localbank.finance.data.database.*
import com.localbank.finance.data.model.*
import com.localbank.finance.sync.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FinanceRepository(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val scheduledExpenseDao: ScheduledExpenseDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao? = null,
    private val syncManager: FirestoreSyncManager? = null,
    private val householdId: String? = null
) {

    // ─────────────────────────────────────────────
    // Contas
    // ─────────────────────────────────────────────

    fun getAllAccounts(): Flow<List<Account>> =
        accountDao.getAllAccounts()

    suspend fun insertAccount(account: Account) {
        accountDao.insert(account)
        syncPush { it.pushAccount(householdId!!, account) }
    }

    suspend fun updateAccount(account: Account) {
        accountDao.update(account)
        syncPush { it.pushAccount(householdId!!, account) }
    }

    suspend fun deleteAccount(account: Account) {
        accountDao.delete(account)
        syncPush { it.deleteRemote(householdId!!, "accounts", account.id) }
    }

    // ─────────────────────────────────────────────
    // Categorias
    // ─────────────────────────────────────────────

    fun getAllCategories(): Flow<List<Category>> =
        categoryDao.getAllCategories()

    fun getCategoriesByType(type: TransactionType): Flow<List<Category>> =
        categoryDao.getByType(type)

    suspend fun getCategoryById(id: String): Category? = categoryDao.getById(id)

    suspend fun updateCategory(category: Category) {
        categoryDao.update(category)
        syncPush { it.pushCategory(householdId!!, category) }
    }

    suspend fun insertCategory(category: Category) {
        categoryDao.insert(category)
        syncPush { it.pushCategory(householdId!!, category) }
    }

    // ─────────────────────────────────────────────
    // Transações
    // ─────────────────────────────────────────────

    fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions()

    fun getTransactionsByPeriod(from: Long, to: Long): Flow<List<Transaction>> =
        transactionDao.getByPeriod(from, to)

    suspend fun getTotalIncome(from: Long, to: Long): Double =
        transactionDao.getTotalIncome(from, to)

    suspend fun getTotalExpense(from: Long, to: Long): Double =
        transactionDao.getTotalExpense(from, to)

    suspend fun getTotalExpenseByCategory(categoryId: String, from: Long, to: Long): Double =
        transactionDao.getTotalExpenseByCategory(categoryId, from, to)

    suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insert(transaction)
        val delta = when (transaction.type) {
            TransactionType.INCOME  ->  transaction.amount
            TransactionType.EXPENSE -> -transaction.amount
        }
        accountDao.updateBalance(transaction.accountId, delta)
        syncPush { it.pushTransaction(householdId!!, transaction) }
        // Sincroniza o saldo atualizado da conta para o Firestore
        accountDao.getById(transaction.accountId)?.let { updated ->
            syncPush { it.pushAccount(householdId!!, updated) }
        }
    }

    suspend fun updateTransaction(old: Transaction, new: Transaction) {
        // Reverte o efeito da transação antiga no saldo
        val reverseOld = when (old.type) {
            TransactionType.INCOME  -> -old.amount
            TransactionType.EXPENSE ->  old.amount
        }
        // Aplica o efeito da nova transação no saldo
        val applyNew = when (new.type) {
            TransactionType.INCOME  ->  new.amount
            TransactionType.EXPENSE -> -new.amount
        }

        if (old.accountId != new.accountId) {
            accountDao.updateBalance(old.accountId, reverseOld)
            accountDao.updateBalance(new.accountId, applyNew)
            accountDao.getById(old.accountId)?.let { acc -> syncPush { it.pushAccount(householdId!!, acc) } }
        } else {
            accountDao.updateBalance(old.accountId, reverseOld + applyNew)
        }

        transactionDao.insert(new)
        syncPush { it.pushTransaction(householdId!!, new) }
        accountDao.getById(new.accountId)?.let { acc -> syncPush { it.pushAccount(householdId!!, acc) } }
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.delete(transaction)
        val delta = when (transaction.type) {
            TransactionType.INCOME  -> -transaction.amount
            TransactionType.EXPENSE ->  transaction.amount
        }
        accountDao.updateBalance(transaction.accountId, delta)
        syncPush { it.deleteRemote(householdId!!, "transactions", transaction.id) }
        // Sincroniza o saldo atualizado da conta para o Firestore
        accountDao.getById(transaction.accountId)?.let { updated ->
            syncPush { it.pushAccount(householdId!!, updated) }
        }
    }

    // ─────────────────────────────────────────────
    // Despesas agendadas
    // ─────────────────────────────────────────────

    fun getAllScheduled(): Flow<List<ScheduledExpense>> =
        scheduledExpenseDao.getAllScheduled()

    suspend fun insertScheduled(expense: ScheduledExpense) {
        scheduledExpenseDao.insert(expense)
        syncPush { it.pushScheduledExpense(householdId!!, expense) }
    }

    suspend fun updateScheduled(expense: ScheduledExpense) {
        scheduledExpenseDao.update(expense)
        syncPush { it.pushScheduledExpense(householdId!!, expense) }
    }

    suspend fun deleteScheduled(expense: ScheduledExpense) {
        scheduledExpenseDao.delete(expense)
        syncPush { it.deleteRemote(householdId!!, "scheduled_expenses", expense.id) }
    }

    suspend fun getPendingNotification(now: Long, in7Days: Long): List<ScheduledExpense> =
        scheduledExpenseDao.getPendingNotification(now, in7Days)

    suspend fun markAsNotified(id: String) =
        scheduledExpenseDao.markAsNotified(id)

    suspend fun payScheduledExpense(expense: ScheduledExpense) {
        scheduledExpenseDao.markAsPaid(expense.id)
        val transaction = Transaction(
            accountId   = expense.accountId,
            categoryId  = expense.categoryId,
            amount      = expense.amount,
            type        = TransactionType.EXPENSE,
            description = expense.description,
            date        = System.currentTimeMillis(),
            scheduledId = expense.id
        )
        insertTransaction(transaction)
        syncPush {
            it.pushScheduledExpense(householdId!!, expense.copy(isPaid = true))
        }

        // Criar próxima ocorrência automaticamente
        if (expense.isRecurring && expense.recurrenceRule != null) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = expense.dueDate }
            when (expense.recurrenceRule) {
                RecurrenceRule.DAILY   -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                RecurrenceRule.WEEKLY  -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                RecurrenceRule.MONTHLY -> cal.add(java.util.Calendar.MONTH, 1)
                RecurrenceRule.YEARLY  -> cal.add(java.util.Calendar.YEAR, 1)
            }
            val next = expense.copy(
                id      = java.util.UUID.randomUUID().toString(),
                dueDate = cal.timeInMillis,
                isPaid  = false,
                notified = false
            )
            insertScheduled(next)
        }
    }

    // ─────────────────────────────────────────────
    // Orçamentos
    // ─────────────────────────────────────────────

    fun getBudgetsByMonth(monthYear: String): Flow<List<Budget>> =
        budgetDao.getByMonth(monthYear)

    suspend fun getBudgetForCategory(categoryId: String, monthYear: String): Budget? =
        budgetDao.getByMonth(monthYear).first().find { it.categoryId == categoryId }

    suspend fun getSpentForCategory(categoryId: String, from: Long, to: Long): Double =
        transactionDao.getTotalExpenseByCategory(categoryId, from, to)

    fun getAllBudgets(): Flow<List<Budget>> =
        budgetDao.getAllBudgets()

    suspend fun insertBudget(budget: Budget) {
        budgetDao.insert(budget)
        syncPush { it.pushBudget(householdId!!, budget) }
    }

    suspend fun updateBudget(budget: Budget) {
        budgetDao.update(budget)
        syncPush { it.pushBudget(householdId!!, budget) }
    }

    suspend fun deleteBudget(budget: Budget) {
        budgetDao.delete(budget)
        syncPush { it.deleteRemote(householdId!!, "budgets", budget.id) }
    }

    // ─────────────────────────────────────────────
    // Metas de economia
    // ─────────────────────────────────────────────

    fun getAllSavingsGoals(): Flow<List<SavingsGoal>> =
        savingsGoalDao!!.getAll()

    suspend fun insertSavingsGoal(goal: SavingsGoal) =
        savingsGoalDao!!.insert(goal)

    suspend fun updateSavingsGoal(goal: SavingsGoal) =
        savingsGoalDao!!.update(goal)

    suspend fun deleteSavingsGoal(goal: SavingsGoal) =
        savingsGoalDao!!.delete(goal)

    // ─────────────────────────────────────────────
    // Sync helper
    // ─────────────────────────────────────────────

    private fun syncPush(action: (FirestoreSyncManager) -> Unit) {
        if (syncManager != null && householdId != null) {
            try { action(syncManager) } catch (_: Exception) { /* silently fail — offline first */ }
        }
    }
}