package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.auth.AuthManager
import com.localbank.finance.data.model.*
import com.localbank.finance.data.repository.FinanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ExpenseViewModel(private val repository: FinanceRepository) : ViewModel() {

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
            val trimmed = name.trim()
            // ID determinístico: mesma categoria sempre gera o mesmo ID em qualquer device
            val deterministicId = UUID.nameUUIDFromBytes(
                "${type.name}:${trimmed.lowercase()}".toByteArray()
            ).toString()
            // Só cria se ainda não existe (evita duplicatas no sync)
            if (repository.getCategoryById(deterministicId) == null) {
                repository.insertCategory(
                    Category(id = deterministicId, name = trimmed, icon = "category",
                        colorHex = colorHex, type = type)
                )
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
