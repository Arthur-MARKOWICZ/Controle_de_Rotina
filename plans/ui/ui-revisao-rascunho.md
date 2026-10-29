# Feature — Revisão do rascunho explicável (UI/UX · issue #6)

Escopo: tornar o rascunho do plano revisável de verdade — justificativa por bloco (prazo + prioridade), conflitos acionáveis, diff entre versões, confirmação ao aprovar, adicionar tarefa com busca.  
Fora deste slice: expor score/motivo interno do planner no backend (pode vir depois); replanejamento automático de negócio (issues de negócio); redesign geral da Home já coberto em `ui-home-agenda.md`.

Dependências: `planner.md`, `ui-home-agenda.md`, `ui-estados-feedback.md`. Issue: #6 · pai #1.

---

## Conceito

O diferencial do produto é “sistema propõe → humano revisa → aprova”. A UI do rascunho deve deixar isso explícito sem exigir novos campos de score no MVP: justificativa mínima = **prazo relativo + prioridade** (dados já no bloco/tarefa).

---

## Entities

Nenhuma nova.

---

## Back-end

Sem endpoint novo neste slice.

- Justificativa: derivada no front a partir de prazo/prioridade já retornados.
- Diff entre versões: comparar resposta do plano corrente com a versão anterior **já disponível** via API existente de gerar/listar; se a API atual não devolver a versão N-1 no mesmo payload, o front guarda snapshot da lista de blocos **antes** de “Gerar nova versão” na sessão (decisão MVP — sem endpoint extra).
- Ações de conflito: “aumentar capacidade” navega/abre fluxo de override (API já existente); “remover menor prioridade” usa remover bloco já existente.

---

## Front-end

1. **Motivo no bloco** — linha/tooltip: ex. “Prazo em 2 dias · prioridade alta”.
2. **Banner de conflito** — além da mensagem: ações “Remover tarefa de menor prioridade” (escolhe a de menor prioridade no rascunho e chama remove), “Aumentar capacidade de hoje” (atalho para override/capacidade), “Manter assim” (dismiss).
3. **Diff de versão** — após gerar nova versão, marcar blocos novos/removidos vs snapshot anterior (estilo visual discreto, tokens).
4. **Aprovar** — dialog de confirmação com resumo: N blocos, Xh de Yh capacidade; confirma → `POST` aprovar.
5. **Adicionar tarefa** — lista pesquisável (título + prazo + duração), não `<select>` monólito.

---

## Decisões fechadas (revisão)

1. MVP de “explicável” = prazo + prioridade; score do motor **fora**.
2. Diff via snapshot client-side antes do gerar (sem endpoint novo).
3. Conflito oferece ≥ 1 ação acionável além de dismiss.
4. Aprovar sempre com confirmação + resumo.
5. Sem prefixo novo de API / proxy.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Bloco de rascunho mostra justificativa com prazo/prioridade em PT | F1 | Princípio explicável |
| T2 | Banner de conflito renderiza pelo menos uma ação que dispara callback/navegação | F2 | Conflito acionável |
| T3 | Após “gerar”, UI marca diferença vs snapshot anterior (novo ou removido) | F3 | Diff de versão |
| T4 | Aprovar abre dialog; só chama API após confirmar | F4 | Confirmação |
| T5 | Adicionar tarefa filtra por texto na lista pesquisável | F5 | Usabilidade |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Justificativa no BlocoCard (rascunho)** | Motivo prazo+prioridade; **T1** |
| **F2** | **Banner de conflito acionável** | Ações remove / capacidade / manter; **T2** |
| **F3** | **Diff entre versões** | Snapshot + destaque; **T3** |
| **F4** | **Confirmar aprovação** | Dialog + resumo; **T4** |
| **F5** | **Adicionar tarefa pesquisável** | Combobox/lista filtrável; **T5** |

### Ordem

```text
F1 → F2 → F3 → F4 → F5
```
