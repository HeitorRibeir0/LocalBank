package com.localbank.finance.ui.viewmodel

import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.AccountType
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BuildDashboardTest {

    private fun at(month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(2026, month, day, hour, 0, 0) }.timeInMillis

    // 17 de outubro de 2026: faltam 15 dias contando hoje
    private val now = Calendar.getInstance().apply { timeInMillis = at(Calendar.OCTOBER, 17, 10) }

    private val accounts = listOf(
        Account(id = "a", name = "Carteira", type = AccountType.WALLET, balance = 2000.0),
        Account(id = "b", name = "Banco", type = AccountType.CHECKING, balance = 1500.0)
    )

    private fun bill(id: String, amount: Double, due: Long, paid: Boolean = false) = ScheduledExpense(
        id = id, accountId = "a", categoryId = null, amount = amount,
        description = id, dueDate = due, isPaid = paid
    )

    private val scheduled = listOf(
        bill("aluguel", 1450.0, at(Calendar.OCTOBER, 18, 0)),
        bill("internet", 119.90, at(Calendar.OCTOBER, 20, 0)),
        bill("atrasada", 50.0, at(Calendar.OCTOBER, 5, 0)),
        bill("luz", 200.0, at(Calendar.OCTOBER, 10, 0), paid = true),
        bill("novembro", 999.0, at(Calendar.NOVEMBER, 5, 0))   // fora do mês: não desconta
    )

    @Test
    fun `por dia desconta contas do mes e divide pelos dias restantes`() {
        val state = buildDashboard(emptyList(), scheduled, emptyList(), accounts, now)

        assertEquals(15, state.daysLeft)
        assertEquals(1619.90, state.pendingThisMonth, 0.001)          // 1450 + 119,90 + 50 atrasada
        assertEquals((3500.0 - 1619.90) / 15, state.dailyAllowance, 0.001)
    }

    @Test
    fun `regua marca dias com conta paga e pendente`() {
        val marks = buildDashboard(emptyList(), scheduled, emptyList(), accounts, now).billMarks

        assertEquals(31, marks.size)
        assertEquals(BillMark.DUE, marks[17])     // dia 18
        assertEquals(BillMark.DUE, marks[19])     // dia 20
        assertEquals(BillMark.PAID, marks[9])     // dia 10
        assertEquals(BillMark.NONE, marks[0])
    }

    @Test
    fun `vence logo separa urgentes`() {
        val state = buildDashboard(emptyList(), scheduled, emptyList(), accounts, now)

        assertEquals(listOf("atrasada", "aluguel", "internet"), state.urgentExpenses.map { it.id })
        assertEquals(listOf("novembro"), state.upcomingExpenses.map { it.id })
    }

    @Test
    fun `saldo menor que as contas deixa o valor por dia negativo`() {
        val poor = listOf(Account(id = "a", name = "Carteira", type = AccountType.WALLET, balance = 100.0))
        val state = buildDashboard(emptyList(), scheduled, emptyList(), poor, now)

        assertTrue(state.dailyAllowance < 0)
    }

    @Test
    fun `entradas e saidas consideram so o mes atual`() {
        val txs = listOf(
            Transaction(accountId = "a", categoryId = null, amount = 5000.0, type = TransactionType.INCOME,
                description = "", date = at(Calendar.OCTOBER, 5)),
            Transaction(accountId = "a", categoryId = null, amount = 300.0, type = TransactionType.EXPENSE,
                description = "", date = at(Calendar.OCTOBER, 10)),
            Transaction(accountId = "a", categoryId = null, amount = 700.0, type = TransactionType.EXPENSE,
                description = "", date = at(Calendar.SEPTEMBER, 28))
        )
        val state = buildDashboard(txs, emptyList(), emptyList(), accounts, now)

        assertEquals(5000.0, state.totalIncome, 0.001)
        assertEquals(300.0, state.totalExpense, 0.001)
        assertEquals("Sem categoria", state.categoryExpenses.single().categoryName)
    }
}
