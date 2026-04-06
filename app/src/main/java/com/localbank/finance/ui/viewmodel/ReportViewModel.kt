package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

data class CategoryExpense(
    val categoryName: String,
    val colorHex: String,
    val totalAmount: Double,
    val percentage: Float       // 0–100
)

data class MonthlyComparison(
    val monthLabel: String,     // "Mar/26"
    val income: Double,
    val expense: Double
)

data class SemesterReport(
    val label: String,                          // "1º Semestre 2026"
    val totalIncome: Double,
    val totalExpense: Double,
    val balance: Double,
    val bestMonth: String,                      // mês com menor gasto
    val worstMonth: String,                     // mês com maior gasto
    val topCategory: String,                    // categoria mais gasta
    val topCategoryColor: String,
    val savingsRate: Float                      // % da renda economizada
)

data class ReportUiState(
    val categoryExpenses: List<CategoryExpense> = emptyList(),
    val monthlyComparison: List<MonthlyComparison> = emptyList(),
    val totalExpenseThisMonth: Double = 0.0,
    val semesterReport: SemesterReport? = null
)

class ReportViewModel(private val repository: FinanceRepository) : ViewModel() {

    private fun getMonthRange(cal: Calendar): Pair<Long, Long> {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis

        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        val to = c.timeInMillis
        return Pair(from, to)
    }

    val uiState: StateFlow<ReportUiState> = combine(
        repository.getAllTransactions(),
        repository.getAllCategories()
    ) { transactions, categories ->

        val now = Calendar.getInstance()
        val (monthFrom, monthTo) = getMonthRange(now)

        // ── Gastos por categoria no mês atual ──
        val monthExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE && it.date in monthFrom..monthTo
        }
        val totalExpense = monthExpenses.sumOf { it.amount }

        val categoryExpenses = monthExpenses
            .groupBy { it.categoryId }
            .map { (catId, txns) ->
                val category = categories.find { it.id == catId }
                val total = txns.sumOf { it.amount }
                CategoryExpense(
                    categoryName = category?.name ?: "Sem categoria",
                    colorHex     = category?.colorHex ?: "#9E9E9E",
                    totalAmount  = total,
                    percentage   = if (totalExpense > 0) (total / totalExpense * 100).toFloat() else 0f
                )
            }
            .sortedByDescending { it.totalAmount }

        // ── Comparativo dos últimos 6 meses ──
        val sdf = SimpleDateFormat("MMM/yy", Locale("pt", "BR"))
        val monthlyComparison = (5 downTo 0).map { monthsAgo ->
            val c = Calendar.getInstance()
            c.add(Calendar.MONTH, -monthsAgo)
            val (from, to) = getMonthRange(c)
            val label = sdf.format(c.time).replaceFirstChar { it.uppercase() }
            val income = transactions
                .filter { it.type == TransactionType.INCOME && it.date in from..to }
                .sumOf { it.amount }
            val expense = transactions
                .filter { it.type == TransactionType.EXPENSE && it.date in from..to }
                .sumOf { it.amount }
            MonthlyComparison(label, income, expense)
        }

        // ── Panorama semestral ──
        val currentMonth = now.get(Calendar.MONTH)  // 0-based
        val currentYear  = now.get(Calendar.YEAR)
        val isFirstSemester = currentMonth < 6
        val semesterStartMonth = if (isFirstSemester) 0 else 6
        val semesterLabel = "${if (isFirstSemester) "1º" else "2º"} Semestre $currentYear"
        val monthLabelSdf = SimpleDateFormat("MMM", Locale("pt", "BR"))

        val semesterMonths = (semesterStartMonth until semesterStartMonth + 6).map { month ->
            val c = Calendar.getInstance()
            c.set(Calendar.YEAR, currentYear)
            c.set(Calendar.MONTH, month)
            val (from, to) = getMonthRange(c)
            val label = monthLabelSdf.format(c.time).replaceFirstChar { it.uppercase() }
            val inc = transactions.filter { it.type == TransactionType.INCOME && it.date in from..to }.sumOf { it.amount }
            val exp = transactions.filter { it.type == TransactionType.EXPENSE && it.date in from..to }.sumOf { it.amount }
            Triple(label, inc, exp)
        }

        val semesterIncome  = semesterMonths.sumOf { it.second }
        val semesterExpense = semesterMonths.sumOf { it.third }

        val activeMonths = semesterMonths.filter { it.third > 0 }
        val bestMonth  = activeMonths.minByOrNull { it.third }?.first ?: "-"
        val worstMonth = activeMonths.maxByOrNull { it.third }?.first ?: "-"

        val semesterTxExpenses = transactions.filter {
            it.type == TransactionType.EXPENSE && run {
                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                c.get(Calendar.YEAR) == currentYear && c.get(Calendar.MONTH) in semesterStartMonth until semesterStartMonth + 6
            }
        }
        val topCatEntry = semesterTxExpenses.groupBy { it.categoryId }
            .maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }
        val topCategory = categories.find { it.id == topCatEntry?.key }
        val savingsRate = if (semesterIncome > 0)
            ((semesterIncome - semesterExpense) / semesterIncome * 100).toFloat().coerceIn(0f, 100f)
        else 0f

        val semesterReport = if (semesterExpense > 0 || semesterIncome > 0) SemesterReport(
            label            = semesterLabel,
            totalIncome      = semesterIncome,
            totalExpense     = semesterExpense,
            balance          = semesterIncome - semesterExpense,
            bestMonth        = bestMonth,
            worstMonth       = worstMonth,
            topCategory      = topCategory?.name ?: "Sem dados",
            topCategoryColor = topCategory?.colorHex ?: "#9E9E9E",
            savingsRate      = savingsRate
        ) else null

        ReportUiState(
            categoryExpenses      = categoryExpenses,
            monthlyComparison     = monthlyComparison,
            totalExpenseThisMonth = totalExpense,
            semesterReport        = semesterReport
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportUiState()
    )
}
