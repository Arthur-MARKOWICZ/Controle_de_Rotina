# Feature — Lista de tarefas agrupada e filtros na URL (UI/UX · issue #8)

Escopo: lista legível em mobile (cards/linhas), agrupamento por prazo, visões rápidas, filtros essenciais com estado na query string, excluir com desfazer, empty state com CTA.  
Fora deste slice: novas queries/endpoints de agregação; dezenas de filtros avançados; NLP; quick add (já em `ui-quick-add.md`, só consumir).

Dependências: `tarefa.md` (listagem, soft delete, restore), `ui-quick-add.md`, `ui-estados-feedback.md`. Issue: #8 · pai #1.

---

## Entities

Nenhuma.

---

## Back-end

Sem endpoints novos. Reutilizar paginação/filtros atuais de `GET /tarefas` e restore existente.

**Finalizar na lista (checkbox):** só se a transição via `PATCH` já for permitida pelas regras de `tarefa.md`. Se o status `FINALIZADA` pela lista conflitar com a regra “finaliza só via execução”, **não** implementar checkbox neste slice (fica fora) — preferir não inventar atalho que burle o domínio.

---

## Front-end

1. **Layout** — cards/linhas legíveis em **320px** (sem tabela com scroll horizontal).
2. **Grupos** — Atrasadas / Hoje / Esta semana / Depois / Sem prazo (contador por grupo). “Sem prazo” só aparece se a API vier a permitir null (hoje prazo é obrigatório — grupo pode ficar vazio até issue #27).
3. **Prazo relativo** — “amanhã”, “em 3 dias”, “atrasada há 2 dias”; data absoluta no `title`.
4. **Visões (chips)** — Todas, Pendentes, Em progresso, Finalizadas, Excluídas; aplicação imediata.
5. **Filtros avançados** — painel recolhido; só prazo, duração, prioridade, ordenação (remover ruído de criado/atualizado de/até da UI principal).
6. **URL** — `useSearchParams` para visão, filtros e página; recarregar/voltar preserva estado.
7. **Excluir** — soft delete + toast “Desfazer” chamando restore; sem confirm modal se desfazer estiver disponível.
8. **Empty** — CTA “Criar primeira tarefa” (abre quick add).
9. Labels PT para prioridade/status.

---

## Decisões fechadas (revisão)

1. Sem novas queries no backend.
2. Filtros/página na query string.
3. Excluir = desfazer via toast (não dialog), alinhado a `ui-estados-feedback.md`.
4. Checkbox “finalizar” só se domínio já permitir; senão fora.
5. Agrupamento é **client-side** sobre a página carregada ou sobre o conjunto retornado pelos filtros atuais (não inventar endpoint de grupos).

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Com search params, recarregar a página reaplica visão/filtro | F2 | Persistência na URL |
| T2 | Excluir mostra toast Desfazer e restore é chamado ao desfazer | F3 | Critério da issue |
| T3 | Lista renderiza grupos com contador (fixture com prazos variados) | F1 | Agrupamento |
| T4 | Em viewport estreito (ou ausência de `<table>`), não há dependência de scroll horizontal da tabela antiga | F1 | Mobile |
| T5 | Empty state oferece CTA de criar | F4 | Onboarding leve |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Lista em cards + grupos + prazo relativo** | Substituir tabela; grupos; labels PT; **T3–T4** |
| **F2** | **Chips + filtros + URL** | Visões, painel recolhido, `useSearchParams`; **T1** |
| **F3** | **Excluir com desfazer** | Soft delete + toast restore; **T2** |
| **F4** | **Empty + quick add CTA** | Empty state; wire `ui-quick-add`; **T5** |

### Ordem

```text
F1 → F2 → F3 → F4
```
