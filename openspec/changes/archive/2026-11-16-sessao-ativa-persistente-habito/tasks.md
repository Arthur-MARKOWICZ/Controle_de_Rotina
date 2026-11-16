# Tasks

Regra de testes do projeto: cada unidade com lógica tem ≥ 7 cenários (1 feliz + 3 anomalias + 3 erros), com `Clock` fixo em BRT nos limites. Backend: `cd backend && mvn test`. Front: `cd frontend/frontend-web && npm run test:coverage` (e `npm run build`, que faz typecheck dos testes; mocks com `vi.mocked(...)`). Proxy: `/execucoes` já está em `vite.config.ts`, `nginx.conf` e `API_PROXY.md` — nada a mudar.

## 1. Consulta da sessão ativa (backend)

- [x] 1.1 Extrair de `ExecucaoService.garantirPlanoExecutavel` um predicado `planoExecutavel(bloco, plano)` (sem exceção) e fazer `garantirPlanoExecutavel` usá-lo. Verificar com `ExecucaoIniciarIT`, `ExecucaoConcluirIT` e `ExecucaoAbortarIT` verdes sem alteração
- [x] 1.2 Adicionar em `ExecucaoRepository` a busca de execuções do usuário por status ordenadas por `iniciadoEm desc` (ex.: `findByUsuarioIdAndStatusOrderByIniciadoEmDesc`). Verificar pelo IT da 1.4 (ordem e isolamento por usuário)
- [x] 1.3 Criar `ExecucaoService.buscarAtiva(usuarioId): Optional<ItemExecucaoResponse>`: percorre as `EM_ANDAMENTO` em ordem, carrega bloco + plano, devolve a primeira com `planoExecutavel`, mapeada por `mapearItem` (mapas de um elemento; esperas via `esperaService.enriquecerTarefas`). Verificar no IT da 1.4
- [x] 1.4 Expor `GET /execucoes/ativa` no `ExecucaoController` (`200` com item ou `204`). Verificar em `ExecucaoSessaoAtivaIT` (≥7): hábito avulso → 200 com `habitoResumo` e `tarefaResumo` nulo; bloco do plano aprovado → 200 com `tarefaResumo`; tarefa avulsa → 200; sem sessão (só `CONCLUIDO`/`ABORTADO`) → 204; duas abertas → a de `iniciadoEm` mais recente e a outra continua `EM_ANDAMENTO`; iniciada 23:50 BRT de ontem consultada 00:10 BRT (`Clock` fixo) → 200; plano aprovado substituído por rascunho v2 → 204 e execução intacta no banco; substituída + avulsa → devolve a avulsa; sessão só de outro usuário → 204; sem token → 401

## 2. Iniciar hábito retoma sessão em andamento (backend)

- [x] 2.1 Em `HabitoExecucaoAvulsaService.iniciar`, após escolher o bloco (aprovado aberto ou avulso materializado), se a execução do bloco estiver `EM_ANDAMENTO`, devolver `new ResultadoInicio(execucao, false)` sem chamar `ExecucaoService.iniciar`. Verificar em `HabitoExecucaoAvulsaIT` (≥7 novos/ajustados): reiniciar avulso em andamento → 200, mesmo `id` e `iniciadoEm` original; nenhuma nova linha em `plano`/`bloco_plano`/`execucao`; bloco do plano aprovado em andamento → 200 com essa execução; avulso `ABORTADO` → 200 reiniciado com novo `iniciadoEm` (comportamento atual preservado); avulso `CONCLUIDO` → 409 `$.title` de execução já concluída; hábito de outro usuário → 404 "Hábito não encontrado"; hábito pausado → erro de não elegível com `$.title`; e em `ExecucaoIniciarIT`, `POST /execucoes/iniciar` em bloco `EM_ANDAMENTO` continua 409

## 3. Cliente da API (front)

- [x] 3.1 Em `execucao/api/execucaoApi.ts`, criar `buscarSessaoAtiva(): Promise<ItemExecucaoResponse | null>` (`204 → null`, `!ok → lerErroApi` com fallback "Não foi possível carregar a sessão ativa.", senão `readApiJson`). Verificar em `execucaoApi.test.ts` a matriz: 200 com item, 204 → `null`, 400, 401→refresh e repete, 404, 409, 500/timeout, HTML do proxy/JSON inválido

## 4. Sessão ativa restaurada no front

- [x] 4.1 Em `SessaoAtivaContext`, `refrescarDoDia` passa a usar `buscarSessaoAtiva()`: item → `itemParaSessao`, `null` → `setSessao(null)`, erro → mantém o estado atual. Remover o remendo `atual?.habitoId`, `listarExecucoes` e `escolherMaisRecente` do contexto. Verificar em `SessaoAtivaContext.test.tsx` (≥7): montar com sessão avulsa de hábito no servidor mostra a barra com título e tempo desde `iniciadoEm` (simula F5); montar com tarefa avulsa mostra a barra; `204` não mostra barra; `registrarInicio` seguido de `refrescarDoDia` com tarefa avulsa mantém a barra; sessão encerrada no servidor some no próximo refresh; falha 500 mantém a sessão já exibida; HTML do proxy no mount não mostra barra nem toast; concluir/abortar pela barra continuam funcionando (testes atuais ajustados para o novo mock)
- [x] 4.2 Em `HabitoCard`, remover o estado local `execucaoAtivaId` e derivar `execucaoId` só de `sessao?.habitoId === habito.id`. Verificar em `HabitoCard.test.tsx` (≥7): com sessão do próprio hábito no contexto mostra Finalizar/Pausar e não Iniciar (cenário F5); sessão de outro hábito mostra Iniciar/Concluir; sem sessão mostra Iniciar/Concluir; Iniciar com resposta 200 de execução existente registra a sessão e não mostra toast de erro; concluir pela barra (contexto limpo) volta o card para Iniciar; Iniciar com 409 de já concluída mostra a mensagem da API e o card fica em Iniciar; Iniciar com 404 mostra a mensagem e não registra sessão
- [x] 4.3 Conferir no app rodando (`npm run dev` + backend local): iniciar um hábito em `/habitos`, dar F5 e ver Finalizar no card e a barra com o tempo certo; clicar Iniciar numa segunda aba desatualizada e ver a sessão retomada sem erro; repetir o F5 com uma tarefa avulsa iniciada em `/tarefas`. Verificar também na largura mobile (375 px)

## 5. Cobertura e fechamento

- [x] 5.1 Rodar `cd backend && mvn test` e `cd frontend/frontend-web && npm run test:coverage && npm run build`; subir `jacoco.minimo.*` no `pom.xml` e `test.coverage.thresholds` no `vite.config.ts` para o novo piso medido, arredondado para baixo (nunca descer). Verificar que os dois gates passam com os limites novos
- [x] 5.2 Registrar `GET /execucoes/ativa` e a retomada no iniciar de hábito em `plans/negocio/habito-sem-plano.md` (fluxo de hábito avulso). Verificar com o diff do arquivo

## 6. Ajustes da verificação

- [x] 6.1 Em `SessaoAtivaContext`, ignorar respostas de `buscarSessaoAtiva` que chegam depois de uma resposta ou mudança local mais nova (contador de requisição invalidado por `registrarInicio`, `definirSessao`, `limpar`, concluir e abortar). Verificar em `SessaoAtivaContext.test.tsx`: consulta lenta iniciada antes de `limpar` não traz a barra de volta; consulta lenta antiga não sobrescreve a resposta de uma consulta mais nova; consulta lenta não desfaz um `registrarInicio` feito depois dela
- [x] 6.2 Em `ExecucaoService.buscarAtiva`, carregar blocos e planos das execuções `EM_ANDAMENTO` em lote (uma consulta por tabela), mantendo a ordem por `iniciadoEm` e o filtro de plano executável. Verificar com `ExecucaoSessaoAtivaIT` verde sem alteração de comportamento
- [x] 6.3 Cobrir o cenário "Falha ao consultar a sessão ativa" na montagem com 500 e timeout. Verificar em `HabitoCard.test.tsx`: montar com rejeição 500 e com falha de rede mostra Iniciar/Concluir, sem barra e sem toast

## Workflow follow-up

- Mobile: avaliar numa issue separada se o `SessaoAtivaStore` do Android passa a usar `GET /execucoes/ativa` (hoje funciona via `/execucoes` + `/habitos/do-dia`, mas é limitado ao dia).
- Arquivar esta change depois da revisão (`/openspec-archive-change`).
