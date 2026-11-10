# Design

## Context

See proposal.md — Why. Hoje a execução de tarefa só existe via bloco do plano `APROVADO` (`POST /execucoes/iniciar` → concluir/abortar). Hábitos já têm slot `HABITO_AVULSO`, `POST /habitos/{id}/iniciar` e `concluir-direto`, e CTAs duplos no Home. O painel `BlocoAtivo` vive só em `HomePage`. A unique `(plano_id, tarefa_id)` em `bloco_plano` já foi removida (V10), então várias sessões da mesma tarefa no mesmo plano são permitidas — necessário para `TAREFA_AVULSA` com várias sessões/dia.

## Goals / Non-Goals

**Goals:**

- Reusar o modelo `Execucao` + `BlocoPlano` (como hábito avulso), sem entity paralela.
- Paridade de CTAs tarefa/hábito no Home; sessão a partir de `/tarefas` com duração escolhida.
- Barra de sessão ativa disponível em qualquer rota autenticada.
- Concluir direto de tarefa credita XP/tempo efetivo = duração do bloco (não intervalo zero).

**Non-Goals:**

- Pause/resume; mudar a fração 80% do `XpMotor` no caminho com timer.
- Refatorar histórico/calendário para listar `TAREFA_AVULSA` (pode ignorar como já ignora hábito avulso na lente Plano).
- Tools MCP novas (pode ser follow-up).
- Obrigar correção do XP zero de concluir-direto de hábito — alinhar só se o helper compartilhado tornar trivial.

## Decisions

### 1. Status `TAREFA_AVULSA` espelhando `HABITO_AVULSO`

- Novo valor em `StatusPlano`.
- Fora de `STATUS_CORRENTE` (Home/geração/aprovação).
- `ExecucaoService.garantirPlanoExecutavel` aceita `TAREFA_AVULSA` somente se o bloco tem `tarefaId` (simétrico ao hábito).
- Alternativa rejeitada: reusar `HABITO_AVULSO` com tarefas — mistura origens e invariantes.

### 2. Serviços espelho do hábito

- `TarefaSlotAvulsoService.materializar(usuarioId, tarefaId, data, duracaoMinutos)` → plano do dia + **novo** bloco com a duração (ou reusa bloco avulso não `CONCLUIDO` da tarefa, atualizando duração se ainda não concluído).
- `TarefaExecucaoAvulsaService.iniciar` / `concluirDireto`: primeiro tenta bloco aberto no `APROVADO` corrente do dia; senão materializa avulso.
- Controllers em `TarefaController` (prefixo `/tarefas` já no proxy):  
  - `POST /tarefas/{id}/iniciar` body `{ duracaoMinutos?, data? }`  
  - `POST /tarefas/{id}/concluir-direto` body `{ duracaoMinutos?, data? }`  
  - `duracaoMinutos` obrigatório quando for caminho avulso; ignorado/ausente quando reusa aprovado.

### 3. Restante e validação

```
restante = tarefa.duracaoMinutos - soma(duracaoMinutos dos blocos CONCLUIDO da tarefa)
duracaoSessao valida <=> 1 <= duracaoMinutos <= restante
```

- Cálculo no service (mesma base do progresso atual: soma por status `CONCLUIDO`).
- 400 se inválido; 409 se sem restante / finalizada / conflito de execução.

### 4. Concluir direto no bloco de tarefa

- Novo método em `ExecucaoService` (análogo a `concluirHabitoDiretoNoBloco`): cria/atualiza via `concluirDireto` / `concluirDiretoNova`.
- **XP / minutos efetivos:** passar `bloco.getDuracaoMinutos()` diretamente à concessão (não `XpMotor.minutosEfetivos` sobre timestamps iguais).
- Progresso/finalização: reutilizar `atualizarTarefaAposConcluir`.

### 5. Home Concluir de tarefa

- UI chama o mesmo concluir-direto por `tarefaId` **ou** um `POST /execucoes/{bloco}` dedicado. Preferência: **por tarefaId com data do plano** (reusa path da lista e a regra “preferir bloco aberto do aprovado”), evitando endpoint só de bloco.
- Alternativa: `POST /execucoes/concluir-direto` com `blocoId` — mais simétrico ao fluxo atual de concluir por execução; aceitável se a lista já resolveu o bloco. **Decisão:** endpoint por `tarefaId` + resolução de bloco no service (aprovado aberto → senão avulso), e no Home o Concluir do card chama o mesmo endpoint (bloco do card é o aberto do aprovado).

### 6. Barra global = elevar `BlocoAtivo`

- Estado de “execução em andamento do dia” sobe para contexto/hook no `AppShell` (poll ou reuse de `listarExecucoes` + slots avulsos se necessário).
- Home deixa de renderizar `BlocoAtivo` duplicado; usa a barra global.
- Se houver vários `EM_ANDAMENTO`, exibir o mais recentemente `iniciadoEm` (documentar; MVP simples).
- Alternativa rejeitada: redirecionar para Home após iniciar — quebra o fluxo da lista.

### 7. Picker na UI

- Dialog/sheet em `/tarefas`: presets `[15,25,30,45,60]` filtrados por `restante`, + Restante + input.
- Home: sem picker.
- Restante no card: derivado de `tarefa.duracaoMinutos` e, se a API de lista não trouxer minutos concluídos, calcular via campo novo opcional no `TarefaResponse` **ou** endpoint leve. **Decisão:** adicionar `minutosConcluidos` (ou `restanteMinutos`) na resposta de tarefa para a lista não chutar.

## Risks / Trade-offs

- [Vários EM_ANDAMENTO] → Barra mostra só um; mitiga escolhendo o mais recente; Abortar/Concluir age nele.
- [Sessão avulsa vs bloco do plano no mesmo dia] → Duas execuções possíveis (como hábito); progresso soma ambos; documentar na UX com toast claro.
- [Calendário/histórico ignoram avulso] → Sessões só na lista/Home podem “sumir” de agregados até slice futuro; aceitável no MVP.
- [Concluir direto Home via tarefaId] → Se houver dois blocos abertos da mesma tarefa (raro), precisa desambiguar: preferir o bloco do card (`blocoId` opcional no body). **Incluir `blocoId` opcional** no body de iniciar/concluir-direto quando a UI já conhece o bloco.

## Migration Plan

1. Enum + migrate status (só valor novo; sem backfill).
2. APIs + testes de domínio/IT.
3. Front: API client → picker → CTAs lista → Home Concluir → AppShell barra.
4. Rollback: feature só adiciona paths/status; reverter deploy remove CTAs; planos `TAREFA_AVULSA` órfãos são inofensivos à corrente.

## Open Questions

- Expor `restanteMinutos` no `TarefaResponse` vs calcular só no client com novo campo `minutosConcluidos` — implementar o que for menor diff no mapper existente.
- Incluir ou não tools MCP nesta change (default: não).
