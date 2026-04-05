package com.localbank.finance.sync

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.localbank.finance.data.database.AccountDao
import com.localbank.finance.data.database.BudgetDao
import com.localbank.finance.data.database.CategoryDao
import com.localbank.finance.data.database.ScheduledExpenseDao
import com.localbank.finance.data.database.TransactionDao
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.AccountType
import com.localbank.finance.data.model.Budget
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.RecurrenceRule
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.model.Transaction as AppTransaction
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sincronização bidirecional Room ↔ Firestore.
 */
class FirestoreSyncManager(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val scheduledExpenseDao: ScheduledExpenseDao,
    private val budgetDao: BudgetDao
) {
    private val db = FirebaseFirestore.getInstance()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("FirestoreSyncManager", "Sync error: ${throwable.message}")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val listeners = mutableListOf<ListenerRegistration>()

    private fun householdRef(householdId: String) =
        db.collection("households").document(householdId)

    // ─────────────────────────────────────────────
    // PUSH — enviar dados locais para o Firestore
    // ─────────────────────────────────────────────

    fun pushAccount(householdId: String, account: Account) {
        householdRef(householdId).collection("accounts")
            .document(account.id).set(accountToMap(account))
    }

    fun pushCategory(householdId: String, category: Category) {
        householdRef(householdId).collection("categories")
            .document(category.id).set(categoryToMap(category))
    }

    fun pushTransaction(householdId: String, transaction: AppTransaction) {
        householdRef(householdId).collection("transactions")
            .document(transaction.id).set(transactionToMap(transaction))
    }

    fun pushScheduledExpense(householdId: String, expense: ScheduledExpense) {
        householdRef(householdId).collection("scheduled_expenses")
            .document(expense.id).set(scheduledToMap(expense))
    }

    fun pushBudget(householdId: String, budget: Budget) {
        householdRef(householdId).collection("budgets")
            .document(budget.id).set(budgetToMap(budget))
    }

    fun deleteRemote(householdId: String, collection: String, docId: String) {
        householdRef(householdId).collection(collection).document(docId).delete()
    }

    // Deleta em lotes respeitando o limite de 500 ops do Firestore
    private suspend fun deleteCollection(ref: CollectionReference) {
        val docs = ref.get().await().documents
        if (docs.isEmpty()) return
        docs.chunked(499).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    // Apaga transações, agendamentos e orçamentos + zera saldos das contas
    suspend fun deleteHistoryRemote(householdId: String) {
        val ref = householdRef(householdId)
        deleteCollection(ref.collection("transactions"))
        deleteCollection(ref.collection("scheduled_expenses"))
        deleteCollection(ref.collection("budgets"))
        val accounts = ref.collection("accounts").get().await().documents
        if (accounts.isNotEmpty()) {
            val batch = db.batch()
            accounts.forEach { batch.update(it.reference, "balance", 0.0) }
            batch.commit().await()
        }
    }

    // Recalcula todos os saldos a partir das transações salvas localmente.
    // Chame isso ao iniciar o app para corrigir divergências de saldo entre devices.
    suspend fun recalculateAllBalances() {
        val accounts = accountDao.getAllOnce()
        accounts.forEach { account ->
            val net = transactionDao.getNetForAccount(account.id)
            accountDao.update(account.copy(balance = net))
        }
    }

    // Apaga tudo: histórico + contas + categorias
    suspend fun deleteAllRemote(householdId: String) {
        val ref = householdRef(householdId)
        deleteCollection(ref.collection("transactions"))
        deleteCollection(ref.collection("scheduled_expenses"))
        deleteCollection(ref.collection("budgets"))
        deleteCollection(ref.collection("accounts"))
        deleteCollection(ref.collection("categories"))
    }

    // ─────────────────────────────────────────────
    // UPLOAD INICIAL — Room → Firestore (batch)
    // ─────────────────────────────────────────────

    suspend fun uploadAll(
        householdId: String,
        accounts: List<Account>,
        categories: List<Category>,
        transactions: List<AppTransaction>,
        scheduledExpenses: List<ScheduledExpense>,
        budgets: List<Budget>
    ) {
        val batch = db.batch()
        val ref = householdRef(householdId)

        accounts.forEach { batch.set(ref.collection("accounts").document(it.id), accountToMap(it)) }
        categories.forEach { batch.set(ref.collection("categories").document(it.id), categoryToMap(it)) }
        transactions.forEach { batch.set(ref.collection("transactions").document(it.id), transactionToMap(it)) }
        scheduledExpenses.forEach { batch.set(ref.collection("scheduled_expenses").document(it.id), scheduledToMap(it)) }
        budgets.forEach { batch.set(ref.collection("budgets").document(it.id), budgetToMap(it)) }

        batch.commit().await()
    }

    // ─────────────────────────────────────────────
    // DOWNLOAD COM LIMPEZA — para membros novos
    // ─────────────────────────────────────────────

    /**
     * Limpa TODOS os dados locais e baixa tudo do Firestore.
     * Respeita a ordem de FK: primeiro deleta filhos, depois pais.
     * Depois insere pais primeiro, filhos depois.
     */
    suspend fun clearAndDownload(householdId: String) {
        // 1) Limpar na ordem correta (filhos antes dos pais)
        transactionDao.deleteAll()
        scheduledExpenseDao.deleteAll()
        budgetDao.deleteAll()
        categoryDao.deleteAll()
        accountDao.deleteAll()

        // 2) Baixar na ordem correta (pais antes dos filhos)
        val ref = householdRef(householdId)

        ref.collection("accounts").get().await().documents.forEach { doc ->
            mapToAccount(doc)?.let { accountDao.insert(it) }
        }
        ref.collection("categories").get().await().documents.forEach { doc ->
            mapToCategory(doc)?.let { categoryDao.insert(it) }
        }
        ref.collection("transactions").get().await().documents.forEach { doc ->
            mapToTransaction(doc)?.let {
                try { transactionDao.insert(it) } catch (_: Exception) { }
            }
        }
        ref.collection("scheduled_expenses").get().await().documents.forEach { doc ->
            mapToScheduled(doc)?.let {
                try { scheduledExpenseDao.insert(it) } catch (_: Exception) { }
            }
        }
        ref.collection("budgets").get().await().documents.forEach { doc ->
            mapToBudget(doc)?.let {
                try { budgetDao.insert(it) } catch (_: Exception) { }
            }
        }
    }

    /**
     * Download incremental (sem limpar). Útil para refresh.
     */
    suspend fun downloadAll(householdId: String) {
        val ref = householdRef(householdId)

        ref.collection("accounts").get().await().documents.forEach { doc ->
            mapToAccount(doc)?.let { accountDao.insert(it) }
        }
        ref.collection("categories").get().await().documents.forEach { doc ->
            mapToCategory(doc)?.let { categoryDao.insert(it) }
        }
        ref.collection("transactions").get().await().documents.forEach { doc ->
            mapToTransaction(doc)?.let {
                try { transactionDao.insert(it) } catch (_: Exception) { }
            }
        }
        ref.collection("scheduled_expenses").get().await().documents.forEach { doc ->
            mapToScheduled(doc)?.let {
                try { scheduledExpenseDao.insert(it) } catch (_: Exception) { }
            }
        }
        ref.collection("budgets").get().await().documents.forEach { doc ->
            mapToBudget(doc)?.let {
                try { budgetDao.insert(it) } catch (_: Exception) { }
            }
        }
    }

    // ─────────────────────────────────────────────
    // LISTENERS — receber mudanças em tempo real
    // ─────────────────────────────────────────────

    fun startListening(householdId: String) {
        stopListening()
        val ref = householdRef(householdId)

        // Accounts e Categories primeiro (não têm foreign keys)
        listeners += ref.collection("accounts")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    scope.launch {
                        try {
                            val account = mapToAccount(change.document) ?: return@launch
                            when (change.type) {
                                DocumentChange.Type.ADDED,
                                DocumentChange.Type.MODIFIED -> {
                                    // Preserva o saldo local — não sobrescreve com valor desatualizado do Firestore
                                    accountDao.insertIfNotExists(
                                        account.id, account.name, account.type.name,
                                        account.balance, account.currency, account.createdAt
                                    )
                                    accountDao.updateMetaOnly(
                                        account.id, account.name, account.type.name, account.currency
                                    )
                                }
                                DocumentChange.Type.REMOVED -> accountDao.delete(account)
                            }
                        } catch (_: Exception) { /* offline */ }
                    }
                }
            }

        listeners += ref.collection("categories")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    scope.launch {
                        try {
                            when (change.type) {
                                DocumentChange.Type.ADDED,
                                DocumentChange.Type.MODIFIED -> {
                                    mapToCategory(change.document)?.let { categoryDao.insert(it) }
                                }
                                DocumentChange.Type.REMOVED -> {
                                    mapToCategory(change.document)?.let { categoryDao.delete(it) }
                                }
                            }
                        } catch (_: Exception) { /* offline */ }
                    }
                }
            }

        // Transactions: verifica se é nova para evitar double-counting no saldo
        listeners += ref.collection("transactions")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    scope.launch {
                        val tx = mapToTransaction(change.document) ?: return@launch
                        when (change.type) {
                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {
                                val isNew = transactionDao.getById(tx.id) == null
                                insertWithRetry { transactionDao.insert(tx) }
                                if (isNew) {
                                    // Transação veio de outro device — atualiza saldo local
                                    val delta = if (tx.type == com.localbank.finance.data.model.TransactionType.INCOME)
                                        tx.amount else -tx.amount
                                    try { accountDao.updateBalance(tx.accountId, delta) } catch (_: Exception) { }
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                val existing = transactionDao.getById(tx.id)
                                if (existing != null) {
                                    try {
                                        transactionDao.delete(tx)
                                        // Reverte o efeito no saldo
                                        val delta = if (tx.type == com.localbank.finance.data.model.TransactionType.INCOME)
                                            -tx.amount else tx.amount
                                        accountDao.updateBalance(tx.accountId, delta)
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                    }
                }
            }

        // Scheduled expenses (depende de accounts e categories)
        listeners += ref.collection("scheduled_expenses")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {
                                mapToScheduled(change.document)?.let { exp ->
                                    insertWithRetry { scheduledExpenseDao.insert(exp) }
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                mapToScheduled(change.document)?.let {
                                    try { scheduledExpenseDao.delete(it) } catch (_: Exception) { }
                                }
                            }
                        }
                    }
                }
            }

        // Budgets (depende de categories)
        listeners += ref.collection("budgets")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documentChanges?.forEach { change ->
                    scope.launch {
                        when (change.type) {
                            DocumentChange.Type.ADDED,
                            DocumentChange.Type.MODIFIED -> {
                                mapToBudget(change.document)?.let { b ->
                                    insertWithRetry { budgetDao.insert(b) }
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                mapToBudget(change.document)?.let {
                                    try { budgetDao.delete(it) } catch (_: Exception) { }
                                }
                            }
                        }
                    }
                }
            }
    }

    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    /**
     * Tenta inserir com retry — lida com FK violations quando
     * entidades-pai (account/category) ainda não chegaram.
     */
    private suspend fun insertWithRetry(maxRetries: Int = 3, action: suspend () -> Unit) {
        var attempt = 0
        while (attempt < maxRetries) {
            try {
                action()
                return
            } catch (e: Exception) {
                attempt++
                if (attempt < maxRetries) {
                    delay(2000L * attempt) // 2s, 4s, 6s
                } else {
                    android.util.Log.w("FirestoreSyncManager", "Insert failed after $maxRetries retries: ${e.message}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Entity → Map
    // ─────────────────────────────────────────────

    private fun accountToMap(a: Account): HashMap<String, Any> = hashMapOf(
        "id" to a.id, "name" to a.name, "type" to a.type.name,
        "balance" to a.balance, "currency" to a.currency, "createdAt" to a.createdAt
    )

    private fun categoryToMap(c: Category): HashMap<String, String> = hashMapOf(
        "id" to c.id, "name" to c.name, "icon" to c.icon,
        "colorHex" to c.colorHex, "type" to c.type.name
    )

    private fun transactionToMap(t: AppTransaction): HashMap<String, Any?> = hashMapOf(
        "id" to t.id, "accountId" to t.accountId, "categoryId" to t.categoryId,
        "amount" to t.amount, "type" to t.type.name, "description" to t.description,
        "date" to t.date, "scheduledId" to t.scheduledId, "createdBy" to t.createdBy
    )

    private fun scheduledToMap(s: ScheduledExpense): HashMap<String, Any?> = hashMapOf(
        "id" to s.id, "accountId" to s.accountId, "categoryId" to s.categoryId,
        "amount" to s.amount, "description" to s.description, "dueDate" to s.dueDate,
        "isRecurring" to s.isRecurring, "recurrenceRule" to s.recurrenceRule?.name,
        "isPaid" to s.isPaid, "notified" to s.notified, "createdBy" to s.createdBy
    )

    private fun budgetToMap(b: Budget): HashMap<String, Any?> = hashMapOf(
        "id" to b.id, "categoryId" to b.categoryId, "monthYear" to b.monthYear,
        "limitAmount" to b.limitAmount, "createdBy" to b.createdBy, "createdAt" to b.createdAt
    )

    // ─────────────────────────────────────────────
    // Map → Entity
    // ─────────────────────────────────────────────

    private fun mapToAccount(doc: DocumentSnapshot): Account? {
        return try {
            Account(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name") ?: return null,
                type = AccountType.valueOf(doc.getString("type") ?: return null),
                balance = doc.getDouble("balance") ?: 0.0,
                currency = doc.getString("currency") ?: "BRL",
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (_: Exception) { null }
    }

    private fun mapToCategory(doc: DocumentSnapshot): Category? {
        return try {
            Category(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name") ?: return null,
                icon = doc.getString("icon") ?: "category",
                colorHex = doc.getString("colorHex") ?: "#9E9E9E",
                type = TransactionType.valueOf(doc.getString("type") ?: return null)
            )
        } catch (_: Exception) { null }
    }

    private fun mapToTransaction(doc: DocumentSnapshot): AppTransaction? {
        return try {
            AppTransaction(
                id = doc.getString("id") ?: doc.id,
                accountId = doc.getString("accountId") ?: return null,
                categoryId = doc.getString("categoryId"),
                amount = doc.getDouble("amount") ?: return null,
                type = TransactionType.valueOf(doc.getString("type") ?: return null),
                description = doc.getString("description") ?: "",
                date = doc.getLong("date") ?: return null,
                scheduledId = doc.getString("scheduledId"),
                createdBy = doc.getString("createdBy")
            )
        } catch (_: Exception) { null }
    }

    private fun mapToScheduled(doc: DocumentSnapshot): ScheduledExpense? {
        return try {
            ScheduledExpense(
                id = doc.getString("id") ?: doc.id,
                accountId = doc.getString("accountId") ?: return null,
                categoryId = doc.getString("categoryId"),
                amount = doc.getDouble("amount") ?: return null,
                description = doc.getString("description") ?: "",
                dueDate = doc.getLong("dueDate") ?: return null,
                isRecurring = doc.getBoolean("isRecurring") ?: false,
                recurrenceRule = doc.getString("recurrenceRule")?.let {
                    try { RecurrenceRule.valueOf(it) } catch (_: Exception) { null }
                },
                isPaid = doc.getBoolean("isPaid") ?: false,
                notified = doc.getBoolean("notified") ?: false,
                createdBy = doc.getString("createdBy")
            )
        } catch (_: Exception) { null }
    }

    private fun mapToBudget(doc: DocumentSnapshot): Budget? {
        return try {
            Budget(
                id = doc.getString("id") ?: doc.id,
                categoryId = doc.getString("categoryId") ?: return null,
                monthYear = doc.getString("monthYear") ?: return null,
                limitAmount = doc.getDouble("limitAmount") ?: return null,
                createdBy = doc.getString("createdBy"),
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (_: Exception) { null }
    }
}
