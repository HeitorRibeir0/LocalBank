package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.repository.FinanceRepository
import com.localbank.finance.util.DateRange
import com.localbank.finance.util.DateRanges
import kotlinx.coroutines.Dispatchers
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

    // ── Mês selecionado pelo usuário ──
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())
    val selectedMonth: StateFlow<Calendar> = _selectedMonth.asStateFlow()

    fun navigateMonth(delta: Int) {
        val c = _selectedMonth.value.clone() as Calendar
        c.add(Calendar.MONTH, delta)
        _selectedMonth.value = c
    }

    fun isCurrentMonth(): Boolean =
        DateRanges.isSameMonth(_selectedMonth.value, Calendar.getInstance())

    val uiState: StateFlow<ReportUiState> = combine(
        repository.getAllTransactions(),
        repository.getAllCategories(),
        _selectedMonth
    ) { transactions, categories, selectedCal ->
        buildReport(transactions, categories, selectedCal)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportUiState()
    )
}

/** Cálculo puro do relatório — separado do ViewModel para poder ser testado. */
internal fun buildReport(
    transactions: List<Transaction>,
    categories: List<Category>,
    selectedCal: Calendar
): ReportUiState {
    val locale = Locale.forLanguageTag("pt-BR")
    val categoriesById = categories.associateBy { it.id }

    // ── Uma passada: totais de entrada e saída por mês ──
    val incomeByMonth = HashMap<Int, Double>()
    val expenseByMonth = HashMap<Int, Double>()
    val cursor = Calendar.getInstance()
    transactions.forEach { tx ->
        cursor.timeInMillis = tx.date
        val totals = if (tx.type == TransactionType.INCOME) incomeByMonth else expenseByMonth
        totals.merge(DateRanges.monthIndex(cursor), tx.amount) { a, b -> a + b }
    }
    fun income(idx: Int) = incomeByMonth[idx] ?: 0.0
    fun expense(idx: Int) = expenseByMonth[idx] ?: 0.0

    fun expensesByCategory(range: DateRange) = transactions
        .filter { it.type == TransactionType.EXPENSE && it.date in range }
        .groupBy { it.categoryId }
        .mapValues { (_, txns) -> txns.sumOf { it.amount } }

    val selectedIdx = DateRanges.monthIndex(selectedCal)

    // ── Gastos por categoria no mês selecionado ──
    val monthByCategory = expensesByCategory(DateRanges.month(selectedCal))
    val totalExpense = monthByCategory.values.sum()
    val categoryExpenses = monthByCategory
        .map { (catId, total) ->
            val category = categoriesById[catId]
            CategoryExpense(
                categoryName = category?.name ?: "Sem categoria",
                colorHex     = category?.colorHex ?: "#9E9E9E",
                totalAmount  = total,
                percentage   = if (totalExpense > 0) (total / totalExpense * 100).toFloat() else 0f
            )
        }
        .sortedByDescending { it.totalAmount }

    // ── Comparativo: 6 meses até o mês selecionado ──
    val sdf = SimpleDateFormat("MMM/yy", locale)
    val monthlyComparison = (5 downTo 0).map { monthsAgo ->
        val c = (selectedCal.clone() as Calendar).apply { add(Calendar.MONTH, -monthsAgo) }
        val idx = selectedIdx - monthsAgo
        MonthlyComparison(sdf.format(c.time).replaceFirstChar { it.uppercase() }, income(idx), expense(idx))
    }

    // ── Panorama semestral (baseado no mês selecionado) ──
    val selectedYear = selectedCal.get(Calendar.YEAR)
    val isFirstSemester = selectedCal.get(Calendar.MONTH) < 6
    val semesterStartMonth = if (isFirstSemester) 0 else 6
    val semesterLabel = "${if (isFirstSemester) "1º" else "2º"} Semestre $selectedYear"
    val monthLabelSdf = SimpleDateFormat("MMM", locale)

    val semesterMonths = (0 until 6).map { offset ->
        val c = (selectedCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, semesterStartMonth + offset)
        }
        val idx = DateRanges.monthIndex(c)
        Triple(monthLabelSdf.format(c.time).replaceFirstChar { it.uppercase() }, income(idx), expense(idx))
    }

    val semesterIncome  = semesterMonths.sumOf { it.second }
    val semesterExpense = semesterMonths.sumOf { it.third }

    val activeMonths = semesterMonths.filter { it.third > 0 }
    val bestMonth  = activeMonths.minByOrNull { it.third }?.first ?: "-"
    val worstMonth = activeMonths.maxByOrNull { it.third }?.first ?: "-"

    val topCategoryId = expensesByCategory(DateRanges.semester(selectedCal)).maxByOrNull { it.value }?.key
    val topCategory = topCategoryId?.let { categoriesById[it] }
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

    return ReportUiState(
        categoryExpenses      = categoryExpenses,
        monthlyComparison     = monthlyComparison,
        totalExpenseThisMonth = totalExpense,
        semesterReport        = semesterReport
    )
}
