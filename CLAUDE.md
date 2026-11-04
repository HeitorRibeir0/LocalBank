# LocalBank

App Android (Kotlin + Jetpack Compose) de finanças do lar: duas ou mais pessoas compartilham contas,
lançamentos, contas a pagar, orçamentos e metas. Banco local Room (cifrado com SQLCipher) sincronizado com
Firestore entre os aparelhos do mesmo lar (household). Projeto Firebase: `localbank-8e349`.

## Comandos

| O quê | Comando |
|---|---|
| Testes do app (inclui migrações e repositório) | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` (o release roda `lintVitalRelease`, mais rígido) |
| APK debug / release | `./gradlew assembleDebug` / `./gradlew assembleRelease` |
| Functions + regras do Firestore | `cd functions && npm test` |

`npm test` sobe o emulador do Firestore e precisa de **Java 21+**. O JDK 17 do sistema não serve; use o JBR do
Android Studio: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="$JAVA_HOME/bin:$PATH"`.

Não há aparelho/emulador Android ligado a este PC por padrão: tudo que depende de tela, login Google,
Keystore ou SQLCipher real só é verificado num celular. Diga isso ao relatar o trabalho.

## Estrutura

```
app/src/main/java/com/localbank/
  finance/
    MainActivity.kt          bootstrap: login → recuperar/escolher lar → sync → navegação; logout
    auth/AuthManager.kt      login Google pelo Credential Manager + Firebase Auth
    capture/                 lê notificações de banco (NotificationListener) → fila pending_captures
    data/model/              entidades Room (dinheiro em centavos Long)
    data/database/           AppDatabase (versão, migrações, seed), Daos, DbPassphrase (senha do SQLCipher)
    data/repository/         FinanceRepository: única porta de escrita; grava local e faz syncPush
    sync/FirestoreSyncManager.kt  push/pull Room ↔ Firestore, listeners, upload/download inicial
    sync/HouseholdManager.kt      id do lar no aparelho + chamadas às Cloud Functions
    ui/MainNavigation.kt     abas e AddEntrySheet
    ui/screens/              uma tela por arquivo
    ui/viewmodel/            ViewModels + funções puras buildDashboard/buildReport (testadas)
    ui/util/Formatters.kt    NumberFormat.cents(), signed(), datas pt-BR
    update/UpdateChecker.kt  compara versionCode com o Remote Config (latest_version_code)
    util/                    DateRanges (mês, semestre, daysUntil), Money (toCents/toReais)
    worker/                  NotificationWorker (lembrete de vencimento) a cada 12h
  ui/theme/                  AppColors por tema, LocalAppColors, tipografia
app/schemas/                 schemas exportados do Room (1 json por versão) — versionar sempre
app/src/test/                JUnit + Robolectric (MigrationTest, FinanceRepositoryTest, build*Test, parser)
functions/                   Cloud Functions (Node 22): createHousehold, createInvite, joinHousehold + testes
firestore.rules              regras (testadas em functions/test/household.test.js)
.github/workflows/           build.yml (testes, lint, APK debug, testes das functions) e release.yml
.github/scripts/update_remote_config.py   publica a versão nova no Remote Config
gradle/libs.versions.toml    todas as versões de dependências (não coloque versão solta no build.gradle.kts)
```

## Fluxo de dados

1. A UI chama o ViewModel, que chama o `FinanceRepository`. Nenhuma tela escreve no DAO direto.
2. O repositório grava no Room e depois chama `syncPush`, que envia ao Firestore se houver lar. Falha de rede só
   gera log: o app é offline-first e o SDK do Firestore reenvia quando a rede volta.
3. Operações com várias escritas (pagar agendada, confirmar captura) rodam em `db.withTransaction`, e o push
   acontece só depois do sucesso local.
4. O saldo da conta nunca é somado à mão: `recalculateBalance` recalcula pela soma das transações.
5. `FirestoreSyncManager` escuta as coleções do lar e aplica cada snapshot numa transação do Room. Pais
   (contas, categorias) entram antes dos filhos; filho sem pai é adiado e tentado de novo até 3 vezes.
6. Entrada no lar: membro novo baixa tudo (`clearAndDownload`); quem criou o lar envia tudo uma vez
   (`uploadAll`); quem reinstalou é recuperado por `recoverHousehold` (consulta `members array-contains uid`).
7. ViewModels leem só o necessário: o painel lê o mês atual (`getTransactionsByPeriod`) mais `getRecent(5)`;
   Despesas lê o mês selecionado (`ExpenseViewModel.setPeriod`); Relatório e Orçamento usam somas agregadas no
   SQL (`monthlyTotals`, `expenseByCategory`).

## Regras que não podem ser quebradas

**Dinheiro**
- Todo valor é `Long` em centavos (`amountCents`, `balanceCents`, `limitCents`, `targetCents`, `savedCents`).
  `Double` só aparece na borda: formatação e gráficos.
- **Nunca** passe centavos para `NumberFormat.format()`: 12550 vira "R$ 12.550,00". Use `currency.cents(x)`,
  ou `x.toReais()` para gráficos. O compilador não pega esse erro, por isso os campos de estado têm sufixo
  `Cents`.
- Entrada do usuário: `CurrencyField` já entrega dígitos em centavos (`amountRaw.toLongOrNull()`).

**Banco (Room)**
- Versão atual: **8**. Migrações em `AppDatabase.kt` (4→5, 5→6, 6→7, 7→8); 1–3 são destrutivas.
- Toda mudança de entidade exige: subir a versão, uma `Migration` nova, o json novo em `app/schemas/`
  (gerado no build) e um caso em `MigrationTest`.
- Não use `ALTER TABLE ... DROP COLUMN`: o SQLite do Robolectric não tem, então não dá para testar.
- Não faça `DROP TABLE accounts` com `transactions`/`scheduled_expenses` no lugar: o drop dispara o
  `ON DELETE CASCADE` e apaga os lançamentos. Padrão seguro (veja `MIGRATION_7_8`): copiar as filhas para
  tabelas TEMP, apagar as filhas, recriar o pai, recriar as filhas a partir das TEMP.
- `AccountDao.insert` usa `ABORT`, não `REPLACE`: o REPLACE apaga a linha e o cascade leva as transações.
  Pelo mesmo motivo categorias vindas do Firestore usam insert-ignore + update.
- Senha do SQLCipher: `DbPassphrase` (chave AES no Android Keystore + texto cifrado em SharedPreferences).
  Se a senha se perder, o banco é recriado e os dados voltam pelo Firestore. Backup do Android está
  desligado de propósito (a chave do Keystore não vai junto).

**Firestore / sincronização**
- Todo push usa `set()` sem merge, então o documento inteiro é regravado. Campo novo só vale quando todos os
  aparelhos conhecerem o campo; até lá um aparelho antigo apaga o campo ao regravar.
- Transição para centavos: grava os dois campos (`amount` em reais e `amountCents`) e **lê o em reais primeiro**
  (`FirestoreSyncManager.cents()`), porque a versão antiga só atualiza ele. Veja a pendência P2.
- Metas: depósito é incremento atômico no servidor (`FieldValue.increment`) e edição usa merge só dos campos de
  metadados, para dois aparelhos não se sobrescreverem.
- Lar e convites: o app **não escreve** em `households/{id}` nem em `invites`; quem escreve são as Cloud
  Functions. As regras só deixam membro ler o lar e mexer nas subcoleções.

**Notificações**
- `notifiedStage` local: 0 = nada, 1 = avisou "faltam 1–3 dias", 2 = avisou "vence hoje/atrasada". Mudar o
  vencimento zera o estágio. A conta paga ou a próxima ocorrência recorrente nasce com 0.

**UI**
- Cores sempre por `LocalAppColors.current.*` (os atalhos `DarkBg`, `OnDarkText` etc. foram removidos).
- Conta arquivada (`archived`) some dos seletores (`ExpenseViewModel.activeAccounts`) e do saldo do painel,
  mas continua nos relatórios e na tela de contas.

## Release

1. Rodar `./gradlew testDebugUnitTest lintDebug assembleRelease` e `cd functions && npm test`.
2. Se mudou `functions/` ou `firestore.rules`, publique **antes** do app (ordem da pendência P1).
3. `git tag -a vX.Y -m "LocalBank vX.Y" && git push origin main vX.Y`.
4. O `release.yml` roda os testes, compila o APK assinado, cria a release no GitHub e atualiza o Remote Config.
   A partir daí o app de todo mundo mostra o aviso de atualização, então só suba a tag com o app testado num
   celular.
5. `versionCode` = número de commits (`git rev-list --count HEAD`) e `versionName` = última tag. Por isso o
   checkout da CI usa `fetch-depth: 0`.
6. Não há `gh` instalado neste PC: acompanhe em https://github.com/HeitorRibeir0/LocalBank/actions.

Segredos da CI (GitHub → Settings → Secrets): `GOOGLE_SERVICES_JSON`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`, `FIREBASE_SERVICE_ACCOUNT`, `FIREBASE_PROJECT_ID`. Eles chegam aos passos por
`env:`, nunca interpolados direto no `run:`. O `google-services.json` não é versionado.

## Histórico

Revisão completa de código em 2026-11-04, publicada na **v1.12**. Resumo do que mudou (detalhes no `git log`):
- Dinheiro migrado de `Double` para centavos `Long` (migração 7→8) e contas arquiváveis.
- Notificações em dois estágios (migração 6→7), incluindo o aviso no dia do vencimento.
- Consultas do painel, despesas, relatório e orçamento limitadas ao período / agregadas no SQL.
- Lar e convites passaram para Cloud Functions; regras do Firestore fechadas (ainda falta publicar, P1).
- Login pelo Credential Manager (saiu o `play-services-auth`); senha do banco no Keystore.
- Testes com Robolectric (migrações 5→8, repositório) e testes das functions/regras no emulador, na CI.

Lição dessa sessão: uma primeira rodada declarou "29/29 itens" sem verificar, e havia regras do Firestore
quebrando o lar, uma migração que derrubaria o app e telas sem a mudança prometida. Antes de dizer que algo está
pronto: rodar os testes, conferir o código contra o pedido e dizer o que não foi testado em aparelho. Não crie
arquivos de resumo (`*_SUMMARY.md`) e não invente números de desempenho.

# Pendências

Prioridade: **[Alta]** bug que o usuário vê ou dado errado · **[Média]** desempenho ou risco de divergência ·
**[Baixa]** organização/legibilidade. Ao resolver um item, apague-o daqui no mesmo commit.

### P1 [Alta] Publicar functions e regras do Firestore — nesta ordem
O app v1.12 já chama as Cloud Functions (`functions/index.js`) para criar lar, gerar convite e entrar. Sem elas
publicadas, essas três ações falham no app novo.

1. `firebase deploy --only functions` (precisa do plano Blaze; `npm i -g firebase-tools` e `firebase login`).
2. Esperar todos do lar estarem na v1.12 ou superior.
3. Só então `firebase deploy --only firestore:rules`.

Publicar as regras antes do passo 2 quebra criar/entrar no lar para quem está na versão antiga. Depois disso,
considere App Check nas functions (`enforceAppCheck`) contra quem tenta adivinhar códigos de convite.

### P2 [Média] Parar de gravar os campos em reais no Firestore
Quando todos estiverem na v1.12+: em `FirestoreSyncManager`, ler só `*Cents`, parar de gravar `amount`,
`balance`, `limitAmount`, `targetAmount`, `savedAmount` e tirar o incremento de `savedAmount`. Até lá, um
aparelho antigo que regravar uma conta apaga o campo `archived`.

### P3 [Baixa] Tirar a `security-crypto` (descontinuada)
Ainda é usada em `HouseholdManager` (prefs do lar) e em `DbPassphrase.readLegacy` (migração única da senha do
banco). Passe as prefs do lar para `SharedPreferences` comum migrando os valores (o id do lar não é segredo) e
remova `readLegacy` quando ninguém mais tiver a senha no formato antigo.

### P4 [Baixa] Avisos de API obsoleta
`Icons.Filled.TrendingUp/Down/Flat` e `Logout` → versões `AutoMirrored`; `LocalLifecycleOwner` em
`PendingCapturesScreen` → `androidx.lifecycle.compose` (dependência `lifecycle-runtime-compose`).
