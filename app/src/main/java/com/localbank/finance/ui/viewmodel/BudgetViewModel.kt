package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.*
import com.localbank.finance.data.repository.FinanceRepository
import com.localbank.finance.util.DateRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BudgetWithProgress(
    val budget: Budget,
    val categoryName: String,
    val categoryColorHex: String,
    val spentCents: Long,
    val percentage: Float   // 0.0 a 1.0+  (pode passar de 1.0 se estourou)
)

data class BudgetUiState(
    val budgets: List<BudgetWithProgress> = emptyList(),
    val categories: List<Category> = emptyList()
)

class BudgetViewModel(private val repository: FinanceRepository) : ViewModel() {

    // Mês corrente é re-checado a cada minuto: se o app ficar aberto na virada do mês,
    // a lista de orçamentos acompanha o mês novo (antes ficava presa no mês em que o ViewModel nasceu).
    private val monthKeyFlow: Flow<String> = flow {
        while (true) {
            emit(DateRanges.monthKey())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<BudgetUiState> = monthKeyFlow.flatMapLatest { monthKey ->
        val month = DateRanges.month()
        combine(
            repository.getBudgetsByMonth(monthKey),
            repository.getAllCategories(),
            repository.getExpenseByCategory(month.from, month.to)
        ) { budgets, categories, expenseByCategory ->

            val spentByCategory = expenseByCategory.associate { it.categoryId to it.total }
            val categoriesById = categories.associateBy { it.id }

            val budgetsWithProgress = budgets.map { budget ->
                val category = categoriesById[budget.categoryId]
                val spent = spentByCategory[budget.categoryId] ?: 0L

                BudgetWithProgress(
                    budget           = budget,
                    categoryName     = category?.displayName ?: "Sem categoria",
                    categoryColorHex = category?.colorHex ?: "#9E9E9E",
                    spentCents       = spent,
                    percentage       = if (budget.limitCents > 0) spent.toFloat() / budget.limitCents else 0f
                )
            }.sortedByDescending { it.percentage }

            BudgetUiState(
                budgets    = budgetsWithProgress,
                categories = categories
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetUiState()
    )

    /**
     * Define o limite mensal da categoria. Se a categoria já tem limite neste mês,
     * atualiza o existente em vez de criar um segundo (evita duplicidade).
     */
    fun addBudget(categoryId: String, limitCents: Long) {
        viewModelScope.launch {
            val monthKey = DateRanges.monthKey()
            val existing = repository.getBudgetForCategory(categoryId, monthKey)
            if (existing != null) {
                repository.updateBudget(existing.copy(limitCents = limitCents))
            } else {
                repository.insertBudget(
                    Budget(
                        categoryId  = categoryId,
                        monthYear   = monthKey,
                        limitCents  = limitCents
                    )
                )
            }
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }
}
