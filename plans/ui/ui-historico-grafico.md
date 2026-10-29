# Feature — Histórico com gráfico e comparação (UI/UX · issue #10)

Escopo: gráfico semanal (CSS/SVG), comparação vs período anterior, títulos/datas em pt-BR, empty motivacional; corrigir classes órfãs remanescentes.  
Fora deste slice: heatmap multi-semana (avaliar depois; sem cache; evitaria N consultas); lib de charts; streaks; novos endpoints genéricos `de`/`até`.

Dependências: `historico.md` (GET dia/semana), `ui-design-system.md`, `ui-layout-nav.md`. Issue: #10 · pai #1.

---

## Entities

Nenhuma.

---

## Back-end

Sem endpoint novo.

Comparação “vs semana passada”: o front chama o endpoint de semana **duas vezes** (semana corrente e `semanaInicio - 7 dias`) e calcula variação percentual no cliente. Sem N+1 por dia; sem materialização.

---

## Front-end

1. **Gráfico de barras** — minutos ou XP por dia da semana selecionada (SVG/CSS, sem lib); alternativa textual acessível (lista/tabela sr-only ou `aria`).
2. **Comparação** — em cada métrica: “+12% XP vs. semana passada” (ou “—” se base 0).
3. **Títulos** — card do dia dinâmico: “Hoje”, “Ontem”, “Segunda, 20/10”; nunca título fixo “Hoje” com subtítulo ISO cru.
4. **Datas** — sempre pt-BR.
5. **Empty** — mensagem curta + link para o plano de hoje.
6. Remover dependência de `home-panel` / `home-nav` / `form-error` se ainda houver.

---

## Decisões fechadas (revisão)

1. Sem lib de gráfico.
2. Comparação = 2 requests de semana; heatmap fora.
3. Métrica do gráfico: **XP por dia** como padrão da barra (alinhado a Karma); minutos podem ser toggle simples se barato — senão só XP neste slice.
4. Sem cache.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Helper de variação % (0 base, positivo, negativo) | F2 | Comparação correta |
| T2 | Título do dia: hoje/ontem/outra data formatada pt-BR | F1 | Critério datas |
| T3 | Gráfico tem alternativa textual acessível | F3 | A11y |
| T4 | `HistoricoPage` testes atualizados (navegação dia/semana) | F3 | Regressão |

### Unitários / API

Nenhum obrigatório novo no back (contratos de `historico.md` já cobertos).

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Títulos e datas pt-BR** | Card dinâmico; limpar classes órfãs; **T2** |
| **F2** | **Comparação vs período anterior** | 2ª chamada semana; labels ↑/↓; **T1** |
| **F3** | **Gráfico semanal + empty** | SVG/CSS + a11y + empty motivacional; **T3–T4** |

### Ordem

```text
F1 → F2 → F3
```
