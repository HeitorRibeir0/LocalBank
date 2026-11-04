# LocalBank

App Android (Kotlin + Jetpack Compose) de finanças do lar. Room local + sincronização com Firestore
entre os aparelhos do mesmo lar (household). Testes unitários: `./gradlew testDebugUnitTest`.

# Melhorias pendentes

Lista de revisão de código feita em 2026-11-04. Cada item diz onde está, por que é problema e o passo a passo
para corrigir. Ao resolver um item, apague-o daqui (ou marque como feito) no mesmo commit.

Prioridade: **[Alta]** bug que o usuário vê ou dado errado · **[Média]** desempenho ou risco de divergência ·
**[Baixa]** organização/legibilidade.

## Parte 1 — camada de dados, sincronização e tela inicial

### 1.1 [Alta] Conta atrasada aparece como "Vence hoje"
**Onde:** `ui/screens/ExpensesScreen.kt:303` e `worker/NotificationWorker.kt:44`.

**Problema:** `((dueDate - agora) / (1000*60*60*24)).toInt()` faz divisão inteira, que corta a fração em direção
ao zero. Uma conta que venceu ontem às 23h dá `-0,4 → 0` e aparece como "Vence hoje"; uma que venceu há 2 dias
às 0h, com o relógio às 10h, dá `-1`. A tela inicial (`DashboardScreen.kt`) já calcula certo: compara o início
do dia e usa `Math.floorDiv`.

**Passo a passo:**
1. Em `util/DateRanges.kt`, crie uma função única:
   ```kotlin
   fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
       timeInMillis = ms
       set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
       set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
   }.timeInMillis

   /** Dias de calendário entre hoje e [ms]: 0 = hoje, 1 = amanhã, -1 = ontem. */
   fun daysUntil(ms: Long, now: Long = System.currentTimeMillis()): Int =
       Math.floorDiv(startOfDay(ms) - startOfDay(now), DAY_MS).toInt()
   ```
   Use `Math.round` sobre a diferença em vez de `floorDiv` se quiser tolerar o dia de 23h/25h de horário
   de verão (o Brasil não tem hoje, mas outros fusos têm).
2. Troque o cálculo em `ExpensesScreen.kt:303` por `DateRanges.daysUntil(expense.dueDate)`.
3. Troque em `NotificationWorker.kt:44` por `DateRanges.daysUntil(expense.dueDate, now)`.
4. Em `DashboardScreen.kt`, apague `startOfDay`, `DAY_MS` locais e use as funções de `DateRanges` (vale também
   para `relativeDay`).
5. Em `DateRangesTest.kt`, teste: vencida ontem às 23h → `-1`; hoje às 0h com relógio às 22h → `0`;
   amanhã às 0h com relógio às 23h59 → `1`.

### 1.2 [Alta] Pagamento pode divergir entre o celular e o Firestore
**Onde:** `data/repository/FinanceRepository.kt`, `payScheduledExpense` / `payScheduledExpenseInternal`.

**Problema:** os `syncPush` (envio ao Firestore) rodam **dentro** de `db.withTransaction { }`. O SDK do Firestore
envia na hora; se algo falhar depois (ex.: `insertScheduled` da próxima recorrência), o Room desfaz tudo, mas o
Firestore já recebeu "paga" e o lançamento. O outro aparelho mostra a conta paga; este, não.

**Passo a passo:**
1. Separe a parte local da parte remota. Faça `payScheduledExpenseInternal` só gravar no Room e **devolver** o
   que precisa ser enviado:
   ```kotlin
   private data class PayResult(val tx: Transaction, val paid: ScheduledExpense,
                                val next: ScheduledExpense?, val account: Account?)
   ```
   Use `transactionDao.insert` + `accountDao.recalculateBalance` + `scheduledExpenseDao.insert` diretamente,
   em vez de `insertTransaction`/`insertScheduled` (que fazem push).
2. Em `payScheduledExpense`, rode a transação, guarde o `PayResult` e **só depois** chame os `syncPush`
   (transação, agendada paga, próxima ocorrência, saldo da conta).
3. Se não houver `database` (testes), chame a parte interna sem transação e faça o push do mesmo jeito.
4. Teste com um DAO falso que lança exceção em `insert` da próxima ocorrência: nada pode ter sido enviado
   ao `syncManager` falso.

### 1.3 [Alta] Valores em `Double` e `roundCents()` nunca usada
**Onde:** `util/Money.kt`; todos os modelos (`amount`, `balance`, `limitAmount`, `targetAmount`, `savedAmount`);
as somas em `Daos.kt` (`recalculateBalance`, `getTotalIncome`...).

**Problema:** o comentário de `Money.kt` diz que `roundCents()` é aplicada "em todo valor que entra no app", mas
ela não é chamada em lugar nenhum. Somar `Double` acumula erro (`0.1 + 0.2 = 0.30000000000000004`); com
centenas de lançamentos o saldo pode sair `1234.5699999` e comparações como `saldo == 0` ou `pct >= 1.0`
falham por um fio. O `PurchaseParser` já trabalha em centavos (`Long`), então o modelo está inconsistente.

**Passo a passo (solução definitiva — centavos em `Long`):**
1. Troque os campos de dinheiro para `Long` em centavos: `amountCents`, `balanceCents`, etc.
2. Crie a migração do Room (versão atual + 1) em `AppDatabase.kt`: para cada tabela, crie a coluna nova,
   `UPDATE t SET amountCents = CAST(ROUND(amount * 100) AS INTEGER)`, e recrie a tabela sem a coluna antiga
   (SQLite antigo não tem `DROP COLUMN`; use o padrão "criar tabela nova → copiar → renomear").
3. Ajuste as consultas de soma em `Daos.kt` para somar `amountCents`.
4. No Firestore, grave os dois campos por uma versão (`amount` e `amountCents`) e leia `amountCents` com
   `amount` como fallback, para não quebrar o aparelho que ainda não atualizou. Depois de todos atualizarem,
   pare de gravar `amount`.
5. Na UI, converta só na borda: `CurrencyField` → centavos; formatação → `cents / 100.0`.
6. Adicione um teste de migração com `MigrationTestHelper` (os schemas já estão em `app/schemas/`).

**Paliativo rápido (se não quiser migrar agora):** chame `.roundCents()` em `ExpenseViewModel.addTransaction`,
`addScheduledExpense`, nos `mapTo*` do `FirestoreSyncManager` e no resultado de `buildDashboard`/`buildReport`.
Corrija o comentário de `Money.kt` para dizer a verdade.

### 1.4 [Média] Painel e lançamentos carregam o histórico inteiro na memória
**Onde:** `ui/viewmodel/Dashboardviewmodel.kt` (`repository.getAllTransactions()`) e
`ui/viewmodel/Expenseviewmodel.kt` (`transactions`).

**Problema:** toda mudança em qualquer transação (inclusive as que chegam do outro aparelho) emite a lista
completa, e `buildDashboard` filtra o mês em Kotlin. Com alguns anos de uso são milhares de linhas lidas,
convertidas e percorridas várias vezes (`filter` ×4, `groupBy`) a cada lançamento.

**Passo a passo:**
1. Em `TransactionDao`, adicione:
   ```kotlin
   @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT :limit")
   fun getRecent(limit: Int): Flow<List<TransactionEntity>>
   ```
2. No `DashboardViewModel`, troque `getAllTransactions()` por dois flows: `getTransactionsByPeriod(mes.from,
   mes.to)` (já existe) e `getRecent(5)`. Passe os dois para `buildDashboard`.
3. Como o mês muda com o tempo, gere o período num flow que reage à virada de mês (ou recalcule ao
   `ON_RESUME`) — senão quem deixa o app aberto de 31 para 1º vê o mês errado.
4. Faça as somas de entrada/saída numa só passada (`for` com dois acumuladores) em vez de dois `filter`+`sumOf`.
5. Os índices em `transactions(date)`, `(accountId)` e `(categoryId)` já existem (`Transaction.kt`), então as
   consultas por período usam índice — não precisa de migração para isso.
6. A `ExpensesScreen` recebe todas as transações e filtra o mês na própria tela (`ExpensesScreen.kt:63`). Mova o
   mês selecionado para o `ExpenseViewModel` e use `flatMapLatest { getTransactionsByPeriod(...) }` (ver 2.12).

### 1.5 [Baixa] `addTransaction` aceita valor zero ou negativo
**Onde:** `ui/viewmodel/Expenseviewmodel.kt:55` (e `addScheduledExpense`).

**Problema:** a validação está só na tela. Qualquer outra chamada (captura de notificação, teste, tela nova)
pode gravar `amount <= 0`, e como o saldo é `SUM(CASE WHEN INCOME THEN amount ELSE -amount)`, um negativo
inverte o sentido do lançamento.

**Passo a passo:**
1. No início de `addTransaction` e `addScheduledExpense`: `require(amount > 0) { "Valor deve ser positivo" }`,
   ou retorne cedo e emita uma mensagem de erro num `SharedFlow`.
2. Faça o mesmo em `FinanceRepository.insertTransaction`, que é o ponto por onde tudo passa.

### 1.6 [Baixa] `syncPush { it.x(householdId!!, ...) }` repetido ~20 vezes
**Onde:** `data/repository/FinanceRepository.kt`.

**Problema:** o `!!` em cada chamada é redundante (o `syncPush` já testa `householdId != null`) e polui o código.

**Passo a passo:**
1. Mude a assinatura para passar o id já verificado:
   ```kotlin
   private inline fun syncPush(action: (FirestoreSyncManager, String) -> Unit) {
       val sync = syncManager ?: return
       val hid = householdId ?: return
       try { action(sync, hid) } catch (e: Exception) { Log.w(TAG, "push falhou", e) }
   }
   ```
2. Troque as chamadas para `syncPush { s, hid -> s.pushAccount(hid, account) }`.
3. Registre a exceção em log em vez de engolir em silêncio — hoje um erro de mapeamento some sem rastro.

### 1.7 [Baixa] Cálculo de "dias até" duplicado
**Onde:** `DashboardScreen.kt` (`startOfDay`, `DAY_MS`, `relativeDay`), `Dashboardviewmodel.kt` (`DAY_MS`),
`ExpensesScreen.kt:303`, `NotificationWorker.kt:44`.

**Passo a passo:** resolvido junto com o item 1.1 — tudo passa a usar `DateRanges.daysUntil`/`startOfDay`.
Também troque a partição de urgentes em `buildDashboard` (`(dueDate - nowMs) / DAY_MS <= 3`) por
`DateRanges.daysUntil(it.dueDate, nowMs) <= 3`, para usar a mesma régua de dias da tela.

## Parte 2 — resto do projeto (telas, auth, captura, workers, regras, build e CI)

### 2.1 [Alta] Lembretes de vencimento nunca aparecem no Android 13+
**Onde:** `app/src/main/AndroidManifest.xml`; `MainActivity.kt:309` (`scheduleNotifications`).

**Problema:** o app pede `POST_NOTIFICATIONS` em tempo de execução, mas a permissão **não está declarada** no
manifesto (conferido também no manifesto final gerado pelo build). No Android 13+ o pedido é negado
automaticamente, sem mostrar diálogo; como o `NotificationScheduler.schedule` só roda quando a permissão é
concedida, o worker de lembretes nem chega a ser agendado.

**Passo a passo:**
1. No `AndroidManifest.xml`, junto da permissão de internet:
   `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
2. Agende o worker sempre (em `onCreate`), independente da permissão. No `NotificationWorker`, antes de
   notificar, teste `NotificationManagerCompat.from(context).areNotificationsEnabled()` e saia se for `false`.
3. Teste num emulador com API 33+: instale do zero, aceite o pedido e confira em
   *App Inspection › Background Task Inspector* que o trabalho `finance_notification_check` existe.

### 2.2 [Alta] Abrir a edição e salvar perde 1 centavo
**Onde:** `ExpensesScreen.kt:451` e `:578`, `SavingsGoalScreen.kt:137` — `(valor * 100).toLong().toString()`.

**Problema:** `toLong()` corta a fração. `0.29 * 100` vale `28.999999999999996` em `Double`, então o campo abre
com R$ 0,28 e, ao salvar, grava o valor errado. Acontece com vários valores comuns (0,29; 1,15; 4,35; 9,95...).

**Passo a passo:**
1. Em `util/Money.kt`, crie `fun Double.toCents(): Long = (this * 100).roundToLong()`.
2. Troque as três ocorrências por `valor.toCents().toString()`.
3. Teste unitário: `assertEquals(29L, 0.29.toCents())`, `assertEquals(115L, 1.15.toCents())`.
4. A solução definitiva é o item 1.3 (guardar centavos em `Long`), que elimina esse tipo de conversão.

### 2.3 [Alta] Depois de sair e entrar de novo, as telas continuam ligadas ao lar antigo
**Onde:** `MainActivity.kt:257-261` (`viewModel(factory = factory)`) e `onLogout` (`MainActivity.kt:269`).

**Problema:** `viewModel()` guarda os ViewModels na Activity, indexados só pela classe. A `factory` só é usada na
primeira vez. Ao sair da conta o app volta para o login **sem recriar a Activity**; ao entrar de novo (outro lar
ou outra conta), `viewModel()` devolve os ViewModels antigos, com o `FinanceRepository` antigo — `householdId`
antigo e `FirestoreSyncManager` já fechado. Os lançamentos novos vão para o lar errado (ou são recusados pelas
regras do Firestore) e o outro aparelho nunca os recebe. Entrar em lar pelo Perfil não tem o problema porque lá
já se chama `recreate()`.

**Passo a passo:**
1. Passe o lar como chave em cada ViewModel:
   `val dashboardVm: DashboardViewModel = viewModel(key = "dashboard-$householdId", factory = factory)` (idem
   para os outros quatro).
2. Além disso, no fim do logout chame `recreate()` (ver 2.7), igual ao fluxo de "entrar em lar".
3. Teste manual: entre no lar A, lance algo, saia, crie o lar B, lance algo e confira no console do Firestore
   que o lançamento foi para `households/B/transactions`.

### 2.4 [Alta] Trocar de celular pode fazer o app fechar ao abrir
**Onde:** `res/xml/data_extraction_rules.xml` (ainda é o modelo vazio) e `AppDatabase.getOrCreatePassphrase`.

**Problema:** no Android 12+, `allowBackup="false"` não bloqueia a **transferência entre aparelhos**; quem
controla isso é o `dataExtractionRules`, que está vazio. Na troca de celular o banco SQLCipher e o
`EncryptedSharedPreferences` são copiados, mas a chave do Android Keystore não sai do aparelho antigo. No
celular novo a leitura da senha do banco lança exceção dentro de `getInstance`, e o app fecha toda vez que abre.

**Passo a passo:**
1. Preencha `data_extraction_rules.xml`:
   ```xml
   <data-extraction-rules>
       <cloud-backup>
           <exclude domain="database" path="." />
           <exclude domain="sharedpref" path="." />
       </cloud-backup>
       <device-transfer>
           <exclude domain="database" path="." />
           <exclude domain="sharedpref" path="." />
       </device-transfer>
   </data-extraction-rules>
   ```
2. Em `getOrCreatePassphrase`, envolva a leitura em `try/catch`. Na falha: apague o arquivo
   `secure_db_prefs` (`context.deleteSharedPreferences`) e o banco (`context.deleteDatabase("finance_secure_db")`),
   gere uma senha nova e deixe o fluxo de `recoverHousehold` baixar tudo do Firestore. Faça o mesmo em
   `HouseholdManager.getSecurePrefs`.
3. Teste mínimo do passo 2: com o app instalado, apague só o arquivo de preferências
   (`adb shell run-as com.localbank rm shared_prefs/secure_db_prefs.xml`, em build debug) e abra o app — ele
   precisa se recuperar em vez de fechar. Para o passo 1, siga o guia oficial "Test backup and restore" (`bmgr`
   com transporte local).

### 2.5 [Alta] Regras do Firestore: lar legível por qualquer um e entrada sem precisar do código
**Onde:** `firestore.rules:16-39` e `:50-54`; `HouseholdManager.joinHousehold` e `generateInviteCode`.

**Problema:**
- `allow get: if signedIn()` em `households/{hid}` deixa qualquer usuário logado ler `inviteCode`, `members` e
  `memberNames` de qualquer lar cujo ID conheça (o próprio comentário do arquivo admite isso).
- O ramo de "entrar" no `update` confere só se `inviteExpiresAt` não venceu — **não confere o código**. Quem
  souber o ID do lar entra em qualquer momento dentro dessa 1 hora, inclusive depois de o convite ter sido usado
  e apagado.
- Em `invites/{code}`, qualquer usuário logado pode **criar** um convite apontando para qualquer lar e **apagar**
  qualquer convite.
- O código usa `kotlin.random.Random`, que não é gerador criptográfico.

**Passo a passo (recomendado — Cloud Function):**
1. Crie uma Cloud Function chamável `joinHousehold({ code })` com o Admin SDK: lê `invites/{code}`, confere a
   validade, adiciona `request.auth.uid` em `members` numa transação e apaga o convite.
2. Crie também `createInvite({ householdId })`, que confere se quem chama é membro antes de gerar o código.
3. Nas regras: `households/{hid}` → `allow get: if isMember(hid)`; remova o ramo de entrada do `update`;
   `invites/{code}` → `allow read, write: if false` (só a function mexe).
4. No app, troque `HouseholdManager.joinHousehold`/`createInvite` por `Firebase.functions.getHttpsCallable(...)`.
5. Troque `Random.nextInt` por `java.security.SecureRandom().nextInt(chars.length)` (na function, use
   `crypto.randomInt`).
6. Teste as regras com o Emulator Suite (`firebase emulators:start` + `@firebase/rules-unit-testing`): um
   não-membro não lê o lar e não consegue se adicionar sem a function.

**Sem Cloud Function (paliativo):** guarde no lar só o **hash** do código (`inviteHash`) e exija na regra de
entrada que o cliente grave `request.resource.data.inviteProof == resource.data.inviteHash`; ainda assim, feche o
`create`/`delete` de `invites` para quem não é membro do lar apontado.

### 2.6 [Média] Compra capturada pode virar dois lançamentos
**Onde:** `PendingCapturesScreen.kt:152-162`.

**Problema:** "Confirmar" dispara `addTransaction` (numa corrotina do ViewModel) e o `delete` da captura
(noutra corrotina). Até a captura sumir da lista, um segundo toque lança de novo; se o app for fechado entre as
duas operações, a captura volta e pode ser confirmada outra vez.

**Passo a passo:**
1. Leve o `PendingCaptureDao` para o `FinanceRepository` e crie `confirmCapture(capture, accountId, categoryId,
   description)`: dentro de `database.withTransaction { }`, insere a transação, recalcula o saldo e apaga a
   captura; o `syncPush` vem depois do commit (mesma ideia do 1.2).
2. Exponha no `ExpenseViewModel` e chame da tela. A tela deixa de acessar `AppDatabase` direto (vale também para
   a contagem em `MainNavigation.kt:68`).
3. No `CaptureCard`, guarde `var sending by remember { mutableStateOf(false) }` e desabilite o botão após o
   primeiro toque.

### 2.7 [Média] Sair da conta: limpeza do banco corre junto com o próximo login
**Onde:** `MainActivity.kt:269-279`; `ProfileScreen.kt:396-400`.

**Problema:** o logout manda a tela para o login e só **depois**, em segundo plano, roda `clearAllTables()` +
`seedDefaults`. Se a pessoa entra rápido e o `recoverHousehold` dispara `clearAndDownload`, o seed pode rodar
depois do download e criar categorias e a "Carteira" padrão duplicadas. Além disso, o botão do Perfil já chama
`signOut` e `clearHouseholdId`, e o `onLogout` chama os dois de novo.

**Passo a passo:**
1. Transforme o logout numa função `suspend`, rodando na ordem: `syncManager.close()` → `withContext(IO) {
   db.clearAllTables(); seedDefaults(...) }` → `AuthManager.signOut` → `HouseholdManager.clearHouseholdId` →
   `recreate()`. Mostre um carregando enquanto isso.
2. No `ProfileScreen`, o botão "Sair" chama só `onLogout()`.
3. Atenção: `clearAllTables()` não pode rodar na thread principal e não pode estar dentro de transação.

### 2.8 [Média] Datas digitadas à mão aceitam valores inválidos
**Onde:** `ExpensesScreen.kt:498-507` e `:546` (editar agendada); `SavingsGoalScreen.kt:353-364` e `:397` (prazo
da meta).

**Problema:** `SimpleDateFormat` é tolerante por padrão: "31/02/2026" vira 03/03/2026 sem erro. No formulário
de meta, uma data inválida vira "sem prazo" em silêncio.

**Passo a passo:**
1. Use o mesmo `DatePickerDialog` do `AddEntrySheet`. Mova `localToUtcDay`/`utcDayToLocal`
   (`AddEntrySheet.kt:379-393`) para `util/DateRanges.kt` para reaproveitar.
2. Se quiser manter o campo de texto, defina `sdf.isLenient = false` e mostre erro quando `parse` falhar.

### 2.9 [Média] Lembrete do dia do vencimento não sai, e editar a data não reativa o lembrete
**Onde:** `Daos.kt:209-214` (`getPendingNotification`) e `NotificationWorker.kt`.

**Problema:**
- A consulta pega `dueDate BETWEEN agora AND agora+7d`. Agendadas vencem à meia-noite (`AddEntrySheet.kt:293`),
  então no próprio dia o vencimento já é menor que "agora" e fica de fora. Na prática cada conta é avisada uma
  única vez, cerca de 7 dias antes, e nunca mais.
- Ao editar a data de vencimento, `notified` continua `true`.
- O worker marca só no Room (`markAsNotified`); o outro aparelho do lar também avisa. Se isso for desejado (cada
  pessoa recebe o seu aviso), apague o `markNotified` com sync do repositório, que hoje não é usado.

**Passo a passo:**
1. Troque o booleano por um estágio: `notifiedStage: Int` (0 = nada, 1 = avisou "faltam 3 dias", 2 = avisou
   "vence hoje"). Migração simples: `ALTER TABLE scheduled_expenses ADD COLUMN notifiedStage INTEGER NOT NULL
   DEFAULT 0`.
2. No worker, use `DateRanges.daysUntil` (item 1.1): `days in 1..3 && stage < 1` → avisa e grava 1; `days <= 0
   && stage < 2` → avisa "vence hoje/atrasada" e grava 2.
3. Em `FinanceRepository.updateScheduled`, se `dueDate` mudou, zere o estágio.
4. Considere rodar o worker 2x por dia (`repeatInterval = 12h`), já que a hora da execução é imprevisível.

### 2.10 [Média] Excluir conta apaga todos os lançamentos dela sem avisar
**Onde:** `AccountsScreen.kt:103-119`; `FinanceRepository.deleteAccount`.

**Problema:** o diálogo diz só "A conta será excluída", mas o `CASCADE` apaga todas as transações e agendadas
dessa conta — e o `deleteAccount` apaga também no Firestore, para todos do lar. Isso também muda o "Dinheiro nas
contas" da tela inicial.

**Passo a passo:**
1. Em `TransactionDao`/`ScheduledExpenseDao`, crie `countByAccount(accountId)`.
2. Antes de abrir o diálogo, busque as contagens e mostre: "Serão apagados X lançamentos e Y agendadas, para todos
   do lar. Isso não pode ser desfeito."
3. Se a conta tiver lançamentos, ofereça "Arquivar" (coluna `archived`) em vez de apagar.

### 2.11 [Média] Perfil corta os últimos botões em celular pequeno
**Onde:** `ProfileScreen.kt:116-121` e `:392`.

**Problema:** a tela é uma `Column` sem rolagem com 8 blocos e um `Spacer(Modifier.weight(1f))`. Em tela pequena
ou fonte grande, "Limpeza total" e "Sair da conta" ficam fora da tela e não dá para alcançá-los.

**Passo a passo:**
1. Adicione `.verticalScroll(rememberScrollState())` à `Column`.
2. Remova o `Spacer(weight)` (não funciona dentro de rolagem) e use um `Spacer(Modifier.height(24.dp))`.
3. Teste com *Configurações › Tamanho da fonte* no máximo.

### 2.12 [Média] Quatro ViewModels leem o histórico inteiro a cada mudança
**Onde:** `Dashboardviewmodel.kt`, `Expenseviewmodel.kt`, `BudgetViewModel.kt:41`, `ReportViewModel.kt:64`.

**Problema:** todos assinam `getAllTransactions()`. Cada lançamento (seu ou vindo do outro aparelho) faz o Room
ler e converter **todas** as linhas quatro vezes, e cada ViewModel percorre tudo de novo em Kotlin. Complementa
o item 1.4.

**Passo a passo:**
1. **Orçamento:** consulta agregada como `Flow`:
   ```kotlin
   data class CategoryTotal(val categoryId: String?, val total: Double)
   @Query("""SELECT categoryId, SUM(amount) AS total FROM transactions
             WHERE type = 'EXPENSE' AND date BETWEEN :from AND :to GROUP BY categoryId""")
   fun expenseByCategory(from: Long, to: Long): Flow<List<CategoryTotal>>
   ```
2. **Relatório:** totais por mês no SQL, só para o intervalo exibido (6 meses + semestre):
   `strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') AS ym ... GROUP BY ym, type`. Use
   `expenseByCategory` para o mês e o semestre.
3. **Despesas:** mês selecionado no ViewModel + `flatMapLatest { getTransactionsByPeriod(from, to) }`.
4. **Painel:** ver item 1.4.
5. Mantenha as funções puras (`buildDashboard`, `buildReport`) recebendo os dados já agregados, para os testes
   continuarem simples.

### 2.13 [Média] Dependências descontinuadas, desatualizadas ou sem uso
**Onde:** `app/build.gradle.kts`, `gradle/libs.versions.toml`.

**Problema e passo a passo (um PR por item, testando login e abertura do banco a cada um):**
1. `net.zetetic:android-database-sqlcipher:4.5.4` está **descontinuada**. Troque por
   `net.zetetic:sqlcipher-android` (versão atual) + `androidx.sqlite`. O import muda para
   `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` e é preciso `System.loadLibrary("sqlcipher")` antes
   de abrir o banco. O formato do arquivo é compatível (SQLCipher 4). A versão nova também atende à exigência de
   páginas de 16 KB dos aparelhos novos.
2. `firebase-analytics` está no build, mas **não é usado em lugar nenhum** do código, e traz para o manifesto final
   as permissões `AD_ID` e `ACCESS_ADSERVICES_*`. Num app financeiro isso pesa na loja e na privacidade. Remova a
   dependência.
3. `play-services-auth` (`GoogleSignIn`) está descontinuado. Migre `AuthManager`/`LoginScreen` para Credential
   Manager (`androidx.credentials:credentials`, `credentials-play-services-auth` e
   `com.google.android.libraries.identity.googleid`) com `GetGoogleIdOption`.
4. `security-crypto:1.1.0-alpha06` (alfa, e a biblioteca foi descontinuada). Curto prazo: atualize para a última
   1.1.x. Médio prazo: guarde a senha do banco cifrada com uma chave do Keystore em DataStore.
5. Versões antigas: `coreKtx 1.10.1`, `activityCompose 1.8.0`, `lifecycle 2.8.0`, `composeBom 2024.09.00`,
   `room 2.7.0`, `work 2.9.0`. Atualize pelo *Project Structure › Suggestions* do Android Studio, e mova as
   versões soltas do `build.gradle.kts` para o `libs.versions.toml`.

### 2.14 [Média] ProGuard desliga o encolhimento de bibliotecas inteiras
**Onde:** `app/proguard-rules.pro:13-26`.

**Problema:** `-keep class com.google.firebase.** { *; }`, `com.google.android.gms.**` e `androidx.compose.** {
*; }` impedem o R8 de remover e otimizar essas bibliotecas: APK maior e inicialização mais lenta. Elas já trazem
as próprias regras. E como o app converte os modelos para `Map` à mão (`FirestoreSyncManager`), nem os modelos
precisam de reflexão.

**Passo a passo:**
1. Apague os blocos Firebase, GMS, Google Sign-In e Compose. Mantenha `-keepattributes SourceFile,LineNumberTable`.
2. Mantenha só o necessário do SQLCipher (`-keep class net.sqlcipher.** { *; }`, ou o pacote novo após 2.13).
3. Gere o release, compare o tamanho do APK e teste login, sincronização, captura de notificação e relatório no
   build de release.

### 2.15 [Média] CI não roda testes
**Onde:** `.github/workflows/build.yml` e `release.yml`.

**Passo a passo:**
1. Em `build.yml`, antes do `assembleDebug`: `- run: ./gradlew testDebugUnitTest lintDebug`.
2. Em `release.yml`, rode os testes antes do `assembleRelease` e adicione no job `permissions: contents: write`
   (necessário para `softprops/action-gh-release` quando o token padrão é só leitura).
3. Passe segredos por `env:` em vez de `${{ secrets.X }}` direto dentro do `run:` (evita que o valor entre no
   script do shell).
4. Use `gradle/actions/setup-gradle` no lugar do `actions/cache` manual (cache melhor e mais rápido).

### 2.16 [Baixa] Recorrência aparece em inglês na lista de despesas
**Onde:** `ExpensesScreen.kt:315-317` — `recurrenceRule.name.lowercase()...` mostra "Monthly".

**Passo a passo:** troque por `expense.recurrenceRule.label` (de `ui/util/Formatters.kt`), que já devolve
"Mensal".

### 2.17 [Baixa] Texto diz "Válido por 24 horas", mas o convite vale 1 hora
**Onde:** `HouseholdScreen.kt:189`.

**Passo a passo:** troque o texto por "Válido por 1 hora" (o `ProfileScreen.kt:296` já está certo). Melhor
ainda: crie uma constante `INVITE_TTL_MS` no `HouseholdManager` e gere o texto a partir dela.

### 2.18 [Baixa] Categorias padrão podem duplicar
**Onde:** `AppDatabase.kt:153-162` (seed com `UUID.randomUUID()`) e `ExpenseViewModel.addCategory` (ID
determinístico por nome).

**Problema:** as categorias padrão nascem com ID aleatório. Se a pessoa cria "Alimentação" de novo, o ID
determinístico é outro e a categoria aparece duas vezes.

**Passo a passo:**
1. Crie `fun categoryId(type: TransactionType, name: String)` (a mesma fórmula do `addCategory`) e use no seed.
2. No `addCategory`, procure antes por nome + tipo (`@Query("SELECT * FROM categories WHERE type = :type AND
   lower(name) = lower(:name) LIMIT 1")`) e reaproveite se já existir.

### 2.19 [Baixa] Criar lar não é atômico
**Onde:** `HouseholdManager.createHousehold` (`:133-137`).

**Problema:** grava o lar e depois o convite em duas chamadas. Se a segunda falhar, a tela mostra erro e um novo
toque cria um **segundo** lar.

**Passo a passo:** grave os dois com `db.batch()` (ou dentro da Cloud Function do item 2.5). Antes de criar,
procure lares com `ownerId == uid` e reaproveite se houver.

### 2.20 [Baixa] Link de atualização não é validado
**Onde:** `MainActivity.kt:114` e `UpdateChecker`.

**Passo a passo:** só abra a URL se começar com `https://github.com/HeitorRibeir0/LocalBank/releases/`. Assim um
Remote Config mal configurado (ou uma conta comprometida) não manda o usuário baixar APK de outro lugar.

### 2.21 [Baixa] Apagar o lançamento de uma conta paga não desfaz o "pago"
**Onde:** `FinanceRepository.deleteTransaction`; `Transaction.scheduledId`.

**Passo a passo:** se `transaction.scheduledId != null`, pergunte no diálogo de exclusão "Marcar a conta como não
paga?" e, se sim, grave `isPaid = false` na agendada (com sync). Avalie também apagar a próxima ocorrência criada
pelo pagamento.

### 2.22 [Baixa] Exclusões sem confirmação e botões pequenos demais
**Onde:** `ExpensesScreen.kt:362` (excluir agendada), `BudgetScreen.kt:116` (excluir orçamento); ícones de 24–32 dp
em `ExpensesScreen.kt:282`, `AccountsScreen.kt:163`.

**Passo a passo:**
1. Para agendada e orçamento, mostre um Snackbar com "Desfazer" (guarde o item e reinsira se tocar) ou um diálogo.
2. Garanta área de toque de 48 dp: remova o `Modifier.size(24.dp)` do `IconButton` (o ícone pode continuar
   pequeno) ou use `Modifier.minimumInteractiveComponentSize()`.

### 2.23 [Baixa] Nome de categoria aparece de jeitos diferentes em cada tela
**Onde:** `BudgetViewModel.kt:57`, `ReportViewModel.kt:111` e `:163`, `BudgetScreen.kt:158` usam `category.name`; o
resto usa `category.displayName`.

**Passo a passo:** troque por `displayName` nesses quatro pontos.

### 2.24 [Baixa] Pequenos desperdícios na recomposição
**Onde:** `CurrencyField.kt:37` e `AddEntrySheet.kt:139` criam `CurrencyVisualTransformation()` (e um
`NumberFormat` novo) a cada tecla; `ReportScreen.kt:158` usa `items(...)` sem `key`.

**Passo a passo:**
1. Mova o `formatter` para um `companion object` de `CurrencyVisualTransformation` e use
   `remember { CurrencyVisualTransformation() }` nos dois lugares.
2. `items(state.categoryExpenses, key = { it.categoryName })`.

### 2.25 [Baixa] Mês selecionado se perde ao girar a tela
**Onde:** `ExpensesScreen.kt:56` (`remember`).

**Passo a passo:** guarde o mês como `Int` (`DateRanges.monthIndex`) em `rememberSaveable`, ou leve para o
ViewModel junto com o item 2.12. Avalie também liberar a seta "próximo mês" em Despesas, para ver as contas
agendadas do mês seguinte.

### 2.26 [Baixa] Porcentagens mudam de formato conforme o idioma do aparelho
**Onde:** `ReportScreen.kt:189`, `DonutChart.kt:121` e `:187`, `Expenseviewmodel.kt:91` e `:94`.

**Passo a passo:** use `String.format(PtBr, "%.1f%%", valor)` (o `PtBr` já existe em `Formatters.kt`).

### 2.27 [Baixa] Android 15 força tela de ponta a ponta
**Onde:** `Theme.kt:104-110` (`window.statusBarColor`), `MainActivity.onCreate`.

**Problema:** com `targetSdk = 35`, no Android 15 `statusBarColor`/`navigationBarColor` são ignorados e o app
desenha atrás das barras do sistema. Telas sem `Scaffold` (Login, Lar, carregamentos) não tratam os insets.

**Passo a passo:**
1. Chame `enableEdgeToEdge()` no `onCreate`, antes do `setContent`, e apague o bloco `SideEffect` do `Theme.kt`.
2. Nas telas sem `Scaffold`, adicione `Modifier.safeDrawingPadding()` no contêiner raiz.

### 2.28 [Baixa] Faltam testes justamente nas partes de maior risco
**Onde:** `app/src/test` só cobre funções puras; `ExampleUnitTest` e `ExampleInstrumentedTest` são modelos vazios.

**Passo a passo:**
1. `PurchaseParserTest` (é função pura e não tem teste): textos reais de Nubank, Mercado Pago e iFood, valores com
   milhar, notificação sem valor, sem estabelecimento.
2. Testes do `FinanceRepository` com Room em memória (`Room.inMemoryDatabaseBuilder`, em `androidTest` ou com
   Robolectric): pagar agendada recorrente, excluir conta, recalcular saldo.
3. Teste de migração com `MigrationTestHelper` (os schemas já estão em `app/schemas/`).
4. Apague os dois testes de exemplo.

### 2.29 [Baixa] Nomes de cor enganosos
**Onde:** `ui/theme/Color.kt:138-147` (`DarkBg`, `DarkCard`, `OnDarkText`...), usados em ~180 lugares.

**Problema:** eles já seguem o tema (são atalhos para `LocalAppColors.current`), mas o nome sugere cor fixa
escura e confunde quem lê; parte das telas usa `appColors.background`, parte `DarkBg`.

**Passo a passo:** marque os atalhos com `@Deprecated("Use LocalAppColors.current.x")` e troque aos poucos, tela
por tela, ao mexer nelas.

## Ordem sugerida
1. **Rápidos e de alto impacto (uma tarde):** 2.1, 2.2, 2.16, 2.17, 1.1, 2.11, 2.4 (passo 1).
2. **Consistência de dados:** 2.3, 2.7, 1.2, 2.6, 2.10.
3. **Segurança:** 2.5, 2.20, 2.13 (itens 1 e 2).
4. **Desempenho:** 1.4, 2.12, 2.14, 2.24.
5. **Estrutura (planejar):** 1.3 (centavos em `Long`), 2.9, 2.13 (itens 3 a 5), 2.15, 2.28.
6. **Resto:** itens [Baixa] conforme mexer nas telas.
