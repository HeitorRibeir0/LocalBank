package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.auth.AuthManager
import com.localbank.finance.data.model.*
import com.localbank.finance.data.repository.FinanceRepository
import com.localbank.finance.ui.util.normalizeCategoryName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class ExpenseViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _budgetAlert = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val budgetAlert: SharedFlow<String> = _budgetAlert

    val transactions: StateFlow<List<Transaction>> = repository
        .getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val scheduledExpenses: StateFlow<List<ScheduledExpense>> = repository
        .getAllScheduled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<Category>> = repository
        .getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<Account>> = repository
        .getAllAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTransaction(
        accountId: String,
        categoryId: String?,
        amount: Double,
        type: TransactionType,
        description: String,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                Transaction(
                    accountId   = accountId,
                    categoryId  = categoryId,
                    amount      = amount,
                    type        = type,
                    description = description,
                    date        = date,
                    createdBy   = AuthManager.displayName
                )
            )
            if (type == TransactionType.EXPENSE && categoryId != null) {
                checkBudgetAlert(categoryId)
            }
        }
    }

    private suspend fun checkBudgetAlert(categoryId: String) {
        val cal = Calendar.getInstance()
        val monthYear = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)

        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val to = cal.timeInMillis

        val budget = repository.getBudgetForCategory(categoryId, monthYear) ?: return
        val spent = repository.getSpentForCategory(categoryId, from, to)
        val pct = spent / budget.limitAmount
        val categoryName = categories.value.find { it.id == categoryId }?.name ?: "categoria"

        when {
            pct >= 1.0 -> _budgetAlert.tryEmit(
                "⚠ Limite de $categoryName estourado! Gasto: ${String.format("%.0f", pct * 100)}% do orçamento."
            )
            pct >= 0.8 -> _budgetAlert.tryEmit(
                "Atenção: $categoryName atingiu ${String.format("%.0f", pct * 100)}% do limite mensal."
            )
        }
    }

    fun updateTransaction(old: Transaction, new: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(old, new)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun addScheduledExpense(
        accountId: String,
        categoryId: String?,
        amount: Double,
        description: String,
        dueDate: Long,
        isRecurring: Boolean = false,
        recurrenceRule: RecurrenceRule? = null
    ) {
        viewModelScope.launch {
            repository.insertScheduled(
                ScheduledExpense(
                    accountId      = accountId,
                    categoryId     = categoryId,
                    amount         = amount,
                    description    = description,
                    dueDate        = dueDate,
                    isRecurring    = isRecurring,
                    recurrenceRule = recurrenceRule
                )
            )
        }
    }

    fun payScheduledExpense(expense: ScheduledExpense) {
        viewModelScope.launch {
            repository.payScheduledExpense(expense)
        }
    }

    fun deleteScheduledExpense(expense: ScheduledExpense) {
        viewModelScope.launch {
            repository.deleteScheduled(expense)
        }
    }

    fun updateScheduledExpense(expense: ScheduledExpense) {
        viewModelScope.launch {
            repository.updateScheduled(expense)
        }
    }

    // ── Categorias ──

    fun addCategory(name: String, colorHex: String, type: TransactionType) {
        viewModelScope.launch {
            val canonical = normalizeCategoryName(name)
            val deterministicId = UUID.nameUUIDFromBytes(
                "${type.name}:${canonical.lowercase()}".toByteArray()
            ).toString()
            val existing = repository.getCategoryById(deterministicId)
            if (existing == null) {
                repository.insertCategory(
                    Category(id = deterministicId, name = canonical, icon = "category",
                        colorHex = colorHex, type = type)
                )
            } else if (existing.name != canonical) {
                // Corrige o nome para a forma canônica se já existia com capitalização diferente
                repository.updateCategory(existing.copy(name = canonical))
            }
        }
    }

    // ── Contas ──

    fun addAccount(name: String, type: AccountType) {
        viewModelScope.launch {
            repository.insertAccount(
                Account(name = name, type = type, balance = 0.0)
            )
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            repository.deleteAccount(account)
        }
    }
}
