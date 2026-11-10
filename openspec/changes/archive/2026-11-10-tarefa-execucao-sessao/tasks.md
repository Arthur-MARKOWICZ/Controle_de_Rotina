# Tasks

## 1. Domínio e status TAREFA_AVULSA

- [x] 1.1 Adicionar `TAREFA_AVULSA` em `StatusPlano` e garantir que corrente do planner / calendário lente Plano / geração ignoram o status; verificar com teste de enum/consulta existente adaptado ou novo teste unitário
- [x] 1.2 Estender `ExecucaoService.garantirPlanoExecutavel` para aceitar plano `TAREFA_AVULSA` somente com `tarefaId`; verificar com teste de domínio (espelho do hábito avulso)
- [x] 1.3 Implementar `concluirTarefaDiretoNoBloco` (ou equivalente) creditando XP com `bloco.duracaoMinutos` e atualizando progresso/finalização da tarefa; verificar com teste de domínio/IT de conclusão direta

## 2. Slot e API de sessão de tarefa

- [x] 2.1 Implementar `TarefaSlotAvulsoService.materializar` (plano do dia + bloco com duração; reuso de bloco avulso não concluído) e testes unitários de materialização/restante
- [x] 2.2 Implementar `TarefaExecucaoAvulsaService` (preferir bloco aberto do `APROVADO`; senão avulso; validar `1..restante`) e testes de domínio para iniciar / concluir-direto / rejeições
- [x] 2.3 Expor `POST /tarefas/{id}/iniciar` e `POST /tarefas/{id}/concluir-direto` (body: `duracaoMinutos?`, `data?`, `blocoId?`) e ITs HTTP cobrindo avulso, reuso do aprovado, 400/409
- [x] 2.4 Expor `restanteMinutos` (ou `minutosConcluidos`) em `TarefaResponse` e verificar serialização nos testes de API de tarefa existentes/novos

## 3. Cliente e CTAs

- [x] 3.1 Adicionar funções em `tarefaApi` (iniciar / concluir-direto) + tipos; verificar com testes de API client
- [x] 3.2 No Home, adicionar botão Concluir em blocos de tarefa (paridade com hábito) chamando concluir-direto; verificar em `HomePage` / `BlocoCard` tests
- [x] 3.3 Criar dialog/picker de duração (presets 15/25/30/45/60, Restante, custom, teto=restante) e verificar testes de componente
- [x] 3.4 Em `TarefaCard` / lista, CTAs Iniciar e Concluir com picker quando avulso e sem picker quando reusa aprovado; verificar testes de página/lista

## 4. Barra global de sessão

- [x] 4.1 Extrair/elevar estado de execução em andamento + `BlocoAtivo` para o `AppShell` (ou provider compartilhado), removendo duplicata só do Home; verificar que a barra aparece após iniciar e persiste ao navegar (`AppShell` / integração)
- [x] 4.2 Concluir e Abortar na barra global atualizam lista/home/tarefas conforme já fazem no Home; verificar testes de interação e toasts de XP

## 5. Integração

- [x] 5.1 Rodar suíte relevante backend (execucao/tarefa ITs) e frontend (Home, Tarefas, AppShell) e corrigir regressões óbvias introduzidas por esta change

## Workflow follow-up

- Archive a change após review/implementação (`/openspec-archive-change`).
- Tools MCP para iniciar/concluir-direto de tarefa ficam de fora salvo pedido explícito.
