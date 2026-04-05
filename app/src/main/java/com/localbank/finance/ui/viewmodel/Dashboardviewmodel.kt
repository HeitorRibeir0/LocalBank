package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class CategoryExpenseSlice(
    val categoryName: String,
    val colorHex: String,
    val amount: Double
)

data class DashboardUiState(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val recentTransactions: List<Transaction> = emptyList(),
    val upcomingExpenses: List<ScheduledExpense> = emptyList(),
    val categoryExpenses: List<CategoryExpenseSlice> = emptyList()
)

class DashboardViewModel(private val repository: FinanceRepository) : ViewModel() {

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

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllTransactions(),
        repository.getAllScheduled(),
        repository.getAllCategories()
    ) { transactions, scheduled, categories ->

        val (from, to) = monthRange
        val monthTransactions = transactions.filter { it.date in from..to }

        val income  = monthTransactions.filter { it.type == TransactionType.INCOME  }.sumOf { it.amount }
        val expense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        val now = System.currentTimeMillis()
        val in30Days = now + 30L * 24 * 60 * 60 * 1000
        val upcoming = scheduled
            .filter { !it.isPaid && it.dueDate <= in30Days }
            .sortedBy { it.dueDate }

        // gastos por categoria no mês (para o gráfico donut)
        val categoryExpenses = monthTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .map { (catId, txns) ->
                val cat = categories.find { it.id == catId }
                CategoryExpenseSlice(
                    categoryName = cat?.name ?: "Sem categoria",
                    colorHex     = cat?.colorHex ?: "#9E9E9E",
                    amount       = txns.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.amount }

        DashboardUiState(
            totalIncome        = income,
            totalExpense       = expense,
            balance            = income - expense,
            recentTransactions = transactions.take(5),
            upcomingExpenses   = upcoming,
            categoryExpenses   = categoryExpenses
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}
