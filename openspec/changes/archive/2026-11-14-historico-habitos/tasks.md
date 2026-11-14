# Tasks

Regra de testes do projeto: cada unidade com lógica tem ≥ 7 cenários (1 feliz + 3 anomalias + 3 erros), com `Clock` fixo em BRT nos limites. Backend: `cd backend && mvn test`. Front: `cd frontend/frontend-web && npm run test:coverage` (e `npm run build`, que faz typecheck dos testes; mocks com `vi.mocked(...)`).

## 1. Minutos informados e XP no concluir direto (backend)

- [x] 1.1 Criar `V23__execucao_minutos_informados.sql` (`minutos_informados INTEGER NULL`, check 1..1440) e o campo em `Execucao` (getter + `concluirDireto(agora, minutos)` / `concluirDiretoNova(..., minutos)`). Verificar com `mvn test` subindo o contexto (Flyway aplica) e com um IT que insere 0 e 1441 direto na tabela e recebe violação de check
- [x] 1.2 Adicionar `XpMotor.minutosEfetivos(int duracaoBloco, int minutosDecorridos)` e fazer a versão por instantes delegar para ela. Verificar em `XpMotorTest` (≥7): 0, negativo, exatamente 80%, 79%, 100%, acima da duração, duração 0; e que os testes atuais da versão por instantes continuam verdes
- [x] 1.3 Criar `ConcluirHabitoDiretoRequest(Integer minutos)` e `MinutosInformadosInvalidosException` (400, "Minutos informados inválidos", `minutosMaximo`). Aceitar o body opcional em `HabitoController.concluirDireto` e passar os minutos por `HabitoExecucaoAvulsaService.concluirDireto` → `ExecucaoService.concluirHabitoDiretoNoBloco` (validar antes de qualquer escrita; ausente = duração do bloco; ignorado no ramo `EM_ANDAMENTO`). `finalizarHabitoAposConclusao` calcula o XP pelos minutos. Verificar em `HabitoExecucaoAvulsaIT` (≥7): com 20 min (XP de 20 × 1,5), sem body (XP cheio, não mais 0), 60 min (XP com teto de 30), `EM_ANDAMENTO` usa o timer e não grava minutos, 0/−1/1441 → 400 `$.title` + `$.minutosMaximo` sem execução nem XP criados, hábito de outro usuário → 404, segunda conclusão não duplica XP; e no teste unitário do serviço, que o streak sobe com 5 min de 30

## 2. Consulta do histórico (backend)

- [x] 2.1 Extrair a validação de intervalo de `CalendarioAgregador.validarIntervalo` para um validador compartilhado com limite como parâmetro, mantendo o comportamento do calendário. Verificar com os testes existentes do calendário verdes e um teste do validador (≥7: nulos, só um, invertido, igual, no limite, limite + 1, limite diferente)
- [x] 2.2 Adicionar em `ExecucaoRepository` a query de cumprimentos do hábito no intervalo (`plano.data`, `bloco.duracaoMinutos`, `iniciadoEm`, `concluidoEm`, `minutosInformados`, filtrando usuário, hábito e `CONCLUIDO`). Verificar no IT da 2.4 que só uma query é emitida para N dias (sem N+1)
- [x] 2.3 Criar `HabitoHistoricoService` + `HabitoHistoricoDiaResponse` + enum `OrigemTempo` (agrupa por data, soma minutos, meta = maior duração do dia, `suficiente` ≥ 100%, origem `TIMER`/`INFORMADO`/`MISTO`, padrão de 84 dias via `Clock` em BRT, ordem decrescente). Verificar em `HabitoHistoricoServiceTest` (≥7): timer 25/30 → insuficiente, informado 30/30 → suficiente, duas execuções no dia → soma e `MISTO`, duração editada depois mantém a meta antiga, concluir direto antigo → 0 min `TIMER`, lista vazia, padrão de período com `Clock` fixo à meia-noite BRT, intervalo inválido → `IntervaloCapacidadeInvalidoException`, hábito inexistente → `HabitoNaoEncontradoException`
- [x] 2.4 Expor `GET /habitos/{id}/historico?dataDe=&dataAte=` no `HabitoController`. Verificar em `HabitoHistoricoIT` (≥7): 200 com itens ordenados, 200 vazio, `ABORTADO`/`EM_ANDAMENTO` fora, hábito excluído ainda responde 200, outro usuário → 404 `$.title` "Hábito não encontrado", só `dataDe` → 400 "Intervalo inválido", invertido → 400, 367 dias → 400, sem token → 401

## 3. API e diálogo de minutos (front)

- [x] 3.1 Em `habitoApi.ts`: `concluirHabitoDireto(habitoId, data?, minutos?)` com body JSON `{ minutos }` quando informado, e `buscarHistoricoHabito(habitoId, dataDe?, dataAte?)` com tipos `HabitoHistoricoDia`/`OrigemTempo`. Verificar em `habitoApi.test.ts` a matriz 2xx, 400 (mensagem "Minutos informados inválidos"), 401→refresh, 404, 409, 500/timeout, HTML do proxy/JSON inválido, e que o body só vai quando `minutos` vem
- [x] 3.2 Criar `MinutosHabitoDialog` (campo pré-preenchido com a duração, 1..1440, "Informe entre 1 e 1440 minutos.", confirmar/cancelar, `ocupado`). Verificar em `MinutosHabitoDialog.test.tsx` (≥7): confirmar sem alterar, alterar para 20, cancelar não chama `onConfirm`, 0/vazio/1441 mostram erro e não confirmam, Enter confirma, `ocupado` desabilita
- [x] 3.3 Ligar o diálogo nos três pontos de concluir direto de hábito: `HabitoCard`, `HabitoDoDiaLista` e bloco de hábito em `HomePage`. Verificar nos testes de cada componente que o clique abre o diálogo, que confirmar chama `concluirHabitoDireto` com os minutos, que cancelar não chama a API e que um 400 do servidor mostra o toast de erro e mantém o hábito pendente

## 4. Seção Histórico no drawer (front)

- [x] 4.1 Criar `HabitoHistoricoSecao` (carrega os últimos 84 dias, linhas `data · N / M min`, "Meta atingida" / "Faltaram N min" / "Tempo não registrado", estado vazio, erro com "Tentar de novo") e renderizá-la no `HabitoDrawer` só em modo `editar`. Verificar em `HabitoHistoricoSecao.test.tsx` (≥7): lista com suficiente e insuficiente, concluir direto antigo, vazio, 500 com tentar de novo recarregando, 404 com a mensagem do servidor, HTML do proxy; e em `HabitoDrawer.test.tsx` que a seção não aparece nem chama a API em `/habitos/nova` e que um erro do histórico não bloqueia salvar o formulário
- [x] 4.2 Conferir o visual no app rodando (`npm run dev` com o backend local): abrir um hábito com cumprimentos, concluir direto um hábito informando minutos e ver a linha nova no histórico. Verificar em desktop e na largura mobile (375 px) sem estouro de layout

## 5. Cobertura e fechamento

- [x] 5.1 Rodar `cd backend && mvn test` e `cd frontend/frontend-web && npm run test:coverage && npm run build`; subir `jacoco.minimo.*` no `pom.xml` e `test.coverage.thresholds` no `vite.config.ts` para o novo piso medido, arredondado para baixo. Verificar que os dois gates passam com os limites novos
- [x] 5.2 Atualizar `plans/negocio/habitos.md` (nota de que o histórico por hábito existe e o streak continua fora do `/historico` agregado) e a entrada da issue #84 em `ordem_execucao/docs.md`. Verificar com o diff dos dois arquivos

## Workflow follow-up

- Mobile e MCP: avaliar numa issue separada se o app Android e o MCP passam a enviar `minutos` e mostrar o histórico.
- Arquivar esta change depois da revisão (`/openspec-archive-change`).
