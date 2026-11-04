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
        syncPush { s, hid -> s.pushAccount(hid, account) }
    }

    suspend fun updateAccount(account: Account) {
        accountDao.update(account)
        syncPush { s, hid -> s.pushAccount(hid, account) }
    }

    suspend fun getAccountCounts(accountId: String): Pair<Int, Int> {
        val txCount = accountDao.countTransactions(accountId)
        val schedCount = accountDao.countScheduledExpenses(accountId)
        return txCount to schedCount
    }

    suspend fun setAccountArchived(account: Account, archived: Boolean) {
        accountDao.setArchived(account.id, archived)
        syncPush { s, hid -> s.pushAccount(hid, account.copy(archived = archived)) }
    }

    suspend fun deleteAccount(account: Account) {
        // O cascade local apaga transações e agendamentos da conta; no remoto é preciso apagar à mão
        val txIds = transactionDao.getAllOnce().filter { it.accountId == account.id }.map { it.id }
        val schedIds = scheduledExpenseDao.getAllOnce().filter { it.accountId == account.id }.map { it.id }
        accountDao.delete(account)
        syncPush { s, hid ->
            s.deleteRemoteIds(hid, "transactions", txIds)
            s.deleteRemoteIds(hid, "scheduled_expenses", schedIds)
            s.deleteRemote(hid, "accounts", account.id)
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
        syncPush { s, hid -> s.pushCategory(hid, category) }
    }

    suspend fun insertCategory(category: Category) {
        categoryDao.insert(category)
        syncPush { s, hid -> s.pushCategory(hid, category) }
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

    suspend fun insertTransaction(transaction: Transaction) {
        require(transaction.amountCents > 0) { "Valor deve ser positivo" }
        transactionDao.insert(transaction)
        syncPush { s, hid -> s.pushTransaction(hid, transaction) }
        refreshBalances(transaction.accountId)
    }

    suspend fun updateTransaction(old: Transaction, new: Transaction) {
        require(new.amountCents > 0) { "Valor deve ser positivo" }
        transactionDao.insert(new)
        syncPush { s, hid -> s.pushTransaction(hid, new) }
        refreshBalances(old.accountId, new.accountId)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.delete(transaction)
        syncPush { s, hid -> s.deleteRemote(hid, "transactions", transaction.id) }

        // Se era pagamento de agendada, marca como não pago
        if (transaction.scheduledId != null) {
            val scheduled = scheduledExpenseDao.getById(transaction.scheduledId)
            if (scheduled != null) {
                scheduledExpenseDao.update(scheduled.copy(isPaid = false))
                syncPush { s, hid -> s.pushScheduledExpense(hid, scheduled.copy(isPaid = false)) }
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
                    amountCents = capture.amountCents,
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
                amountCents = capture.amountCents,
                type = TransactionType.EXPENSE,
                description = description,
                date = capture.detectedAt
            )
            transactionDao.insert(tx)
            accountDao.recalculateBalance(accountId)
            pendingCaptureDao?.delete(capture.id)
        }
        // Só faz push após sucesso local
        syncPush { s, hid -> s.pushTransaction(hid, tx) }
    }

    /** Recalcula o saldo das contas afetadas e publica o valor atualizado. */
    private suspend fun refreshBalances(vararg accountIds: String) {
        accountIds.distinct().forEach { id ->
            accountDao.recalculateBalance(id)
            accountDao.getById(id)?.let { acc -> syncPush { s, hid -> s.pushAccount(hid, acc) } }
        }
    }

    // ─────────────────────────────────────────────
    // Despesas agendadas
    // ─────────────────────────────────────────────

    fun getAllScheduled(): Flow<List<ScheduledExpense>> =
        scheduledExpenseDao.getAllScheduled()

    suspend fun insertScheduled(expense: ScheduledExpense) {
        scheduledExpenseDao.insert(expense)
        syncPush { s, hid -> s.pushScheduledExpense(hid, expense) }
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
        syncPush { s, hid -> s.pushScheduledExpense(hid, toUpdate) }
    }

    suspend fun deleteScheduled(expense: ScheduledExpense) {
        scheduledExpenseDao.delete(expense)
        syncPush { s, hid -> s.deleteRemote(hid, "scheduled_expenses", expense.id) }
    }

    suspend fun getPendingNotification(until: Long): List<ScheduledExpense> =
        scheduledExpenseDao.getPendingNotification(until)

    // Só local: cada aparelho do lar avisa a sua pessoa
    suspend fun setNotifiedStage(id: String, stage: Int) =
        scheduledExpenseDao.setNotifiedStage(id, stage)

    suspend fun payScheduledExpense(expense: ScheduledExpense) {
        val payResult: PayResult
        val db = database
        if (db != null) {
            payResult = db.withTransaction { payScheduledExpenseInternal(expense) }
        } else {
            payResult = payScheduledExpenseInternal(expense)
        }
        // Só faz push após a transação local ter sucesso
        syncPush { s, hid -> s.pushTransaction(hid, payResult.tx) }
        syncPush { s, hid -> s.pushScheduledExpense(hid, payResult.paid) }
        if (payResult.next != null) {
            syncPush { s, hid -> s.pushScheduledExpense(hid, payResult.next) }
        }
        if (payResult.account != null) {
            syncPush { s, hid -> s.pushAccount(hid, payResult.account) }
        }
    }

    private suspend fun payScheduledExpenseInternal(expense: ScheduledExpense): PayResult {
        scheduledExpenseDao.markAsPaid(expense.id)
        val transaction = Transaction(
            accountId   = expense.accountId,
            categoryId  = expense.categoryId,
            amountCents = expense.amountCents,
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

    suspend fun getSpentForCategory(categoryId: String, from: Long, to: Long): Long =
        transactionDao.getTotalExpenseByCategory(categoryId, from, to)

    fun getAllBudgets(): Flow<List<Budget>> =
        budgetDao.getAllBudgets()

    suspend fun insertBudget(budget: Budget) {
        budgetDao.insert(budget)
        syncPush { s, hid -> s.pushBudget(hid, budget) }
    }

    suspend fun updateBudget(budget: Budget) {
        budgetDao.update(budget)
        syncPush { s, hid -> s.pushBudget(hid, budget) }
    }

    suspend fun deleteBudget(budget: Budget) {
        budgetDao.delete(budget)
        syncPush { s, hid -> s.deleteRemote(hid, "budgets", budget.id) }
    }

    // ─────────────────────────────────────────────
    // Metas de economia
    // ─────────────────────────────────────────────

    fun getAllSavingsGoals(): Flow<List<SavingsGoal>> =
        savingsGoalDao.getAll()

    suspend fun insertSavingsGoal(goal: SavingsGoal) {
        savingsGoalDao.insert(goal)
        syncPush { s, hid -> s.pushSavingsGoal(hid, goal) }
    }

    /** Atualiza nome, alvo, cor e prazo — o valor guardado só muda por [depositToGoal]. */
    suspend fun updateSavingsGoalMeta(goal: SavingsGoal) {
        savingsGoalDao.updateMeta(goal.id, goal.name, goal.targetCents, goal.colorHex, goal.deadline)
        syncPush { s, hid -> s.pushSavingsGoalMeta(hid, goal) }
    }

    suspend fun depositToGoal(goalId: String, cents: Long) {
        val dao = savingsGoalDao
        val before = dao.getById(goalId)?.savedCents ?: return
        dao.addToSaved(goalId, cents)
        val applied = (dao.getById(goalId)?.savedCents ?: before) - before
        if (applied > 0) syncPush { s, hid -> s.incrementGoalSaved(hid, goalId, applied) }
    }

    suspend fun deleteSavingsGoal(goal: SavingsGoal) {
        savingsGoalDao.delete(goal)
        syncPush { s, hid -> s.deleteRemote(hid, "savings_goals", goal.id) }
    }

    // ─────────────────────────────────────────────
    // Sync helper
    // ─────────────────────────────────────────────

    private inline fun syncPush(action: (FirestoreSyncManager, String) -> Unit) {
        val sync = syncManager ?: return
        val hid = householdId ?: return
        try { action(sync, hid) } catch (e: Exception) {
            android.util.Log.w("FinanceRepository", "push falhou", e)
        }
    }
}
