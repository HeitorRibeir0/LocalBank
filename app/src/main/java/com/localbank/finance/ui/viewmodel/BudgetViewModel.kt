package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.*
import com.localbank.finance.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class BudgetWithProgress(
    val budget: Budget,
    val categoryName: String,
    val categoryColorHex: String,
    val spent: Double,
    val percentage: Float   // 0.0 a 1.0+  (pode passar de 1.0 se estourou)
)

data class BudgetUiState(
    val budgets: List<BudgetWithProgress> = emptyList(),
    val categories: List<Category> = emptyList()
)

class BudgetViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val currentMonthYear: String
        get() {
            val cal = Calendar.getInstance()
            return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }

    private val monthRange: Pair<Long, Long>
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val from = cal.timeInMillis

            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            val to = cal.timeInMillis

            return Pair(from, to)
        }

    val uiState: StateFlow<BudgetUiState> = combine(
        repository.getBudgetsByMonth(currentMonthYear),
        repository.getAllCategories(),
        repository.getAllTransactions()
    ) { budgets, categories, transactions ->

        val (from, to) = monthRange
        val monthExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE && it.date in from..to
        }

        val budgetsWithProgress = budgets.map { budget ->
            val category = categories.find { it.id == budget.categoryId }
            val spent = monthExpenses
                .filter { it.categoryId == budget.categoryId }
                .sumOf { it.amount }

            BudgetWithProgress(
                budget           = budget,
                categoryName     = category?.name ?: "Sem categoria",
                categoryColorHex = category?.colorHex ?: "#9E9E9E",
                spent            = spent,
                percentage       = if (budget.limitAmount > 0) (spent / budget.limitAmount).toFloat() else 0f
            )
        }.sortedByDescending { it.percentage }

        BudgetUiState(
            budgets    = budgetsWithProgress,
            categories = categories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetUiState()
    )

    fun addBudget(categoryId: String, limitAmount: Double) {
        viewModelScope.launch {
            repository.insertBudget(
                Budget(
                    categoryId  = categoryId,
                    monthYear   = currentMonthYear,
                    limitAmount = limitAmount
                )
            )
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }
}
