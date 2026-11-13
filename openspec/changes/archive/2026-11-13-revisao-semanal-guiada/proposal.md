# Proposal

## Why

O produto já tem as peças de um ritual de planejamento (resumo semanal no Histórico, tarefas atrasadas, fila Decidir, esperas, proposta multi-dia), mas cada uma em sua tela. Com o planner sob demanda em `/plano`, o risco é o app virar só uma lista: o usuário nunca chega ao diferencial (gerar e aprovar o plano). A issue [#72](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/72) (OPP-012) pede um momento semanal guiado que leve até lá.

## What Changes

- Nova rota web **`/revisao`** ("Revisão da semana") com um fluxo de **4 passos**, em ordem, todos **puláveis**:
  1. **Sua semana** — resumo da semana corrente (BRT, segunda a domingo) a partir de `GET /historico/semana`: tarefas finalizadas, blocos concluídos, minutos trabalhados, XP.
  2. **Atrasadas e em espera** — tarefas `ATRASADA` e tarefas bloqueadas por espera, com ações rápidas por item: **reagendar** (novo prazo) e **descartar** (status `CANCELADA`) e, para bloqueadas, **remover a espera**.
  3. **Esvaziar o Decidir** — itens abertos da fila com **promover** e **descartar**, reaproveitando o fluxo existente de `/decidir`.
  4. **Plano da próxima semana** — gera a proposta a partir da próxima segunda (`POST /planos/gerar`), mostra os dias e permite **aprovar** (`/planos/propostas/{id}/aceitar`) ou **não aprovar agora** (`/descartar`) de forma explícita.
- Tela final de **conclusão** com o desfecho: plano aprovado, plano não aprovado (escolha explícita) ou passo 4 pulado.
- Entrada para `/revisao` no menu **Mais** e um atalho a partir de `/plano`.
- Backend: a listagem `GET /tarefas` ganha o filtro opcional **`bloqueada=true`**, porque hoje só dá para saber quais tarefas estão em espera lendo cada resposta, e a listagem é paginada. É a única agregação que falta; o resto reaproveita endpoints existentes.

Fora de escopo: lembrete/agendamento do ritual (depende da issue de lembretes); persistir "revisão concluída" no servidor e medir a métrica de sucesso (fica para quando houver telemetria); cartão de calibração dentro da revisão (já existe em `/historico`); app mobile e MCP; "mover" da issue — não existe rebaixar tarefa para o Decidir, e o `PATCH` de tarefa não consegue limpar o prazo; mover de projeto continua na ficha da tarefa.

## Capabilities

### New Capabilities

- `ui-revisao-semanal`: fluxo guiado em `/revisao` — navegação entre passos, pular, conteúdo e ações de cada um dos 4 passos, desfecho explícito do plano e tela de conclusão.

### Modified Capabilities

- `pendencia`: a listagem de tarefas passa a aceitar filtro por tarefas bloqueadas (espera irresolvida própria, pai bloqueado ou projeto origem bloqueado).

## Impact

- Backend: `TarefaController`/`TarefaFiltro`/`TarefaService` (parâmetro `bloqueada`), usando `EsperaService.idsTarefasBloqueadas`. Sem migração Flyway; sem prefixo novo de controller, logo sem mudança de proxy.
- Frontend: nova pasta `src/revisao/` (página + um componente por passo), rota em `App.tsx`, item em `MenuMais.tsx`, atalho em `HomePage.tsx`; reaproveita `historicoApi`, `tarefaApi`, `decidirApi`, `planoApi` e a API de esperas; `tarefaApi.listarTarefas` ganha o parâmetro `bloqueada`.
- Testes: regra 1 + 3 + 3 do AGENTS.md no filtro novo (unitário + IT) e em cada passo e na página (testes de componente para os 4 passos, critério da issue); gate de cobertura não pode cair.
- Risco de produto (issue): virar burocracia se for longo — por isso todo passo é pulável e o alvo é concluir em ~5 minutos.
