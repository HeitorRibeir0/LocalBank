package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.*
import com.localbank.finance.data.repository.FinanceRepository
import com.localbank.finance.util.DateRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
        get() = DateRanges.monthKey()

    val uiState: StateFlow<BudgetUiState> = combine(
        repository.getBudgetsByMonth(currentMonthYear),
        repository.getAllCategories(),
        repository.getAllTransactions()
    ) { budgets, categories, transactions ->

        val month = DateRanges.month()
        val spentByCategory = transactions
            .filter { it.type == TransactionType.EXPENSE && it.date in month }
            .groupBy { it.categoryId }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
        val categoriesById = categories.associateBy { it.id }

        val budgetsWithProgress = budgets.map { budget ->
            val category = categoriesById[budget.categoryId]
            val spent = spentByCategory[budget.categoryId] ?: 0.0

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
    }.flowOn(Dispatchers.Default).stateIn(
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
