package com.localbank.finance.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.localbank.finance.data.database.AppDatabase
import com.localbank.finance.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinanceRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository
    private val account = Account(id = "acc", name = "Conta", type = AccountType.CHECKING, balanceCents = 0)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FinanceRepository(
            accountDao = db.accountDao(),
            categoryDao = db.categoryDao(),
            transactionDao = db.transactionDao(),
            scheduledExpenseDao = db.scheduledExpenseDao(),
            budgetDao = db.budgetDao(),
            savingsGoalDao = db.savingsGoalDao(),
            pendingCaptureDao = db.pendingCaptureDao(),
            database = db
        )
        repo.insertAccount(account)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun balance() = db.accountDao().getById("acc")!!.balanceCents

    private fun tx(cents: Long, type: TransactionType) = Transaction(
        accountId = "acc", categoryId = null, amountCents = cents, type = type, description = "", date = 1000
    )

    @Test
    fun `saldo soma entradas e subtrai saidas sem erro de arredondamento`() = runTest {
        repo.insertTransaction(tx(10, TransactionType.INCOME))
        repo.insertTransaction(tx(20, TransactionType.INCOME))
        repo.insertTransaction(tx(5, TransactionType.EXPENSE))
        assertEquals(25L, balance())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejeita valor zero`() = runTest {
        repo.insertTransaction(tx(0, TransactionType.EXPENSE))
    }

    @Test
    fun `pagar agendada recorrente cria a proxima e debita a conta`() = runTest {
        val due = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 31) }.timeInMillis
        val bill = ScheduledExpense(
            id = "luz", accountId = "acc", categoryId = null, amountCents = 12000, description = "Luz",
            dueDate = due, isRecurring = true, recurrenceRule = RecurrenceRule.MONTHLY, notifiedStage = 2
        )
        repo.insertScheduled(bill)

        repo.payScheduledExpense(bill)

        val all = repo.getAllScheduled().first()
        assertEquals(2, all.size)
        assertTrue(all.single { it.id == "luz" }.isPaid)
        val next = all.single { it.id != "luz" }
        assertFalse(next.isPaid)
        assertEquals(0, next.notifiedStage)
        val nextCal = Calendar.getInstance().apply { timeInMillis = next.dueDate }
        assertEquals(Calendar.FEBRUARY, nextCal.get(Calendar.MONTH))
        assertEquals(-12000L, balance())
        assertEquals("luz", db.transactionDao().getAllOnce().single().scheduledId)
    }

    @Test
    fun `apagar pagamento desfaz o pago e devolve o saldo`() = runTest {
        val bill = ScheduledExpense(id = "agua", accountId = "acc", categoryId = null, amountCents = 5000, description = "Água", dueDate = 1000)
        repo.insertScheduled(bill)
        repo.payScheduledExpense(bill)

        repo.deleteTransaction(db.transactionDao().getAllOnce().single())

        assertFalse(db.scheduledExpenseDao().getById("agua")!!.isPaid)
        assertEquals(0L, balance())
    }

    @Test
    fun `mudar o vencimento zera o estagio de notificacao`() = runTest {
        val bill = ScheduledExpense(id = "net", accountId = "acc", categoryId = null, amountCents = 9900, description = "Net", dueDate = 1000, notifiedStage = 2)
        repo.insertScheduled(bill)

        repo.updateScheduled(bill.copy(description = "Internet"))
        assertEquals(2, db.scheduledExpenseDao().getById("net")!!.notifiedStage)

        repo.updateScheduled(bill.copy(dueDate = 5000))
        assertEquals(0, db.scheduledExpenseDao().getById("net")!!.notifiedStage)
    }

    @Test
    fun `arquivar conta mantem o historico`() = runTest {
        repo.insertTransaction(tx(1000, TransactionType.EXPENSE))
        repo.setAccountArchived(db.accountDao().getById("acc")!!, true)

        assertTrue(db.accountDao().getById("acc")!!.archived)
        assertEquals(1, db.transactionDao().getAllOnce().size)
        assertEquals(-1000L, balance())
    }

    @Test
    fun `excluir conta apaga lancamentos e agendadas dela`() = runTest {
        repo.insertTransaction(tx(1000, TransactionType.EXPENSE))
        repo.insertScheduled(ScheduledExpense(accountId = "acc", categoryId = null, amountCents = 500, description = "x", dueDate = 1))
        assertEquals(1 to 1, repo.getAccountCounts("acc"))

        repo.deleteAccount(db.accountDao().getById("acc")!!)

        assertTrue(db.transactionDao().getAllOnce().isEmpty())
        assertTrue(db.scheduledExpenseDao().getAllOnce().isEmpty())
    }

    @Test
    fun `deposito na meta nao passa do alvo`() = runTest {
        repo.insertSavingsGoal(SavingsGoal(id = "g", name = "Viagem", targetCents = 10000))
        repo.depositToGoal("g", 7000)
        repo.depositToGoal("g", 7000)
        assertEquals(10000L, db.savingsGoalDao().getById("g")!!.savedCents)
    }

    @Test
    fun `confirmar captura cria a saida e remove da fila`() = runTest {
        val capture = PendingCapture(amountCents = 3330, merchant = "Padaria", sourceLabel = "Nubank", sourcePackage = "x", detectedAt = 1000, fingerprint = "f")
        db.pendingCaptureDao().insert(capture)

        repo.confirmCapture(capture, "acc", null, "Padaria")

        assertEquals(-3330L, balance())
        assertTrue(db.pendingCaptureDao().observeAll().first().isEmpty())
    }
}
