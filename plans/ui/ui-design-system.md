# Feature — Design system: tokens, botões e campos (UI/UX · issue #3)

Escopo: tokens CSS em `:root`; classes base reutilizáveis (botão, campo, card, badge); eliminar `!important` e cores hex soltas; `:focus-visible` padrão; corrigir classes inexistentes.  
Fora deste slice: tema escuro (`prefers-color-scheme` opcional depois); redesenho de layout/navegação (`ui-layout-nav.md`); toasts/skeletons (`ui-estados-feedback.md`); pixel-perfect de cada tela de domínio.

Dependências: idealmente após ou em paralelo controlado com `ui-layout-nav.md` (base visual). Issue: #3 · pai #1.

---

## Entities

Nenhuma.

---

## Back-end

Sem endpoints. Sem mudança de comportamento de API.

---

## Front-end

1. **Tokens em `:root`** — espaçamento, raio, sombra; cores semânticas: `--success`, `--warning`, `--danger` / `--danger-bg`, `--priority-alta|media|baixa`, `--status-*` (alinhados aos enums do produto).
2. **Classes base** — `.btn` + modificadores `--primary`, `--secondary`, `--danger`, `--ghost`, `--sm`; `.field` (label + input/select/textarea); `.card`; `.badge`.
3. **Migração** — telas passam a usar as classes base; remover redefinições por página (`.auth-form button`, `.filtros-actions button`, `.capacidade-* button`, etc.).
4. **Higiene** — zero `!important`; nenhuma cor hex fora de `:root` (ou variáveis derivadas); remover ou substituir usos de `home-panel`, `home-nav`, `form-error` inexistentes.
5. **Foco** — `:focus-visible` em botões, links e campos interativos.
6. **Comportamento** — mesma UX funcional (labels/fluxos); só consistência visual.

---

## Decisões fechadas (revisão)

1. Um conjunto pequeno de variantes de botão (primary / secondary / danger / ghost + sm).
2. Prioridade e status usam tokens semânticos (não hex por página).
3. Tema escuro **fora** deste slice.
4. Sem dependência de biblioteca de componentes.
5. Testes existentes devem continuar passando (ajustes de seletores/`getByRole` se classes mudarem).

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Smoke: páginas principais ainda montam e CTAs principais seguem clicáveis após migração de classes | F2 | Regressão visual/funcional |
| T2 | Checklist estático ou teste de contrato CSS: ausência de `!important` no CSS da app (script/grep no CI ou teste de arquivo) | F1 | Critério de aceite da issue |

### Unitários / API

Nenhum.

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Tokens + classes base** | `:root`, `.btn*`, `.field`, `.card`, `.badge`, `:focus-visible`; remover `!important` óbvios; **T2** |
| **F2** | **Migrar telas** | Auth, Home, Tarefas, Capacidade, Histórico, Perfil, forms; limpar seletores por página e classes órfãs; **T1** |

### Ordem

```text
F1 → F2
```
