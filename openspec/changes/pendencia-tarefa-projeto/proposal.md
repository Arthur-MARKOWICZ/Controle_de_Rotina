# Proposal

## Why

Tarefa e projeto já se ligam por `projetoId`, mas não há **espera**: o usuário não consegue dizer que A só anda quando B terminar. Sem isso, o planner trata trabalho bloqueado como elegível, e a lista oferece sessão em coisa que ainda não pode começar. A issue [#59](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/59) pede essa pendência **entre** tarefas e projetos.

## What Changes

- Nova relação **espera**: origem (tarefa ou projeto) aguarda alvo (tarefa ou projeto) do mesmo usuário. Várias esperas na mesma origem = **todas** precisam resolver (E).
- Quatro combinações no v1: tarefa→tarefa, tarefa→projeto, projeto→tarefa, projeto→projeto. Sem auto-espera, sem ciclo.
- Alvo **tarefa** resolve com `FINALIZADA`. `CANCELADA` e soft delete **não** concluem a espera; soft delete do alvo (ou arquivar o projeto envolvido) **remove** o vínculo.
- Alvo **projeto** resolve quando não há tarefas abertas (`PENDENTE` / `EM_PROGRESSO` / `ATRASADA`) **e** existe ao menos uma tarefa não deletada. Projeto vazio continua bloqueando. Sem status novo `CONCLUIDO` neste corte.
- Origem **tarefa** bloqueada (ou filha de pai bloqueado): fora da geração do plano, fora de adicionar bloco no rascunho, sessão `iniciar` / `concluir-direto` recusada, PATCH `FINALIZADA` recusado.
- Origem **projeto** bloqueada: todas as tarefas não deletadas daquele projeto herdam o bloqueio (planner, sessão, finalizar). Reserva não usada segue a regra atual (volta ao pool livre).
- API aninhada (`/tarefas/{id}/esperas`, `/projetos/{id}/esperas`) — sem prefixo HTTP novo. Respostas de tarefa/projeto enriquecem `esperas` + flag `bloqueada`.
- UI: seção no drawer da tarefa e no hub do projeto; badge na lista; copy **Espera** / **Bloqueada por** (não “pendente”, para não colidir com `StatusTarefa.PENDENTE`).

Fora de escopo: status `CONCLUIDO` em projeto; espera em Decidir/hábito; Grafo tipo Jira (blocks/blocked-by simétrico, lag, datas); OR entre esperas; MCP tools novas; issue #58 (GitHub); listar tarefas inline no hub (#46).

## Capabilities

### New Capabilities

- `pendencia`: criar/listar/remover espera entre tarefa e projeto, resolução, ciclos, efeito no planner/ajuste/finalizar, herança pai→filho e projeto→tarefas.
- `ui-pendencia`: criar e ver esperas no drawer da tarefa e no hub do projeto; badge/indicação na lista; copy Espera; sessão ocultada quando bloqueada.

### Modified Capabilities

- `tarefa-sessao`: `iniciar` e `concluir-direto` MUST recusar tarefa bloqueada (espera própria, pai bloqueado ou projeto origem bloqueado).
- `ui-tarefa-sessao`: CTAs Iniciar/Concluir na lista (e no bloco do Home, se o item ainda estiver no plano) MUST NÃO aparecer em tarefa bloqueada.

## Impact

- Backend: tabela `espera` (Flyway após `V20`); package `pendencia` (ou equivalente) + hooks em `PlannerGeracaoService`, `PlanoAjusteService`, `TarefaSlotAvulsoService`, `TarefaService` (PATCH finalizar), `ProjetoService` (arquivar limpa vínculos); enrich em `TarefaResponse` / `ProjetoResponse`; Problem Details para ciclo, alvo inválido, sessão/finalizar bloqueados.
- Frontend: drawer de tarefa, hub `/projetos/:id`, cards da lista; picker de alvo (tarefa ou projeto); testes de lista/drawer/hub.
- Proxy: sem prefixo novo (`/tarefas` e `/projetos` já existem).
- MCP: JSON da API passa a expor esperas; sem tool nova neste corte.
