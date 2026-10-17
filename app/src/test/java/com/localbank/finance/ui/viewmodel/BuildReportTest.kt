package com.localbank.finance.ui.viewmodel

import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class BuildReportTest {

    private val food = Category(id = "food", name = "Alimentação", icon = "restaurant", colorHex = "#FF5722", type = TransactionType.EXPENSE)
    private val home = Category(id = "home", name = "Moradia", icon = "home", colorHex = "#9C27B0", type = TransactionType.EXPENSE)
    private val salary = Category(id = "salary", name = "Salário", icon = "work", colorHex = "#4CAF50", type = TransactionType.INCOME)
    private val categories = listOf(food, home, salary)

    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, 12, 0, 0) }.timeInMillis

    private fun tx(amount: Double, type: TransactionType, categoryId: String?, date: Long) = Transaction(
        accountId = "acc", categoryId = categoryId, amount = amount,
        type = type, description = "", date = date
    )

    private val transactions = listOf(
        tx(5000.0, TransactionType.INCOME, "salary", at(2026, Calendar.AUGUST, 5)),
        tx(300.0, TransactionType.EXPENSE, "food", at(2026, Calendar.AUGUST, 10)),
        tx(1200.0, TransactionType.EXPENSE, "home", at(2026, Calendar.AUGUST, 1)),
        tx(100.0, TransactionType.EXPENSE, null, at(2026, Calendar.AUGUST, 31)),
        tx(4000.0, TransactionType.INCOME, "salary", at(2026, Calendar.JULY, 5)),
        tx(800.0, TransactionType.EXPENSE, "food", at(2026, Calendar.JULY, 20)),
        // fora do semestre selecionado
        tx(999.0, TransactionType.EXPENSE, "food", at(2026, Calendar.JUNE, 30))
    )

    // Dia 31 de propósito: é o caso que quebrava o cálculo do semestre
    private val selected = Calendar.getInstance().apply { clear(); set(2026, Calendar.AUGUST, 31, 10, 0, 0) }

    @Test
    fun `gastos por categoria do mes selecionado`() {
        val report = buildReport(transactions, categories, selected)

        assertEquals(1600.0, report.totalExpenseThisMonth, 0.001)
        assertEquals(listOf("Moradia", "Alimentação", "Sem categoria"), report.categoryExpenses.map { it.categoryName })
        assertEquals(75f, report.categoryExpenses.first().percentage, 0.01f)
    }

    @Test
    fun `comparativo termina no mes selecionado`() {
        val months = buildReport(transactions, categories, selected).monthlyComparison

        assertEquals(6, months.size)
        assertEquals(5000.0, months.last().income, 0.001)
        assertEquals(1600.0, months.last().expense, 0.001)
        assertEquals(800.0, months[4].expense, 0.001)    // julho
        assertEquals(999.0, months[3].expense, 0.001)    // junho
    }

    @Test
    fun `semestre considera apenas julho a dezembro`() {
        val semester = buildReport(transactions, categories, selected).semesterReport

        assertNotNull(semester)
        semester!!
        assertEquals("2º Semestre 2026", semester.label)
        assertEquals(9000.0, semester.totalIncome, 0.001)
        assertEquals(2400.0, semester.totalExpense, 0.001)
        assertEquals(6600.0, semester.balance, 0.001)
        assertEquals("Moradia", semester.topCategory)    // 1200 vs 1100 de alimentação no semestre
        assertEquals(73.33f, semester.savingsRate, 0.01f)
    }

    @Test
    fun `sem transacoes nao gera panorama`() {
        val report = buildReport(emptyList(), categories, selected)

        assertNull(report.semesterReport)
        assertEquals(0.0, report.totalExpenseThisMonth, 0.0)
        assertEquals(6, report.monthlyComparison.size)
    }
}
