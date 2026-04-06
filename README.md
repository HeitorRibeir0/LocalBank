# LocalBank

Aplicativo Android de finanças pessoais para controle compartilhado entre cônjuges ou parceiros.

## Funcionalidades

- **Dashboard** — saldo atual, entradas e saídas do mês, próximos vencimentos e gastos por categoria
- **Despesas** — registro de transações e despesas agendadas com filtro por mês
- **Orçamento** — limite por categoria com alerta ao atingir 80% e 100%
- **Metas de economia** — progresso visual com depósitos e prazo
- **Relatório** — gráfico de tendência, balanço mensal e panorama semestral
- **Household** — conta compartilhada com sincronização em tempo real entre dispositivos
- **Atualização automática** — notificação no app quando uma nova versão é lançada

## Tecnologias

- Kotlin + Jetpack Compose
- Room (banco local)
- Firebase Auth (Google Sign-In)
- Firestore (sincronização entre dispositivos)
- Firebase Remote Config (controle de versão)
- GitHub Actions (CI/CD — build, release e atualização do Remote Config automáticos)

## Requisitos

- Android 7.0+ (API 24)
- Conta Google para login

## Instalação

Baixe o APK mais recente em [Releases](../../releases/latest) e instale no dispositivo.

> Em **Configurações → Segurança**, habilite "Instalar de fontes desconhecidas" antes de instalar.

## Desenvolvimento

```bash
# Clonar
git clone https://github.com/HeitorRibeir0/LocalBank.git

# Abrir no Android Studio e adicionar o google-services.json do seu projeto Firebase em app/
```

### Release

```bash
git add .
git commit -m "feat: descrição"
git tag v1.x
git push origin main v1.x
```

O GitHub Actions compila o APK assinado, cria o Release e atualiza o Firebase Remote Config automaticamente.
