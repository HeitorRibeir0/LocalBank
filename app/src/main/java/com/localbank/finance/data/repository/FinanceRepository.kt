package com.localbank.finance.data.repository

import com.localbank.finance.data.database.*
import com.localbank.finance.data.model.*
import com.localbank.finance.sync.FirestoreSyncManager
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

private data class PayResult(
    val tx: Transaction,
    val paid: ScheduledExpense,
    val next: ScheduledExpense?,
    val account: Account?
)

class FinanceRepository(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val scheduledExpenseDao: ScheduledExpenseDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val pendingCaptureDao: PendingCaptureDao? = null,
    private val syncManager: FirestoreSyncManager? = null,
    private val householdId: String? = null,
    private val database: AppDatabase? = null
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

    suspend fun getAccountCounts(accountId: String): Pair<Int, Int> {
        val txCount = accountDao.countTransactions(accountId)
        val schedCount = accountDao.countScheduledExpenses(accountId)
        return txCount to schedCount
    }

    suspend fun deleteAccount(account: Account) {
        // O cascade local apaga transações e agendamentos da conta; no remoto é preciso apagar à mão
        val txIds = transactionDao.getAllOnce().filter { it.accountId == account.id }.map { it.id }
        val schedIds = scheduledExpenseDao.getAllOnce().filter { it.accountId == account.id }.map { it.id }
        accountDao.delete(account)
        syncPush {
            it.deleteRemoteIds(householdId!!, "transactions", txIds)
            it.deleteRemoteIds(householdId!!, "scheduled_expenses", schedIds)
            it.deleteRemote(householdId!!, "accounts", account.id)
        }
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

    fun getRecentTransactions(limit: Int): Flow<List<Transaction>> =
        transactionDao.getRecent(limit)

    fun getExpenseByCategory(from: Long, to: Long): Flow<List<TransactionDao.CategoryTotal>> =
        transactionDao.expenseByCategory(from, to)

    fun getMonthlyTotals(from: Long, to: Long): Flow<List<TransactionDao.MonthlyTotal>> =
        transactionDao.monthlyTotals(from, to)

    suspend fun getTotalIncome(from: Long, to: Long): Double =
        transactionDao.getTotalIncome(from, to)

    suspend fun getTotalExpense(from: Long, to: Long): Double =
        transactionDao.getTotalExpense(from, to)

    suspend fun getTotalExpenseByCategory(categoryId: String, from: Long, to: Long): Double =
        transactionDao.getTotalExpenseByCategory(categoryId, from, to)

    suspend fun insertTransaction(transaction: Transaction) {
        require(transaction.amount > 0) { "Valor deve ser positivo" }
        transactionDao.insert(transaction)
        syncPush { it.pushTransaction(householdId!!, transaction) }
        refreshBalances(transaction.accountId)
    }

    suspend fun updateTransaction(old: Transaction, new: Transaction) {
        transactionDao.insert(new)
        syncPush { it.pushTransaction(householdId!!, new) }
        refreshBalances(old.accountId, new.accountId)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.delete(transaction)
        syncPush { it.deleteRemote(householdId!!, "transactions", transaction.id) }

        // Se era pagamento de agendada, marca como não pago
        if (transaction.scheduledId != null) {
            val scheduled = scheduledExpenseDao.getById(transaction.scheduledId)
            if (scheduled != null) {
                scheduledExpenseDao.update(scheduled.copy(isPaid = false))
                syncPush { it.pushScheduledExpense(householdId!!, scheduled.copy(isPaid = false)) }
            }
        }

        refreshBalances(transaction.accountId)
    }

    suspend fun confirmCapture(
        capture: PendingCapture,
        accountId: String,
        categoryId: String?,
        description: String
    ) {
        val db = database
        val tx: Transaction
        if (db != null) {
            tx = db.withTransaction {
                val transaction = Transaction(
                    accountId = accountId,
                    categoryId = categoryId,
                    amount = capture.amount,
                    type = TransactionType.EXPENSE,
                    description = description,
                    date = capture.detectedAt
                )
                transactionDao.insert(transaction)
                accountDao.recalculateBalance(accountId)
                pendingCaptureDao?.delete(capture.id)
                transaction
            }
        } else {
            tx = Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = capture.amount,
                type = TransactionType.EXPENSE,
                description = description,
                date = capture.detectedAt
            )
            transactionDao.insert(tx)
            accountDao.recalculateBalance(accountId)
            pendingCaptureDao?.delete(capture.id)
        }
        // Só faz push após sucesso local
        syncPush { it.pushTransaction(householdId!!, tx) }
    }

    /** Recalcula o saldo das contas afetadas e publica o valor atualizado. */
    private suspend fun refreshBalances(vararg accountIds: String) {
        accountIds.distinct().forEach { id ->
            accountDao.recalculateBalance(id)
            accountDao.getById(id)?.let { acc -> syncPush { it.pushAccount(householdId!!, acc) } }
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
        // Se a data de vencimento mudou, zera o estágio de notificação
        val current = scheduledExpenseDao.getById(expense.id)
        val toUpdate = if (current != null && current.dueDate != expense.dueDate) {
            expense.copy(notifiedStage = 0)
        } else {
            expense
        }
        scheduledExpenseDao.update(toUpdate)
        syncPush { it.pushScheduledExpense(householdId!!, toUpdate) }
    }

    suspend fun deleteScheduled(expense: ScheduledExpense) {
        scheduledExpenseDao.delete(expense)
        syncPush { it.deleteRemote(householdId!!, "scheduled_expenses", expense.id) }
    }

    suspend fun getPendingNotification(now: Long, in7Days: Long): List<ScheduledExpense> =
        scheduledExpenseDao.getPendingNotification(now, in7Days)

    suspend fun markAsNotified(id: String) =
        scheduledExpenseDao.markAsNotified(id)

    /** Marca como avisada e envia a marcação ao Firestore. */
    suspend fun markNotified(expense: ScheduledExpense) {
        val nextStage = (expense.notifiedStage + 1).coerceAtMost(2)
        scheduledExpenseDao.markAsNotified(expense.id)
        syncPush { it.pushScheduledExpense(householdId!!, expense.copy(notifiedStage = nextStage)) }
    }

    suspend fun payScheduledExpense(expense: ScheduledExpense) {
        val payResult: PayResult
        val db = database
        if (db != null) {
            payResult = db.withTransaction { payScheduledExpenseInternal(expense) }
        } else {
            payResult = payScheduledExpenseInternal(expense)
        }
        // Só faz push após a transação local ter sucesso
        syncPush { it.pushTransaction(householdId!!, payResult.tx) }
        syncPush { it.pushScheduledExpense(householdId!!, payResult.paid) }
        if (payResult.next != null) {
            syncPush { it.pushScheduledExpense(householdId!!, payResult.next) }
        }
        if (payResult.account != null) {
            syncPush { it.pushAccount(householdId!!, payResult.account) }
        }
    }

    private suspend fun payScheduledExpenseInternal(expense: ScheduledExpense): PayResult {
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
        transactionDao.insert(transaction)
        accountDao.recalculateBalance(expense.accountId)

        var next: ScheduledExpense? = null
        // Criar próxima ocorrência automaticamente
        if (expense.isRecurring && expense.recurrenceRule != null) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = expense.dueDate }
            when (expense.recurrenceRule) {
                RecurrenceRule.DAILY   -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                RecurrenceRule.WEEKLY  -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                RecurrenceRule.MONTHLY -> cal.add(java.util.Calendar.MONTH, 1)
                RecurrenceRule.YEARLY  -> cal.add(java.util.Calendar.YEAR, 1)
            }
            next = expense.copy(
                id      = java.util.UUID.randomUUID().toString(),
                dueDate = cal.timeInMillis,
                isPaid  = false,
                notifiedStage = 0
            )
            scheduledExpenseDao.insert(next)
        }

        val account = accountDao.getById(expense.accountId)
        return PayResult(
            tx = transaction,
            paid = expense.copy(isPaid = true),
            next = next,
            account = account
        )
    }

    // ─────────────────────────────────────────────
    // Orçamentos
    // ─────────────────────────────────────────────

    fun getBudgetsByMonth(monthYear: String): Flow<List<Budget>> =
        budgetDao.getByMonth(monthYear)

    suspend fun getBudgetForCategory(categoryId: String, monthYear: String): Budget? =
        budgetDao.getByMonthOnce(monthYear).find { it.categoryId == categoryId }

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
        savingsGoalDao.getAll()

    suspend fun insertSavingsGoal(goal: SavingsGoal) {
        savingsGoalDao.insert(goal)
        syncPush { it.pushSavingsGoal(householdId!!, goal) }
    }

    /** Atualiza nome, alvo, cor e prazo — o valor guardado só muda por [depositToGoal]. */
    suspend fun updateSavingsGoalMeta(goal: SavingsGoal) {
        savingsGoalDao.updateMeta(goal.id, goal.name, goal.targetAmount, goal.colorHex, goal.deadline)
        syncPush { it.pushSavingsGoalMeta(householdId!!, goal) }
    }

    suspend fun depositToGoal(goalId: String, amount: Double) {
        val dao = savingsGoalDao
        val before = dao.getById(goalId)?.savedAmount ?: return
        dao.addToSaved(goalId, amount)
        val applied = (dao.getById(goalId)?.savedAmount ?: before) - before
        if (applied > 0) syncPush { it.incrementGoalSaved(householdId!!, goalId, applied) }
    }

    suspend fun deleteSavingsGoal(goal: SavingsGoal) {
        savingsGoalDao.delete(goal)
        syncPush { it.deleteRemote(householdId!!, "savings_goals", goal.id) }
    }

    // ─────────────────────────────────────────────
    // Sync helper
    // ─────────────────────────────────────────────

    private fun syncPush(action: (FirestoreSyncManager) -> Unit) {
        val sync = syncManager ?: return
        val hid = householdId ?: return
        try { action(sync) } catch (e: Exception) {
            android.util.Log.w("FinanceRepository", "Sync failed (offline first): ${e.message}", e)
        }
    }
}