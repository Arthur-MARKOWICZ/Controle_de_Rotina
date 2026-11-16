# Design

## Context

Motivação em `proposal.md` (Why). Estado atual relevante:

- Iniciar hábito fora do plano cria `Plano HABITO_AVULSO` → `BlocoPlano` → `Execucao EM_ANDAMENTO` (`HabitoSlotAvulsoService` + `ExecucaoService.iniciar`). Tarefa avulsa faz o mesmo com `TAREFA_AVULSA`.
- `ExecucaoService.listar` (`GET /execucoes?data=`) só devolve blocos do plano corrente `APROVADO` do dia — nunca planos avulsos.
- `SessaoAtivaContext.refrescarDoDia` usa só `GET /execucoes` e, para não perder a sessão avulsa de hábito, preserva `atual?.habitoId` em memória. Após F5 a memória está vazia → `sessao = null`. Sessão avulsa de **tarefa** nem esse remendo tem: `TarefaCard` chama `registrarInicio` e logo depois `refrescarDoDia`, que zera a barra.
- `HabitoCard` deriva `emAndamento` de `execucaoAtivaId` (estado local) ou `sessao.habitoId`; `HabitoResponse` não traz dados de execução.
- `ExecucaoService.garantirPlanoExecutavel` define quando concluir/abortar é aceito: plano avulso, ou plano `APROVADO` que ainda é o corrente (maior versão `RASCUNHO`/`APROVADO`) do seu dia.
- Já existe índice `idx_execucao_usuario_status (usuario_id, status)`.

## Goals / Non-Goals

**Goals:**
- Uma única fonte de verdade para "qual sessão está aberta": o servidor.
- O front nunca precisa guardar sessão fora da memória (sem `localStorage`).
- `POST /habitos/{id}/iniciar` deixa de ser um beco sem saída quando o front está desatualizado.

**Non-Goals:**
- Impedir várias sessões simultâneas no backend (regra continua como está).
- Encerrar automaticamente execuções órfãs em plano substituído.
- Alterar o app Android.

## Decisions

### D1. `GET /execucoes/ativa` em vez de ampliar `GET /execucoes`
`GET /execucoes?data=` é contrato do Home (lista do plano aprovado do dia, com `plano` no topo). Misturar blocos avulsos ali mudaria a Home e o mobile. Um endpoint dedicado, sem `data`, também resolve a sessão que atravessa a meia-noite.
*Alternativas:* (a) incluir avulsos em `/execucoes` — quebra semântica da lista do plano; (b) a tela de Hábitos consultar `/habitos/do-dia` — conserta o card mas não a barra global nem tarefa avulsa; (c) `localStorage` — duplica estado e fica errado quando a sessão é encerrada no Android ou em outra aba.

### D2. Resposta reaproveita `ItemExecucaoResponse`; `204` quando vazio
O front já converte esse formato em sessão (`itemParaSessao`, `tituloDoItemExecucao`). No backend, reaproveitar `ExecucaoService.mapearItem` com mapas de um elemento (tarefa/hábito, nome do projeto, esperas via `EsperaService.enriquecerTarefas`). `204` em vez de `200 null` porque é o padrão já usado (logout) e deixa o "não há sessão" explícito.
Front: `buscarSessaoAtiva(): Promise<ItemExecucaoResponse | null>` em `execucaoApi.ts` — `204 → null`, `!ok → lerErroApi`, demais → `readApiJson`.

### D3. Elegibilidade = mesma regra de `garantirPlanoExecutavel`
Consulta no repositório: todas as execuções `EM_ANDAMENTO` do usuário ordenadas por `iniciadoEm desc` (usa o índice existente; volume esperado ≤ poucas linhas). Em Java, percorrer e devolver a primeira cujo bloco/plano passe na regra de executável. Extrair de `garantirPlanoExecutavel` um predicado `planoExecutavel(bloco, plano)` (sem lançar exceção) usado pelos dois caminhos, para não divergir.
*Por quê:* sem esse filtro, uma execução órfã num plano aprovado substituído apareceria na barra e Concluir/Abortar responderiam erro — outro beco sem saída.

### D4. Retomada só no endpoint de hábito
Em `HabitoExecucaoAvulsaService.iniciar`, depois de escolher o bloco (aprovado aberto ou avulso materializado), se a execução do bloco estiver `EM_ANDAMENTO`, devolver `new ResultadoInicio(execucao, false)` → controller já mapeia para `200`. `ExecucaoService.iniciar` (genérico, usado por `/execucoes/iniciar`) continua lançando `ExecucaoJaEmAndamentoException`.
*Alternativa:* tornar `ExecucaoService.iniciar` idempotente para todos — muda o contrato de `/execucoes/iniciar` e testes existentes de 409 sem necessidade.

### D5. Front: contexto lê do servidor; card deriva só do contexto
- `SessaoAtivaContext.refrescarDoDia` passa a chamar `buscarSessaoAtiva()`: item → `itemParaSessao(item)`; `null` → `setSessao(null)`; erro → mantém o estado atual (não derruba a barra por falha transitória). Remover o remendo `atual?.habitoId` e o uso de `listarExecucoes`/`escolherMaisRecente` no contexto (a escolha da mais recente passa a ser do backend).
- `HabitoCard` remove o estado local `execucaoAtivaId`: `execucaoId = sessao?.habitoId === habito.id ? sessao.execucaoId : null`. `registrarInicio` já põe a sessão com `habitoId` no contexto; o estado local só criava divergência (ex.: concluir pela barra deixava o card em Finalizar).
- Como `POST /habitos/{id}/iniciar` agora devolve `200` com a execução existente, o fluxo de Iniciar do card não precisa de tratamento especial.

## Risks / Trade-offs

- [Uma chamada extra no carregamento da app] → `GET /execucoes/ativa` é leve (índice + no máximo um item mapeado) e substitui a chamada `GET /execucoes` que o contexto já fazia.
- [Front antigo em cache recebe 200 em vez de 409 ao reiniciar hábito] → o front antigo trata 200 como sucesso e registra a sessão; comportamento melhor que o atual.
- [Execuções órfãs em plano substituído continuam `EM_ANDAMENTO` no banco] → ficam invisíveis (D3), como já são hoje no Home; limpeza fica fora do escopo.
- [Mais de uma sessão aberta: a barra mostra só a mais recente] → igual ao comportamento atual (`escolherMaisRecente`); as demais continuam acessíveis pelo Home quando estão no plano aprovado.

## Migration Plan

Sem migração de banco. Deploy do backend antes do frontend (o front novo depende de `/execucoes/ativa`; o backend novo é compatível com o front antigo). Rollback: reverter o front primeiro; o endpoint novo pode ficar sem uso. Proxy já cobre `/execucoes`.
