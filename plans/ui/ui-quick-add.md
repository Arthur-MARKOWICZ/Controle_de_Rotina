# Feature — Criação rápida de tarefa (UI/UX · issue #7)

Escopo: criar tarefa sem sair do contexto (modal/drawer ou barra na Home/lista); atalhos de prazo; labels PT; validação inline; toast de sucesso; descrição opcional no backend.  
Fora deste slice: parsing de linguagem natural; formulário completo de edição (`TarefaFormPage` permanece para edição); lista agrupada (`ui-lista-tarefas.md`).

Dependências: `tarefa.md`, `ui-estados-feedback.md`, `ui-design-system.md`. Issue: #7 · pai #1.

---

## Entities

Sem entity nova. Ajuste de validação em `Tarefa`:

| Campo | Mudança |
|-------|---------|
| descricao | Passa a aceitar ausente/blank no create; persistir `""` (ou null se a coluna já permitir — preferir `""` para não mudar nullability sem migration se hoje é NOT NULL) |

---

## Back-end

1. **POST /tarefas** — `descricao` deixa de ser `@NotBlank`; se null/blank → gravar string vazia; título, prioridade, prazo, duração continuam obrigatórios neste slice (prazo ainda obrigatório; “sem prazo” é issue de negócio #27, fora).
2. Demais endpoints inalterados. Sem prefixo novo.

---

## Front-end

1. **Quick add** — acionável da Home e/ou lista sem navegar para `TarefaFormPage`; campos mínimos: título, duração, prioridade, prazo.
2. **Atalhos de prazo** — chips Hoje / Amanhã / Em 3 dias / Próxima segunda (calculados em BRT coerente com o app).
3. **Descrição** — opcional, recolhida (“Adicionar descrição”).
4. **Labels PT** — prioridade (e status se aparecer) nunca como enum cru.
5. **Validação inline** — erro por campo, não só mensagem única no rodapé.
6. **Toast** — “Tarefa criada” com ação/link “Ver” (detalhe/edição).
7. Formulário completo de criação em página dedicada pode redirecionar para o quick add ou permanecer; não duplicar regras de validação (extrair helper).

---

## Decisões fechadas (revisão)

1. Descrição opcional neste slice (B1).
2. Prazo continua obrigatório (sem prazo = fora; issue #27).
3. Sem NLP.
4. Toast via `ui-estados-feedback.md`.
5. Duração continua em horas conforme API atual (mudança para minutos = issue de negócio #24, fora).

---

## Testes automatizados

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `POST /tarefas` sem descrição (omitida ou `""`) → 201 | B1 | Quick add mínimo |
| T2 | `POST` sem título / sem prazo → 400 | B1 | Validação restante |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T3 | Chip “Amanhã” preenche prazo esperado | F1 | Atalho |
| T4 | Submit válido chama API e dispara toast de sucesso | F2 | Fluxo |
| T5 | Erro de campo exibido inline (ex.: título vazio) | F2 | Critério da issue |
| T6 | Nenhum enum cru visível nos selects de prioridade | F1 | UX |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Descrição opcional** | Request/service/IT; **T1–T2** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **UI quick add + atalhos** | Modal/barra; chips prazo; labels PT; **T3, T6** |
| **F2** | **Validação + toast + wire API** | Inline; toast “Ver”; integração Home/lista; **T4–T5** |

### Ordem

```text
B1 → F1 → F2
```
