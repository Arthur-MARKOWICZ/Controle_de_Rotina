# Proposal

## Why

Hoje o usuário vê só a sequência atual de um hábito (`streakAtual`/`melhorStreak`); não há como olhar para trás e saber **em que dias** o hábito foi feito, **quanto tempo** foi dedicado e **se o tempo bateu a duração** do hábito (issue #84). Além disso, o "Concluir direto" de hábito grava início = fim e, por isso, não registra tempo nenhum — hoje credita 0 XP e tornaria o histórico de tempo vazio para o caminho mais usado.

## What Changes

- **Novo endpoint** `GET /habitos/{id}/historico?de=&ate=` (sob o prefixo `/habitos`, sem mudança de proxy) que lista, por dia, os cumprimentos do hábito: data, minutos dedicados, meta do dia, origem do tempo (`TIMER` | `INFORMADO`) e `suficiente`.
- **Meta do dia** = `bloco_plano.duracao_minutos` (cópia da duração no momento da execução); editar a duração do hábito não reescreve dias antigos.
- **Tempo pelo timer** = `concluidoEm − iniciadoEm` bruto, em minutos (sem o arredondamento de 80% do XP).
- **Suficiente** = minutos dedicados ≥ 100% da meta do dia.
- **Concluir direto de hábito aceita minutos**: `POST /habitos/{id}/concluir-direto` passa a aceitar body opcional `{ "minutos": n }`; ausente → duração do bloco. Valor salvo em nova coluna `execucao.minutos_informados`.
- **XP do concluir direto** passa a usar os minutos informados (limitados à duração do bloco pela regra do `XpMotor`), corrigindo o crédito atual de 0 XP. **BREAKING (comportamento)**: concluir direto sem body passa a creditar XP da duração cheia em vez de 0.
- **Sequência inalterada**: dia com tempo insuficiente continua contando para o streak; o histórico só sinaliza.
- **UI**: seção "Histórico" no drawer do hábito (`/habitos/:id`) e diálogo de minutos no "Concluir direto" (pré-preenchido com a duração).

Fora do escopo: concluir direto de **tarefa**; app mobile e MCP (o campo é opcional, então continuam funcionando sem mudança); histórico agregado de todos os hábitos em `/historico`; mudança na regra de streak e o bug de quebra preguiçosa já registrado em `revisao_modulo_habitos.md`.

## Capabilities

### New Capabilities
- `habito-historico`: registro de tempo do cumprimento de hábito (timer ou minutos informados), regra de "suficiente" e consulta do histórico por hábito e período.
- `ui-habito-historico`: exibição do histórico no detalhe do hábito e captura dos minutos no "Concluir direto" de hábito.

### Modified Capabilities
<!-- Nenhuma: hábitos ainda não têm spec principal em openspec/specs/. -->

## Impact

- **Backend**: migração `V23` (`execucao.minutos_informados`), `Execucao`, `ExecucaoService.concluirHabitoDiretoNoBloco`/`finalizarHabitoAposConclusao`, `HabitoExecucaoAvulsaService.concluirDireto`, `HabitoController` (body opcional + novo GET), nova query em `ExecucaoRepository`, novo serviço/DTO de histórico.
- **Frontend**: `habitoApi.ts` (`concluirHabitoDireto` com body, `buscarHistoricoHabito`), `HabitoDoDiaLista.tsx` (diálogo de minutos), `HabitoDrawer.tsx` (seção Histórico).
- **Compatibilidade**: clientes que chamam `concluir-direto` sem body (mobile, MCP) seguem funcionando; o efeito visível é passarem a receber XP da duração cheia.
- **Proxy**: nenhum prefixo novo.
