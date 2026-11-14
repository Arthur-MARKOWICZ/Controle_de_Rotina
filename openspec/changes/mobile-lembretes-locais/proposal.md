# Proposal

## Why

O produto só age quando alguém o abre. Prazos vencem e blocos aprovados não começam, e nada avisa ([issue #73](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/73), OPP-013). O app Android (change `app-mobile-android`) já tem sessão nativa e acesso à API, e deixou as notificações para depois. Esta fatia entrega lembretes locais simples sem mexer no backend.

## What Changes

- Dois lembretes locais no app Android, cada um com chave própria e horário configurável:
  - **Resumo do dia**: no horário escolhido (padrão 08:00), uma notificação com o estado do plano de hoje: sem plano, rascunho para aprovar, ou aprovado com blocos pendentes e o próximo bloco.
  - **Prazo amanhã (D-1)**: no horário escolhido (padrão 19:00), uma notificação com as tarefas abertas cujo prazo cai amanhã (no fuso do aparelho).
- No horário, o app **consulta a API** (`GET /planos`, `GET /execucoes`, `GET /tarefas` com `prazoDe`/`prazoAte`) usando a mesma sessão do app. Não há cache local de dados. Sem rede, sem sessão ou fora da janela de tolerância, a notificação simplesmente não sai.
- Padrão conservador: os dois lembretes vêm **desligados**. No máximo uma notificação por tipo por dia, e nada sai quando não há o que avisar (plano todo executado, nenhum prazo amanhã).
- No Android 13+, a permissão de notificação é pedida ao ligar o primeiro lembrete, com contexto. Se o usuário recusar, o lembrete volta a desligado e o app segue normal.
- Tela de configuração em **Perfil > Lembretes** (liga/desliga e horário de cada tipo).
- Tocar na notificação abre o app na aba certa: Plano para o resumo, Tarefas para o D-1.
- Logout cancela os lembretes agendados e remove as notificações visíveis. O próximo login reagenda conforme as preferências do aparelho.
- O cliente mobile passa a enviar `prazoDe`/`prazoAte` em `GET /tarefas`. O backend já aceita esses parâmetros.

Fora de escopo: Web Push/PWA (fatia 2), FCM disparado pelo servidor (fatia 3), lembrete com horário por bloco (blocos não têm horário), lembrete de hábito, snapshot/cache offline, alarme exato (`SCHEDULE_EXACT_ALARM`), métricas de engajamento (ficam com a issue de métricas).

## Capabilities

### New Capabilities

- `mobile-lembretes`: preferências, agendamento e conteúdo dos lembretes locais do app Android (resumo do dia e prazo D-1), permissão de notificação, privacidade na tela bloqueada, comportamento sem rede ou sem sessão, e cancelamento no logout.

### Modified Capabilities

(nenhuma. As capabilities `mobile-*` ainda estão só na change `app-mobile-android`, que não foi arquivada. O backend e as specs web não mudam.)

## Impact

- **Código**: `mobile/shared` (regras de horário e conteúdo, orquestração do lembrete, view-model de preferências, filtro de prazo na `RotinaApi`); `mobile/androidApp` (worker, canal e permissão de notificação, intent da notificação, manifest).
- **Dependência nova**: `androidx.work:work-runtime-ktx` no `androidApp`.
- **Depende de** `app-mobile-android` (sessão, `ApiHttp`, navegação, Perfil). Precisa entrar depois dela ou junto.
- **Backend / proxy / Docker**: sem mudança.
- **CI**: `mobile.yml` já roda `jvmTest` + Kover. O limite do Kover sobe para o novo piso medido.
- **Docs**: seção "Lembretes" no `mobile/README.md` e checklist da issue #73.
