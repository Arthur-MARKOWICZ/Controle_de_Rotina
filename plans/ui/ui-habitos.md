# Feature — UI Hábitos (lista, plano e streak)

Issue: [#43](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/43) (UI/UX)

Escopo: telas/fluxos para hábitos; pausar/reativar/excluir; exibir horário preferencial; blocos de hábito na Home/revisão; conflito com `itensNaoAlocados` acionável; streak na lista/detalhe; execução do bloco; alinhado ao design system v2.  
Fora deste slice: novos endpoints além de `habitos.md`; RRULE; social; redesign geral da shell; histórico agregado com streak.

Dependências: `plans/negocio/habitos.md`; `ui-layout-nav` / shell v2; `ui-home-agenda` / `ui-revisao-rascunho` / `ui-v2-hoje-fluida`; `ui-estados-feedback`. Idealmente após F* mínimas do plano de negócio.

---

## Entities

Nenhuma nova no front. Consumir `/habitos`, `/planos`, `/execucoes`.

---

## Back-end

Sem endpoints novos. Garantir proxy `/habitos` (ver `API_PROXY.md`) se o slice de negócio ainda não aplicou no ambiente.

---

## Front-end

1. **Nav** — item “Hábitos” (desktop + bottom bar) → `/habitos` (ícone alinhado à shell v2).
2. **Lista** — título, dias (chips Seg–Dom), duração, status, **streak atual** (e melhor no detalhe); ações pausar / reativar; excluir com confirmação + restaurar se API devolver deletado; empty + CTA criar; skeleton.
3. **Criar / editar** — formulário: título, descrição opcional, duração (minutos, step 15), multi-select dias da semana (≥ 1), horário preferencial opcional (time input; pode limpar). Status não escolhido no create.
4. **Home / revisão (rascunho)** — card de bloco com origem hábito (rótulo “Hábito”, título, duração; horário preferencial se existir, como dica — sem fingir slot fixo na agenda). Motivo curto: “Recorrente · [dias]” em vez de prazo/prioridade de tarefa.
5. **Conflito** — se `temConflito` / `itensNaoAlocados`:
   - listar omitidos (tarefa vs hábito);
   - ações: **Remover bloco** do plano (liberar capacidade), **Adicionar** item omitido (chama POST blocos se ainda couber — senão toast de conflito), atalho capacidade (já existente), dismiss.
6. **Execução** — mesmos CTAs iniciar/concluir/abortar do bloco; toast `+N XP`; ao concluir, atualizar streak exibido se a API devolver hábito enrich.
7. **A11y** — labels nos dias/horário; foco; não depender só de cor para status pausado.
8. Viewport estreita usável; tokens v2; cards só onde há interação de lista/form.

---

## Decisões fechadas (revisão)

1. UI dedicada em `plans/ui/`; domínio em `plans/negocio/habitos.md`.
2. Horário preferencial é dica visual, não grade horária.
3. Conflito reutiliza espírito de `ui-revisao-rascunho.md` com lista `itensNaoAlocados`.
4. Streak visível na lista (atual) e detalhe (atual + melhor).
5. Issue #43 (UI).

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Lista renderiza hábitos e pausar chama API | F1 | CRUD visível |
| T2 | Form exige ≥ 1 dia e duração múltipla de 15 (validação UI alinhada ao back) | F2 | Aceite create |
| T3 | Card de bloco HABITO na home/revisão mostra título e rótulo de hábito | F3 | Aceite plano |
| T4 | Banner/lista de omitidos oferece ação que remove ou tenta adicionar | F4 | Conflito acionável |
| T5 | Streak aparece na lista quando API envia `streakAtual` | F1 | Aceite streak |

Se harness front frágil: priorizar T3–T4 com Testing Library no padrão da Home/revisão.

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Nav + lista + streak + proxy** | Rota `/habitos`, lista, pausar/reativar/excluir; checar proxy; **T1**, **T5** |
| **F2** | **Form criar/editar** | Campos + validações; **T2** |
| **F3** | **Bloco hábito na Home/revisão** | Card origem HABITO + copy; **T3** |
| **F4** | **Conflito omitidos + execução** | Lista acionável; CTAs execução/XP; **T4** |

### Ordem

```text
F1 → F2 → F3 → F4
```

F3/F4 dependem de B10–B12 de `habitos.md`.
)
