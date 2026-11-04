# LocalBank

App Android (Kotlin + Jetpack Compose) de finanças do lar. Room local + sincronização com Firestore
entre os aparelhos do mesmo lar (household). Dinheiro sempre em centavos (`Long`); `Double` só na formatação.

- Testes do app: `./gradlew testDebugUnitTest` (inclui migrações e repositório com Robolectric).
- Cloud Functions e regras do Firestore: `cd functions && npm test` (sobe o emulador; precisa de Java 21+ —
  o JBR do Android Studio serve: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`).
- Toda migração nova do Room ganha um caso em `MigrationTest`. Não use `DROP COLUMN` (o SQLite dos testes não
  tem) e não recrie `accounts` com as filhas no lugar (o `DROP TABLE` dispara o `ON DELETE CASCADE`).

# Melhorias pendentes

Prioridade: **[Alta]** bug que o usuário vê ou dado errado · **[Média]** desempenho ou risco de divergência ·
**[Baixa]** organização/legibilidade.

Revisão de código de 2026-11-04: todos os itens de código foram resolvidos. O que sobra depende de publicar
ou de esperar todos os aparelhos atualizarem. Ao resolver um item, apague-o daqui no mesmo commit.

### P1 [Alta] Publicar functions e regras do Firestore — nesta ordem
O app já chama as Cloud Functions (`functions/index.js`) para criar lar, gerar convite e entrar. As regras novas
(`firestore.rules`) fecham `invites` e a escrita no documento do lar.

1. `firebase deploy --only functions` (precisa do plano Blaze no projeto `localbank-8e349`).
2. Publicar a versão nova do app e esperar todos do lar atualizarem.
3. Só então `firebase deploy --only firestore:rules`.

Inverter 2 e 3 quebra criar/entrar no lar para quem ainda está na versão antiga; publicar o app antes do passo 1
quebra para quem atualizou. Depois: considerar App Check nas functions (`enforceAppCheck`) contra quem tenta
adivinhar códigos.

### P2 [Média] Parar de gravar os campos em reais no Firestore
`FirestoreSyncManager` grava `amount` e `amountCents` (idem `balance`, `limitAmount`, `targetAmount`,
`savedAmount`) e lê o campo em reais primeiro, porque a versão antiga do app só atualiza ele. Quando todos estiverem
na versão nova: ler só `*Cents`, parar de gravar os campos em reais e o incremento de `savedAmount`.
Até lá, um aparelho antigo que regravar uma conta apaga o campo `archived` (o `set()` dele não conhece o campo).

### P3 [Baixa] Tirar a `security-crypto` (descontinuada)
Ainda é usada em `HouseholdManager` (prefs do lar) e em `DbPassphrase.readLegacy` (migração única da senha do
banco). Passe as prefs do lar para `SharedPreferences` comum (o id do lar não é segredo, quem lê já está no
aparelho) migrando os valores, e remova `readLegacy` quando ninguém mais tiver a senha no formato antigo.
