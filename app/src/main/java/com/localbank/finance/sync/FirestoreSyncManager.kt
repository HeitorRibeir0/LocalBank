package com.localbank.finance.sync

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.localbank.finance.data.database.AppDatabase
import com.localbank.finance.data.model.Account
import com.localbank.finance.data.model.AccountType
import com.localbank.finance.data.model.Budget
import com.localbank.finance.data.model.Category
import com.localbank.finance.data.model.RecurrenceRule
import com.localbank.finance.data.model.SavingsGoal
import com.localbank.finance.data.model.ScheduledExpense
import com.localbank.finance.data.model.TransactionType
import com.localbank.finance.data.model.Transaction as AppTransaction
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sincronização bidirecional Room ↔ Firestore.
 *
 * Mudanças remotas entram numa fila única e cada snapshot é aplicado numa
 * transação do Room: a UI recebe uma emissão por lote, não uma por documento.
 */
class FirestoreSyncManager(private val database: AppDatabase) {

    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val transactionDao = database.transactionDao()
    private val scheduledExpenseDao = database.scheduledExpenseDao()
    private val budgetDao = database.budgetDao()
    private val savingsGoalDao = database.savingsGoalDao()

    private val db = FirebaseFirestore.getInstance()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Sync error: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val listeners = mutableListOf<ListenerRegistration>()

    /** Mudanças de um snapshot de uma coleção. */
    private data class RemoteBatch(
        val collection: String,
        val upserts: List<DocumentSnapshot>,
        val removals: List<DocumentSnapshot>,
        val attempt: Int = 0
    )

    private val inbox = Channel<RemoteBatch>(Channel.UNLIMITED)

    init {
        // Consumidor único: preserva a ordem e evita corrida entre lotes
        scope.launch {
            for (batch in inbox) {
                try {
                    applyBatch(batch)
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao aplicar ${batch.collection}: ${e.message}", e)
                }
            }
        }
    }

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

    fun pushSavingsGoal(householdId: String, goal: SavingsGoal) {
        householdRef(householdId).collection(GOALS)
            .document(goal.id).set(goalToMap(goal))
    }

    // merge só com os campos editáveis: não sobrescreve savedAmount
    fun pushSavingsGoalMeta(householdId: String, goal: SavingsGoal) {
        householdRef(householdId).collection(GOALS).document(goal.id).set(
            mapOf(
                "name" to goal.name, "targetAmount" to goal.targetAmount,
                "colorHex" to goal.colorHex, "deadline" to goal.deadline
            ),
            SetOptions.merge()
        )
    }

    // Incremento atômico no servidor: depósitos simultâneos nos dois aparelhos somam
    fun incrementGoalSaved(householdId: String, goalId: String, amount: Double) {
        householdRef(householdId).collection(GOALS).document(goalId).set(
            mapOf("savedAmount" to FieldValue.increment(amount)),
            SetOptions.merge()
        )
    }

    fun deleteRemote(householdId: String, collection: String, docId: String) {
        householdRef(householdId).collection(collection).document(docId).delete()
    }

    /**
     * Apaga documentos pelos IDs informados, em lotes de até 450 operações.
     * Não espera o servidor: offline, o SDK guarda as exclusões e envia quando a rede voltar.
     */
    fun deleteRemoteIds(householdId: String, collection: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val ref = householdRef(householdId).collection(collection)
        ids.chunked(MAX_BATCH_OPS).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(ref.document(it)) }
            batch.commit().addOnFailureListener { Log.w(TAG, "Falha ao apagar em $collection: ${it.message}") }
        }
    }

    // Apaga transações, agendamentos e orçamentos + zera saldos das contas.
    // Usa os IDs locais, porque ler o remoto falha quando está offline.
    // Precisa ser chamado ANTES de apagar o local.
    suspend fun deleteHistoryRemote(householdId: String) {
        deleteRemoteIds(householdId, TRANSACTIONS, transactionDao.getAllOnce().map { it.id })
        deleteRemoteIds(householdId, SCHEDULED, scheduledExpenseDao.getAllOnce().map { it.id })
        deleteRemoteIds(householdId, BUDGETS, budgetDao.getAllOnce().map { it.id })
        zeroRemoteBalances(householdId, accountDao.getAllOnce().map { it.id })
    }

    private fun zeroRemoteBalances(householdId: String, accountIds: List<String>) {
        val ref = householdRef(householdId).collection(ACCOUNTS)
        accountIds.chunked(MAX_BATCH_OPS).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(ref.document(it), "balance", 0.0) }
            batch.commit().addOnFailureListener { Log.w(TAG, "Falha ao zerar saldos: ${it.message}") }
        }
    }

    // Recalcula todos os saldos a partir das transações salvas localmente.
    suspend fun recalculateAllBalances() = accountDao.recalculateAllBalances()

    // Apaga tudo: histórico + contas + categorias + metas. Precisa ser chamado ANTES do local.
    suspend fun deleteAllRemote(householdId: String) {
        deleteRemoteIds(householdId, TRANSACTIONS, transactionDao.getAllOnce().map { it.id })
        deleteRemoteIds(householdId, SCHEDULED, scheduledExpenseDao.getAllOnce().map { it.id })
        deleteRemoteIds(householdId, BUDGETS, budgetDao.getAllOnce().map { it.id })
        deleteRemoteIds(householdId, ACCOUNTS, accountDao.getAllOnce().map { it.id })
        deleteRemoteIds(householdId, CATEGORIES, categoryDao.getAllOnce().map { it.id })
        deleteRemoteIds(householdId, GOALS, savingsGoalDao.getAllOnce().map { it.id })
    }

    // ─────────────────────────────────────────────
    // UPLOAD INICIAL — Room → Firestore
    // ─────────────────────────────────────────────

    /**
     * Envia todos os dados locais em lotes de até [MAX_BATCH_OPS] (o limite do Firestore é 500).
     * Não espera o servidor: offline, o SDK guarda os writes e envia quando a rede voltar.
     * [onComplete] recebe true quando todos os lotes forem confirmados.
     */
    suspend fun uploadAll(householdId: String, onComplete: (Boolean) -> Unit = {}) {
        val ref = householdRef(householdId)
        val writes = buildList<Pair<DocumentReference, Map<String, Any?>>> {
            accountDao.getAllOnce().forEach { add(ref.collection(ACCOUNTS).document(it.id) to accountToMap(it)) }
            categoryDao.getAllOnce().forEach { add(ref.collection(CATEGORIES).document(it.id) to categoryToMap(it)) }
            transactionDao.getAllOnce().forEach { add(ref.collection(TRANSACTIONS).document(it.id) to transactionToMap(it)) }
            scheduledExpenseDao.getAllOnce().forEach { add(ref.collection(SCHEDULED).document(it.id) to scheduledToMap(it)) }
            budgetDao.getAllOnce().forEach { add(ref.collection(BUDGETS).document(it.id) to budgetToMap(it)) }
            savingsGoalDao.getAllOnce().forEach { add(ref.collection(GOALS).document(it.id) to goalToMap(it)) }
        }
        val chunks = writes.chunked(MAX_BATCH_OPS)
        if (chunks.isEmpty()) { onComplete(true); return }

        val lock = Any()
        var pending = chunks.size
        var failed = false
        chunks.forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (doc, data) -> batch.set(doc, data) }
            batch.commit().addOnCompleteListener { task ->
                synchronized(lock) {
                    if (!task.isSuccessful) {
                        failed = true
                        Log.w(TAG, "Upload parcial falhou: ${task.exception?.message}")
                    }
                    if (--pending == 0) onComplete(!failed)
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // DOWNLOAD COM LIMPEZA — para membros novos
    // ─────────────────────────────────────────────

    /**
     * Substitui os dados locais pelos do Firestore.
     * Baixa as coleções em paralelo e só apaga o local depois que o download terminou,
     * tudo numa única transação (se falhar no meio, nada muda).
     */
    suspend fun clearAndDownload(householdId: String) {
        val ref = householdRef(householdId)
        val docs = coroutineScope {
            listOf(ACCOUNTS, CATEGORIES, TRANSACTIONS, SCHEDULED, BUDGETS, GOALS)
                .map { name -> async { ref.collection(name).get().await().documents } }
                .map { it.await() }
        }
        val (accountDocs, categoryDocs, txDocs, scheduledDocs, budgetDocs) = docs
        val goals = docs[5].mapNotNull(::mapToGoal)

        val accounts = accountDocs.mapNotNull(::mapToAccount)
        val categories = categoryDocs.mapNotNull(::mapToCategory)
        val accountIds = accounts.mapTo(HashSet()) { it.id }
        val categoryIds = categories.mapTo(HashSet()) { it.id }
        fun validCategory(id: String?) = id?.takeIf { it in categoryIds }

        val transactions = txDocs.mapNotNull(::mapToTransaction)
            .filter { it.accountId in accountIds }
            .map { it.copy(categoryId = validCategory(it.categoryId)) }
        val scheduled = scheduledDocs.mapNotNull(::mapToScheduled)
            .filter { it.accountId in accountIds }
            .map { it.copy(categoryId = validCategory(it.categoryId)) }
        val budgets = budgetDocs.mapNotNull(::mapToBudget)
            .filter { it.categoryId in categoryIds }

        database.withTransaction {
            // Filhos antes dos pais na limpeza; pais antes dos filhos na inserção
            transactionDao.deleteAll()
            scheduledExpenseDao.deleteAll()
            budgetDao.deleteAll()
            savingsGoalDao.deleteAll()
            categoryDao.deleteAll()
            accountDao.deleteAll()

            accountDao.insertAll(accounts)
            categoryDao.insertAll(categories)
            transactionDao.insertAll(transactions)
            scheduledExpenseDao.insertAll(scheduled)
            budgetDao.insertAll(budgets)
            savingsGoalDao.insertAll(goals)
            accountDao.recalculateAllBalances()
        }
    }

    // ─────────────────────────────────────────────
    // LISTENERS — receber mudanças em tempo real
    // ─────────────────────────────────────────────

    fun startListening(householdId: String) {
        stopListening()
        if (!scope.isActive) return  // já foi fechado (ex.: finally rodando após o dispose)
        val ref = householdRef(householdId)
        // Pais primeiro (sem FK), depois filhos
        listOf(ACCOUNTS, CATEGORIES, TRANSACTIONS, SCHEDULED, BUDGETS, GOALS)
            .forEach { listeners += listen(ref, it) }
    }

    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    /** Encerra listeners e a fila. Writes já enviados ao SDK do Firestore não são afetados. */
    fun close() {
        stopListening()
        scope.cancel()
    }

    private fun listen(ref: DocumentReference, collection: String): ListenerRegistration =
        ref.collection(collection).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listener $collection: ${error.message}")
                return@addSnapshotListener
            }
            snapshot ?: return@addSnapshotListener

            val upserts = mutableListOf<DocumentSnapshot>()
            val removals = mutableListOf<DocumentSnapshot>()
            snapshot.documentChanges.forEach { change ->
                when (change.type) {
                    DocumentChange.Type.ADDED,
                    DocumentChange.Type.MODIFIED -> {
                        // Eco de um write deste aparelho: já está no Room
                        if (!change.document.metadata.hasPendingWrites()) upserts += change.document
                    }
                    DocumentChange.Type.REMOVED -> removals += change.document
                }
            }
            if (upserts.isNotEmpty() || removals.isNotEmpty()) {
                inbox.trySend(RemoteBatch(collection, upserts, removals))
            }
        }

    /**
     * Aplica um lote numa transação. Documentos cujo pai (conta/categoria) ainda não
     * chegou são adiados e reenfileirados — uma violação de FK dentro da transação
     * desfaria o lote inteiro.
     */
    private suspend fun applyBatch(batch: RemoteBatch) {
        val isLastAttempt = batch.attempt >= MAX_RETRIES
        val deferred = mutableListOf<DocumentSnapshot>()
        var dropped = 0

        database.withTransaction {
            when (batch.collection) {
                ACCOUNTS -> {
                    batch.upserts.mapNotNull(::mapToAccount).forEach { a ->
                        accountDao.insertIfNotExists(a.id, a.name, a.type.name, 0.0, a.currency, a.createdAt)
                        accountDao.updateMetaOnly(a.id, a.name, a.type.name, a.currency)
                    }
                    batch.removals.mapNotNull(::mapToAccount).forEach { accountDao.delete(it) }
                    accountDao.recalculateAllBalances()
                }

                CATEGORIES -> {
                    val categories = batch.upserts.mapNotNull(::mapToCategory)
                    categoryDao.insertOrIgnore(categories)
                    categoryDao.updateAll(categories)
                    batch.removals.mapNotNull(::mapToCategory).forEach { categoryDao.delete(it) }
                }

                TRANSACTIONS -> {
                    val parents = loadParents()
                    val ready = mutableListOf<AppTransaction>()
                    batch.upserts.forEach { doc ->
                        val tx = mapToTransaction(doc) ?: return@forEach
                        when {
                            tx.accountId !in parents.accounts ->
                                if (isLastAttempt) dropped++ else deferred += doc
                            tx.categoryId != null && tx.categoryId !in parents.categories ->
                                if (isLastAttempt) ready += tx.copy(categoryId = null) else deferred += doc
                            else -> ready += tx
                        }
                    }
                    transactionDao.insertAll(ready)
                    batch.removals.mapNotNull(::mapToTransaction).forEach { transactionDao.delete(it) }
                    accountDao.recalculateAllBalances()
                }

                SCHEDULED -> {
                    val parents = loadParents()
                    val ready = mutableListOf<ScheduledExpense>()
                    batch.upserts.forEach { doc ->
                        val exp = mapToScheduled(doc) ?: return@forEach
                        when {
                            exp.accountId !in parents.accounts ->
                                if (isLastAttempt) dropped++ else deferred += doc
                            exp.categoryId != null && exp.categoryId !in parents.categories ->
                                if (isLastAttempt) ready += exp.copy(categoryId = null) else deferred += doc
                            else -> ready += exp
                        }
                    }
                    scheduledExpenseDao.insertAll(ready)
                    batch.removals.mapNotNull(::mapToScheduled).forEach { scheduledExpenseDao.delete(it) }
                }

                GOALS -> {
                    // Sem FK: aplica direto
                    savingsGoalDao.insertAll(batch.upserts.mapNotNull(::mapToGoal))
                    batch.removals.mapNotNull(::mapToGoal).forEach { savingsGoalDao.delete(it) }
                }

                BUDGETS -> {
                    val parents = loadParents()
                    val ready = mutableListOf<Budget>()
                    batch.upserts.forEach { doc ->
                        val budget = mapToBudget(doc) ?: return@forEach
                        when {
                            budget.categoryId in parents.categories -> ready += budget
                            isLastAttempt -> dropped++
                            else -> deferred += doc
                        }
                    }
                    budgetDao.insertAll(ready)
                    batch.removals.mapNotNull(::mapToBudget).forEach { budgetDao.delete(it) }
                }
            }
        }

        if (dropped > 0) {
            Log.w(TAG, "${batch.collection}: $dropped documento(s) sem pai descartados após $MAX_RETRIES tentativas")
        }
        if (deferred.isNotEmpty()) {
            scope.launch {
                delay(RETRY_DELAY_MS * (batch.attempt + 1))
                inbox.send(RemoteBatch(batch.collection, deferred, emptyList(), batch.attempt + 1))
            }
        }
    }

    private data class Parents(val accounts: Set<String>, val categories: Set<String>)

    private suspend fun loadParents() = Parents(
        accounts = accountDao.getAllIds().toHashSet(),
        categories = categoryDao.getAllIds().toHashSet()
    )

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

    private fun goalToMap(g: SavingsGoal): HashMap<String, Any?> = hashMapOf(
        "id" to g.id, "name" to g.name, "targetAmount" to g.targetAmount,
        "savedAmount" to g.savedAmount, "colorHex" to g.colorHex, "deadline" to g.deadline,
        "createdAt" to g.createdAt, "createdBy" to g.createdBy
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

    private fun mapToGoal(doc: DocumentSnapshot): SavingsGoal? {
        return try {
            val target = doc.getDouble("targetAmount") ?: return null
            SavingsGoal(
                id = doc.getString("id") ?: doc.id,
                name = doc.getString("name") ?: return null,
                targetAmount = target,
                // Incrementos simultâneos podem passar do alvo no servidor
                savedAmount = (doc.getDouble("savedAmount") ?: 0.0).coerceIn(0.0, target),
                colorHex = doc.getString("colorHex") ?: "#4CAF50",
                deadline = doc.getLong("deadline"),
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
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

    companion object {
        private const val TAG = "FirestoreSyncManager"
        private const val ACCOUNTS = "accounts"
        private const val CATEGORIES = "categories"
        private const val TRANSACTIONS = "transactions"
        private const val SCHEDULED = "scheduled_expenses"
        private const val BUDGETS = "budgets"
        private const val GOALS = "savings_goals"
        private const val MAX_BATCH_OPS = 450
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
    }
}
