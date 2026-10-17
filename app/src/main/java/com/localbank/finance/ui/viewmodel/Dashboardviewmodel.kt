package com.localbank.finance.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.model.displayName
import com.localbank.finance.data.repository.FinanceRepository
import com.localbank.finance.util.DateRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class CategoryExpenseSlice(
    val categoryName: String,
    val colorHex: String,
    val amount: Double
)

/** Estado de um dia na régua do mês. */
enum class BillMark { NONE, PAID, DUE }

data class DashboardUiState(
    // Ritmo do mês
    val accountsBalance: Double = 0.0,          // saldo somado de todas as contas
    val pendingThisMonth: Double = 0.0,         // agendadas não pagas até o fim do mês (inclui atrasadas)
    val dailyAllowance: Double = 0.0,           // (saldo − pendente) ÷ dias restantes
    val daysLeft: Int = 1,                      // inclui hoje
    val today: Int = 1,
    val billMarks: List<BillMark> = emptyList(),// um item por dia do mês

    // Mês
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val categoryExpenses: List<CategoryExpenseSlice> = emptyList(),

    // Listas
    val urgentExpenses: List<ScheduledExpense> = emptyList(),    // vence em até 3 dias (ou atrasada)
    val upcomingExpenses: List<ScheduledExpense> = emptyList(),  // demais dos próximos 30 dias
    val recentTransactions: List<Transaction> = emptyList(),
    val categoryNames: Map<String, String> = emptyMap()
) {
    val balance: Double get() = totalIncome - totalExpense
}

class DashboardViewModel(private val repository: FinanceRepository) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllTransactions(),
        repository.getAllScheduled(),
        repository.getAllCategories(),
        repository.getAllAccounts()
    ) { transactions, scheduled, categories, accounts ->
        buildDashboard(transactions, scheduled, categories, accounts, Calendar.getInstance())
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Cálculo puro do painel — separado do ViewModel para poder ser testado. */
internal fun buildDashboard(
    transactions: List<Transaction>,
    scheduled: List<ScheduledExpense>,
    categories: List<Category>,
    accounts: List<Account>,
    now: Calendar
): DashboardUiState {
    val nowMs = now.timeInMillis
    val month = DateRanges.month(now)
    val monthTransactions = transactions.filter { it.date in month }
    val categoriesById = categories.associateBy { it.id }

    val income  = monthTransactions.filter { it.type == TransactionType.INCOME  }.sumOf { it.amount }
    val expense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    // ── Ritmo do mês ──
    val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = now.get(Calendar.DAY_OF_MONTH)
    val daysLeft = daysInMonth - today + 1
    val pending = scheduled.filter { !it.isPaid }
    val pendingThisMonth = pending.filter { it.dueDate <= month.to }.sumOf { it.amount }
    val accountsBalance = accounts.sumOf { it.balance }

    val marks = MutableList(daysInMonth) { BillMark.NONE }
    val cursor = Calendar.getInstance()
    scheduled.filter { it.dueDate in month }.forEach { s ->
        cursor.timeInMillis = s.dueDate
        val i = cursor.get(Calendar.DAY_OF_MONTH) - 1
        // Pendente vence sobre paga quando há duas no mesmo dia
        if (!s.isPaid) marks[i] = BillMark.DUE
        else if (marks[i] == BillMark.NONE) marks[i] = BillMark.PAID
    }

    // ── Vencimentos ──
    val (urgent, upcoming) = pending
        .filter { it.dueDate <= nowMs + 30 * DAY_MS }
        .sortedBy { it.dueDate }
        .partition { (it.dueDate - nowMs) / DAY_MS <= 3 }

    // ── Gastos por categoria no mês ──
    val categoryExpenses = monthTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.categoryId }
        .map { (catId, txns) ->
            val cat = categoriesById[catId]
            CategoryExpenseSlice(
                categoryName = cat?.displayName ?: "Sem categoria",
                colorHex     = cat?.colorHex ?: "#9E9E9E",
                amount       = txns.sumOf { it.amount }
            )
        }
        .sortedByDescending { it.amount }

    return DashboardUiState(
        accountsBalance    = accountsBalance,
        pendingThisMonth   = pendingThisMonth,
        dailyAllowance     = (accountsBalance - pendingThisMonth) / daysLeft,
        daysLeft           = daysLeft,
        today              = today,
        billMarks          = marks,
        totalIncome        = income,
        totalExpense       = expense,
        categoryExpenses   = categoryExpenses,
        urgentExpenses     = urgent,
        upcomingExpenses   = upcoming,
        recentTransactions = transactions.take(5),
        categoryNames      = categories.associate { it.id to it.displayName }
    )
}
