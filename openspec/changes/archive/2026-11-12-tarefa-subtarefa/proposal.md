# Proposal

## Why

Tarefas grandes só se quebram em **blocos de tempo anônimos** no planner. Falta subtarefa nomeada, no espírito TickTick, que o usuário cria e que o motor trata como **tarefa normal** (duração, prazo, prioridade, sessão). Sem isso, o pai vira um balde de horas e os passos reais não entram no plano.

## What Changes

- `Tarefa` ganha `parentId` opcional: **um nível** (filho não tem neto). Filho é a mesma entity (título, duração, prazo, prioridade, status, projeto, tags, sessão).
- Com filhos não deletados, `duracaoMinutos` do pai **é a soma dos filhos** (substitui o valor anterior; pai sem trabalho próprio nem bloco).
- Planner e job de atraso: **só folhas** (tarefa sem filhos não deletados). Prazo e prioridade do pai são rótulo de UI; não puxam score nem `ATRASADA`.
- Cascata no pai, sempre com confirmação na UI se houver filhos: finalizar → `FINALIZADA` em pai+filhos (PATCH, **sem XP**); cancelar → `CANCELADA` em todos; excluir → soft delete em todos. Restaurar pai restaura filhos. Restaurar/reabrir filho restaura/reabre o pai; reabrir o pai **não** reabre filhos.
- Último filho `FINALIZADA` → pai `FINALIZADA`. Criar filho num pai `FINALIZADA` reabre o pai.
- Recusar subtarefa se o pai já tiver bloco `CONCLUIDO`, se o alvo já for filho, ou se o pai estiver deletado.
- API: `parentId` opcional no `POST /tarefas` (e no response); sem prefixo HTTP novo.
- UI: criar subtarefa **só no drawer do pai**. Lista sem filtro: card do pai com expandir (default recolhido). Com filtro: filho sobe sozinho com “Subtarefa de {pai}” **somente** se o pai não estiver no resultado.

Fora de escopo: neto; reparentar; drag-and-drop de ordem; MCP/tools novas; herança de tags; checklist (item sem duração).

## Capabilities

### New Capabilities

- `tarefa-subtarefa`: hierarquia de um nível, duração derivada do pai, elegibilidade do planner/atraso/sessão só em folhas, cascatas de status/delete/restore.
- `ui-tarefa-subtarefa`: drawer do pai (criar/listar filhos), árvore na lista com expandir, card solto filtrado com indicador do pai, popups de cascata.

### Modified Capabilities

- `tarefa-sessao`: iniciar / concluir-direto MUST recusar tarefa que tenha filhos não deletados (pai não é unidade de sessão).
- `ui-tarefa-sessao`: Iniciar / Concluir na lista MUST aparecer só em folhas; o card do pai não oferece sessão.

## Impact

- Backend: coluna `parent_id` (Flyway após `V19`); `CriarTarefaRequest` / `TarefaResponse` / PATCH; recálculo de duração do pai; filtro de folhas em `PlannerGeracaoService`, job de atraso e `TarefaSlotAvulsoService`; cascatas em `TarefaService` (status, excluir, restaurar); Problem Details para rejeições (neto, pai com bloco concluído, duração no pai com filhos).
- Frontend: `TarefaDrawer` (seção subtarefas), `TarefaCard` (expandir vs indicador), `TarefasPage` / agrupamento, `useConfirmDialog` nas cascatas; testes de lista/drawer.
- Proxy: sem prefixo novo (`/tarefas` já está no Vite/Nginx).
- MCP: herda `parentId` no JSON da API; sem tool nova neste corte.
