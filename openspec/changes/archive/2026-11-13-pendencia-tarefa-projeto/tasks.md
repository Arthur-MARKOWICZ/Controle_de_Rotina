# Tasks

## 1. Persistência e API de espera

- [x] 1.1 Criar migration Flyway `V21__create_espera.sql` (colunas polimórficas, unique do par, índices origem e alvo); verificar que a migration aplica limpa e o app sobe
- [x] 1.2 Entity `Espera` + DTOs (`alvoTipo`, `alvoId`, resumo com `alvoTitulo` e `resolvida`) + `EsperaService` / repositório no package `pendencia`; verificar compile
- [x] 1.3 `POST/GET/DELETE` em `/tarefas/{id}/esperas` e `/projetos/{id}/esperas`; verificar IT: 201 nas quatro combinações, 404 alvo alheio, 400 auto-espera, 409 duplicata, DELETE 204

## 2. Resolução, ciclo e limpeza

- [x] 2.1 Calcular `resolvida` na leitura (tarefa alvo = `FINALIZADA`; projeto alvo = tem tarefa não deletada e nenhuma aberta); verificar testes: projeto vazio irresolvido, todas `FINALIZADA` resolve, `ATRASADA` não resolve, `CANCELADA` não resolve
- [x] 2.2 Recusar ciclo (direto e transitivo) na criação; verificar IT 400 nos dois grafos
- [x] 2.3 Soft delete da tarefa apaga esperas em que ela é origem ou alvo; restore não recria; arquivar projeto apaga esperas do projeto; verificar IT dos três caminhos

## 3. Planner, ajuste, finalizar e sessão

- [x] 3.1 `estaBloqueada` (espera própria, pai, projeto origem) usado na geração: omitir a tarefa; reserva de projeto bloqueado continua indo ao pool livre; verificar teste/IT de geração nos três casos de herança
- [x] 3.2 `PlanoAjusteService.adicionarBlocoTarefa` recusa tarefa bloqueada; verificar IT 409/400 e que o rascunho não ganha bloco
- [x] 3.3 PATCH `FINALIZADA` recusa tarefa bloqueada; verificar IT e que o status não muda
- [x] 3.4 `TarefaSlotAvulsoService` recusa `iniciar` e `concluir-direto` em tarefa bloqueada (própria, pai, projeto); folha desbloqueada inalterada; verificar IT 409/400 e sessão ok na folha livre

## 4. Enrich nas respostas

- [x] 4.1 `TarefaResponse` e `ProjetoResponse` ganham `esperas` e `bloqueada`; listagens enriquecem em lote (sem N+1); verificar IT de GET tarefa/projeto/lista: título do alvo preenchido, `bloqueada` verdadeiro com espera irresolvida, falso sem espera

## 5. UI drawer e hub

- [x] 5.1 Client (`tarefaApi` / `projetoApi`): create/list/delete de espera e campos novos nos tipos; verificar testes do client
- [x] 5.2 Seção Espera no `TarefaDrawer` (editar: adicionar/remover alvo tarefa ou projeto; criar nova tarefa não exige espera); verificar testes do drawer: cria espera, remove, copy não usa “pendente”
- [x] 5.3 Seção Espera no hub `/projetos/:id`; arquivado sem criar; verificar testes do hub: projeto espera tarefa, hub arquivado só leitura

## 6. UI lista e sessão

- [x] 6.1 Badge no `TarefaCard` (um alvo pelo título; vários = resumo); `bloqueada` esconde Iniciar/Concluir na lista; verificar testes da página/card
- [x] 6.2 Home: bloco de tarefa bloqueada não oferece Iniciar/Concluir e indica a espera; verificar teste do card do bloco

## 7. Integração

- [x] 7.1 Rodar suíte backend relevante (espera + tarefa + planner + ajuste + execução) e suíte frontend (drawer, hub, lista, Home); corrigir regressões desta change

## Workflow follow-up

- Archive a change após review/implementação (`/openspec-archive-change`).
- Fechar a issue [#59](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/59) quando o comportamento estiver em main.
