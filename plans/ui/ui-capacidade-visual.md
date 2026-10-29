# Feature — Capacidade semanal visual e ajuste rápido (UI/UX · issue #9)

Escopo: visão semanal em 7 colunas com barras e stepper; salvar semana em lote (ou autosave claro); presets; override inline; atalho na Home “Ajustar capacidade de hoje”.  
Fora deste slice: calendário completo tipo Google; novos endpoints além dos de `capacidade.md`; planner automático ao mudar capacidade (só oferecer gerar nova versão).

Dependências: `capacidade.md`, `ui-home-agenda.md`, `ui-estados-feedback.md`. Issue: #9 · pai #1.

---

## Entities

Nenhuma nova.

---

## Back-end

Sem endpoints novos. Usar APIs existentes de capacidade semanal, override e capacidade resolvida.

Se “salvar semana” exigir N PUTs já existentes, o front pode emitir em sequência na mesma ação de UI (sem endpoint batch novo neste slice). Sem cache.

---

## Front-end

1. **Grade semanal** — 7 colunas; barra de altura proporcional às horas; stepper (− / +) por dia.
2. **Salvar** — uma ação “Salvar semana” (lote) **ou** autosave com indicação “Salvo”; não um botão salvar por card.
3. **Presets** — “Dias úteis 4h, fim de semana 0h”; “Copiar semana anterior” (lê semana anterior via API já usada na navegação).
4. **Override inline** — badge “exceção” no dia; ação “Voltar ao padrão” (delete override).
5. **Total** — “Nh disponíveis” na semana.
6. **Home** — “Ajustar capacidade de hoje”: define override do dia atual; em seguida oferece CTA “Gerar nova versão do plano” (não gera sozinho sem confirmação).
7. Skeleton da grade no loading.

---

## Decisões fechadas (revisão)

1. Sem endpoint batch novo: lote = N chamadas existentes sob uma UX.
2. Override do dia atual acessível a partir da Home.
3. Diferença padrão vs exceção visualmente clara (badge).
4. Remover override reversível → toast com desfazer se o recreate for trivial; senão confirm — preferir desfazer alinhado a `ui-estados-feedback.md`.
5. Não auto-gerar plano ao mudar capacidade; só sugerir.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Stepper altera valor do dia no estado local | F1 | Interação base |
| T2 | “Salvar semana” dispara persistência dos dias alterados | F2 | Critério “uma ação” |
| T3 | Dia com override mostra badge de exceção | F3 | Clareza visual |
| T4 | Atalho na Home abre/ajusta capacidade de hoje (override) | F4 | Caso “hoje só 1h” |
| T5 | Testes existentes de `CapacidadePage` atualizados e verdes | F2 | Regressão |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Grade semanal visual** | 7 colunas, barras, stepper, total; **T1** |
| **F2** | **Salvar lote + presets** | Salvar semana; presets; **T2, T5** |
| **F3** | **Override inline** | Badge, voltar ao padrão, desfazer; **T3** |
| **F4** | **Atalho na Home** | Ajustar hoje + CTA gerar versão; **T4** |

### Ordem

```text
F1 → F2 → F3 → F4
```
