# LocalBank

App Android (Kotlin + Jetpack Compose) de finanças do lar. Room local + sincronização com Firestore
entre os aparelhos do mesmo lar (household). Testes unitários: `./gradlew testDebugUnitTest`.

# Melhorias pendentes

Lista de revisão de código feita em 2026-11-04. Cada item diz onde está, por que é problema e o passo a passo
para corrigir. Ao resolver um item, apague-o daqui (ou marque como feito) no mesmo commit.

Prioridade: **[Alta]** bug que o usuário vê ou dado errado · **[Média]** desempenho ou risco de divergência ·
**[Baixa]** organização/legibilidade.

## Parte 1 — camada de dados, sincronização e tela inicial

### 1.3 [Alta] Valores em `Double` (migrar para centavos em `Long`)
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

**Paliativo (feito):** chame `.roundCents()` em `ExpenseViewModel.addTransaction`,
`addScheduledExpense`, nos `mapTo*` do `FirestoreSyncManager` e no resultado de `buildDashboard`/`buildReport`.
Corrija o comentário de `Money.kt` para dizer a verdade.

## Parte 2 — resto do projeto

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
5. ~~SecureRandom no app~~ (feito). Na function, use `crypto.randomInt`.
6. Teste as regras com o Emulator Suite (`firebase emulators:start` + `@firebase/rules-unit-testing`): um
   não-membro não lê o lar e não consegue se adicionar sem a function.

**Sem Cloud Function (paliativo):** guarde no lar só o **hash** do código (`inviteHash`) e exija na regra de
entrada que o cliente grave `request.resource.data.inviteProof == resource.data.inviteHash`; ainda assim, feche o
`create`/`delete` de `invites` para quem não é membro do lar apontado.

**Atenção:** só mude as regras junto com a function. Fechar `invites` ou o `get` do lar antes disso quebra criar,
entrar e recuperar o lar (já aconteceu uma vez e foi desfeito).

### 2.10 [Baixa] Arquivar conta em vez de apagar
**Onde:** `AccountsScreen.kt`; `FinanceRepository.deleteAccount`.

O aviso com a contagem de lançamentos já existe. Falta o passo 3: se a conta tiver lançamentos, oferecer
"Arquivar" (coluna `archived`, com migração) em vez de apagar.

### 2.13 [Média] Dependências que faltam
**Onde:** `app/build.gradle.kts`.

Já feito: SQLCipher novo, `firebase-analytics` removido, versões atualizadas.
1. `play-services-auth` (`GoogleSignIn`) está descontinuado. Migre `AuthManager`/`LoginScreen` para Credential
   Manager (`androidx.credentials:credentials`, `credentials-play-services-auth` e
   `com.google.android.libraries.identity.googleid`) com `GetGoogleIdOption`.
2. `security-crypto:1.1.0-alpha07` (alfa, biblioteca descontinuada). Médio prazo: guarde a senha do banco cifrada
   com uma chave do Keystore em DataStore.
3. Mova as versões soltas do `build.gradle.kts` (room, work, firebase...) para o `libs.versions.toml`.

### 2.28 [Baixa] Testes que faltam
Já feito: `PurchaseParserTest`; `BuildReportTest` testa a função agregada; exemplos apagados.
1. Testes do `FinanceRepository` com Room em memória (`Room.inMemoryDatabaseBuilder`, em `androidTest` ou com
   Robolectric): pagar agendada recorrente, excluir conta, recalcular saldo.
2. Teste de migração com `MigrationTestHelper`, principalmente a **6→7** (recria `scheduled_expenses` e converte
   `notified` em `notifiedStage`) — ainda não foi rodada num aparelho.

### 2.29 [Baixa] Nomes de cor enganosos
**Onde:** `ui/theme/Color.kt:138-147` (`DarkBg`, `DarkCard`, `OnDarkText`...), usados em ~180 lugares.

**Problema:** eles já seguem o tema (são atalhos para `LocalAppColors.current`), mas o nome sugere cor fixa
escura e confunde quem lê; parte das telas usa `appColors.background`, parte `DarkBg`.

**Passo a passo:** os atalhos já estão `@Deprecated`; troque aos poucos, tela por tela, ao mexer nelas.

## Ordem sugerida
1. ~~Rápidos, consistência de dados, desempenho e o resto dos itens [Baixa]~~ **RESOLVIDOS** (até 2026-11-04).
2. **Segurança:** 2.5 (Cloud Functions + regras juntas).
3. **Estrutura:** 1.3 (centavos em `Long`), 2.13, 2.28.
4. **Quando mexer nas telas:** 2.10 (arquivar), 2.29.
