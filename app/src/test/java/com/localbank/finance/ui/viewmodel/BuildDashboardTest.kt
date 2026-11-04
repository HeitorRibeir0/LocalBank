package com.localbank.finance.ui.viewmodel

import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.AccountType
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.Transaction
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.util.toCents
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
        Account(id = "a", name = "Carteira", type = AccountType.WALLET, balanceCents = (2000.0).toCents()),
        Account(id = "b", name = "Banco", type = AccountType.CHECKING, balanceCents = (1500.0).toCents())
    )

    private fun bill(id: String, amount: Double, due: Long, paid: Boolean = false) = ScheduledExpense(
        id = id, accountId = "a", categoryId = null, amountCents = amount.toCents(),
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
    fun `saldo mensal zera no novo mes e sobra desconta contas do mes`() {
        val state = buildDashboard(emptyList(), scheduled, emptyList(), accounts, emptyList(), now)

        assertEquals(15, state.daysLeft)
        assertEquals(0L, state.accountsBalanceCents)  // sem transações neste mês: saldo mensal é 0
        assertEquals((1619.90).toCents(), state.pendingThisMonthCents)          // 1450 + 119,90 + 50 atrasada
        assertEquals((0L - 1619.90).toCents(), state.freeAfterBillsCents)  // 0 - contas = negativo
    }

    @Test
    fun `regua marca dias com conta paga e pendente`() {
        val marks = buildDashboard(emptyList(), scheduled, emptyList(), accounts, emptyList(), now).billMarks

        assertEquals(31, marks.size)
        assertEquals(BillMark.DUE, marks[17])     // dia 18
        assertEquals(BillMark.DUE, marks[19])     // dia 20
        assertEquals(BillMark.PAID, marks[9])     // dia 10
        assertEquals(BillMark.NONE, marks[0])
    }

    @Test
    fun `vence logo separa urgentes`() {
        val state = buildDashboard(emptyList(), scheduled, emptyList(), accounts, emptyList(), now)

        assertEquals(listOf("atrasada", "aluguel", "internet"), state.urgentExpenses.map { it.id })
        assertEquals(listOf("novembro"), state.upcomingExpenses.map { it.id })
    }

    @Test
    fun `sem transacoes no mes deixa saldo mensal zero mesmo com saldo nas contas`() {
        val poor = listOf(Account(id = "a", name = "Carteira", type = AccountType.WALLET, balanceCents = (100.0).toCents()))
        val state = buildDashboard(emptyList(), scheduled, emptyList(), poor, emptyList(), now)

        assertEquals(0L, state.accountsBalanceCents)  // saldo mensal, não conta o acumulado
        assertTrue(state.freeAfterBillsCents < 0)  // mas tem contas a pagar
    }

    @Test
    fun `entradas e saidas consideram so o mes atual`() {
        val txs = listOf(
            Transaction(accountId = "a", categoryId = null, amountCents = (5000.0).toCents(), type = TransactionType.INCOME,
                description = "", date = at(Calendar.OCTOBER, 5)),
            Transaction(accountId = "a", categoryId = null, amountCents = (300.0).toCents(), type = TransactionType.EXPENSE,
                description = "", date = at(Calendar.OCTOBER, 10)),
            Transaction(accountId = "a", categoryId = null, amountCents = (700.0).toCents(), type = TransactionType.EXPENSE,
                description = "", date = at(Calendar.SEPTEMBER, 28))
        )
        val state = buildDashboard(txs, emptyList(), emptyList(), accounts, emptyList(), now)

        assertEquals((5000.0).toCents(), state.totalIncomeCents)
        assertEquals((300.0).toCents(), state.totalExpenseCents)
        assertEquals((4700.0).toCents(), state.accountsBalanceCents)  // 5000 - 300 de outubro
        assertEquals("Sem categoria", state.categoryExpenses.single().categoryName)
    }
}
