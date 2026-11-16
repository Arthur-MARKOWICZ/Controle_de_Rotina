# Proposal

## Why

Ao iniciar um hábito pela tela de Hábitos e recarregar a página (F5), o card volta a mostrar **Iniciar** como se nada tivesse começado, a barra global de sessão some e, ao clicar em Iniciar de novo, a API responde 409 (`ExecucaoJaEmAndamentoException`). A execução **está** salva no banco (`EM_ANDAMENTO` num plano `HABITO_AVULSO`); o problema é que o front só conhece a sessão pela memória (`useState` no `HabitoCard` e no `SessaoAtivaContext`) e a única consulta usada para reconstruí-la, `GET /execucoes?data=`, lista apenas o plano `APROVADO` do dia. O usuário fica travado: não vê o timer, não consegue concluir pela barra e não consegue reiniciar.

## What Changes

- **Novo endpoint `GET /execucoes/ativa`**: devolve a execução `EM_ANDAMENTO` mais recente do usuário, em qualquer tipo de plano (`APROVADO`, `HABITO_AVULSO`, `TAREFA_AVULSA`) e sem filtro de data, no mesmo formato de item de `GET /execucoes` (`bloco`, `tarefaResumo`, `habitoResumo`, `execucao`); `204` quando não há sessão.
- **`POST /habitos/{id}/iniciar` idempotente quando já em andamento**: se o bloco escolhido (plano aprovado aberto ou avulso) já tem execução `EM_ANDAMENTO`, responde `200` com essa execução em vez de `409`. `POST /execucoes/iniciar` mantém o `409` atual.
- **`SessaoAtivaContext` reconstrói a sessão a partir de `GET /execucoes/ativa`** ao montar e em cada `refrescarDoDia`; sai o remendo que preservava `atual?.habitoId` na memória.
- **`HabitoCard` reflete a sessão restaurada**: após F5, um hábito em andamento mostra Finalizar/Pausar (não Iniciar), e o timer da barra usa o `iniciadoEm` do servidor.
- Efeito colateral positivo: sessões avulsas de **tarefa** (`TAREFA_AVULSA`) também passam a sobreviver ao F5 na barra global, pelo mesmo endpoint.

Fora de escopo: app Android (já reconstrói a sessão via `GET /execucoes` + `GET /habitos/do-dia`); mudar a regra de quantas sessões simultâneas são permitidas.

## Capabilities

### New Capabilities
- `sessao-ativa`: consulta da sessão em andamento do usuário independente de dia e tipo de plano, e retomada (em vez de erro) ao iniciar um hábito que já está em andamento.
- `ui-habito-sessao`: estado de sessão do card de hábito na lista `/habitos`, incluindo restauração após recarregar a página.

### Modified Capabilities
- `ui-tarefa-sessao`: o requisito "Barra global de sessão ativa" passa a exigir que a barra seja restaurada após recarregar a página, para sessões de plano aprovado e avulsas (hábito e tarefa), e deixa de depender do dia corrente.

## Impact

- **Backend**: `ExecucaoController` / `ExecucaoService` (novo método de consulta reaproveitando `mapearItem`), `ExecucaoRepository` (consulta de execução `EM_ANDAMENTO` por usuário), `HabitoExecucaoAvulsaService.iniciar` (retomada). Sem migração de banco.
- **Frontend**: `execucao/api/execucaoApi.ts` (`buscarSessaoAtiva`), `execucao/SessaoAtivaContext.tsx`, `habito/HabitoCard.tsx`; testes correspondentes.
- **Proxy**: nenhuma alteração — `/execucoes` já está em `vite.config.ts`, `nginx.conf` e `API_PROXY.md`.
- **API pública**: aditiva (novo endpoint); `POST /habitos/{id}/iniciar` troca `409` por `200` no caso "já em andamento" — no app Android, `HabitosViewModel.iniciar` passa a receber a execução existente e registrá-la como sessão, que é o comportamento desejado.
