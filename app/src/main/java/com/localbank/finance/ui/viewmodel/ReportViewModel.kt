package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.database.TransactionDao
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.displayName
import com.localbank.finance.data.repository.FinanceRepository
import com.localbank.finance.util.DateRanges
import com.localbank.finance.util.roundCents
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

    val uiState: StateFlow<ReportUiState> = _selectedMonth.flatMapLatest { selectedCal ->
        val semesterRange = DateRanges.semester(selectedCal)
        val monthRange = DateRanges.month(selectedCal)
        val fiveMonthsBefore = (selectedCal.clone() as Calendar).apply { add(Calendar.MONTH, -5) }
        // Cobre o comparativo (6 meses até o selecionado) e o semestre do mês selecionado
        val from = minOf(DateRanges.month(fiveMonthsBefore).from, semesterRange.from)
        val to = maxOf(monthRange.to, semesterRange.to)

        combine(
            repository.getMonthlyTotals(from, to),
            repository.getExpenseByCategory(monthRange.from, monthRange.to),
            repository.getExpenseByCategory(semesterRange.from, semesterRange.to),
            repository.getAllCategories()
        ) { monthlyTotals, monthExpenses, semesterExpenses, categories ->
            buildReport(
                monthlyTotals = monthlyTotals,
                monthExpensesByCategory = monthExpenses,
                semesterExpensesByCategory = semesterExpenses,
                categories = categories,
                selectedCal = selectedCal
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportUiState()
    )
}

/** Cálculo puro do relatório a partir dos totais agregados no SQL — separado para poder ser testado. */
internal fun buildReport(
    monthlyTotals: List<TransactionDao.MonthlyTotal>,
    monthExpensesByCategory: List<TransactionDao.CategoryTotal>,
    semesterExpensesByCategory: List<TransactionDao.CategoryTotal>,
    categories: List<Category>,
    selectedCal: Calendar
): ReportUiState {
    val locale = Locale.forLanguageTag("pt-BR")
    val categoriesById = categories.associateBy { it.id }

    // ── Mapa de totais por mês (de 6 meses atrás até agora) ──
    val incomeByMonthYm = HashMap<String, Double>()
    val expenseByMonthYm = HashMap<String, Double>()
    monthlyTotals.forEach { (monthYm, type, total) ->
        if (type == "INCOME") {
            incomeByMonthYm[monthYm] = total
        } else {
            expenseByMonthYm[monthYm] = total
        }
    }

    // ── Gastos por categoria no mês selecionado ──
    val totalExpense = monthExpensesByCategory.sumOf { it.total }.roundCents()
    val categoryExpenses = monthExpensesByCategory
        .map { (catId, total) ->
            val category = categoriesById[catId]
            CategoryExpense(
                categoryName = category?.displayName ?: "Sem categoria",
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
        val monthYm = SimpleDateFormat("yyyy-MM", Locale.US).format(c.time)
        MonthlyComparison(
            sdf.format(c.time).replaceFirstChar { it.uppercase() },
            incomeByMonthYm[monthYm] ?: 0.0,
            expenseByMonthYm[monthYm] ?: 0.0
        )
    }

    // ── Panorama semestral ──
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
        val monthYm = SimpleDateFormat("yyyy-MM", Locale.US).format(c.time)
        Triple(
            monthLabelSdf.format(c.time).replaceFirstChar { it.uppercase() },
            incomeByMonthYm[monthYm] ?: 0.0,
            expenseByMonthYm[monthYm] ?: 0.0
        )
    }

    val semesterIncome = semesterMonths.sumOf { it.second }.roundCents()
    val semesterExpense = semesterMonths.sumOf { it.third }.roundCents()

    val activeMonths = semesterMonths.filter { it.third > 0 }
    val bestMonth = activeMonths.minByOrNull { it.third }?.first ?: "-"
    val worstMonth = activeMonths.maxByOrNull { it.third }?.first ?: "-"

    val topCategoryId = semesterExpensesByCategory.maxByOrNull { it.total }?.categoryId
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
        topCategory      = topCategory?.displayName ?: "Sem dados",
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
