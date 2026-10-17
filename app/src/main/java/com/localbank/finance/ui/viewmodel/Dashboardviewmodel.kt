package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.repository.FinanceRepository
import com.localbank.finance.util.DateRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class CategoryExpenseSlice(
    val categoryName: String,
    val colorHex: String,
    val amount: Double
)

data class DashboardUiState(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val projectedBalance: Double = 0.0,
    val pendingScheduledTotal: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    val urgentExpenses: List<ScheduledExpense> = emptyList(),    // vence em até 3 dias
    val upcomingExpenses: List<ScheduledExpense> = emptyList(),  // demais dos próximos 30 dias
    val categoryExpenses: List<CategoryExpenseSlice> = emptyList()
)

class DashboardViewModel(private val repository: FinanceRepository) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllTransactions(),
        repository.getAllScheduled(),
        repository.getAllCategories()
    ) { transactions, scheduled, categories ->

        val month = DateRanges.month()
        val monthTransactions = transactions.filter { it.date in month }

        val income  = monthTransactions.filter { it.type == TransactionType.INCOME  }.sumOf { it.amount }
        val expense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        val now = System.currentTimeMillis()
        val in30Days = now + 30 * DAY_MS
        val pending = scheduled.filter { !it.isPaid }
        val (urgent, upcoming) = pending
            .filter { it.dueDate <= in30Days }
            .sortedBy { it.dueDate }
            .partition { (it.dueDate - now) / DAY_MS <= 3 }
        val pendingTotal = pending.sumOf { it.amount }

        val categoriesById = categories.associateBy { it.id }

        // gastos por categoria no mês (para o gráfico donut)
        val categoryExpenses = monthTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .map { (catId, txns) ->
                val cat = categoriesById[catId]
                CategoryExpenseSlice(
                    categoryName = cat?.name ?: "Sem categoria",
                    colorHex     = cat?.colorHex ?: "#9E9E9E",
                    amount       = txns.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.amount }

        DashboardUiState(
            totalIncome           = income,
            totalExpense          = expense,
            balance               = income - expense,
            projectedBalance      = (income - expense) - pendingTotal,
            pendingScheduledTotal = pendingTotal,
            recentTransactions    = transactions.take(5),
            urgentExpenses        = urgent,
            upcomingExpenses      = upcoming,
            categoryExpenses      = categoryExpenses
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
