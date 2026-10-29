# Feature — Responsividade, acessibilidade e atalhos (UI/UX · issue #12)

Escopo: breakpoints/largura de conteúdo no shell; alvos de toque 44×44; atalhos de teclado documentados; contraste AA; `<title>` por rota; status nunca só por cor.  
Fora deste slice: PWA/manifest (opcional depois); redesign de features de domínio já cobertas; leitores de tela avançados além do baseline.

Dependências: `ui-layout-nav.md`, `ui-design-system.md`, slices de tela já migrados. Issue: #12 · pai #1. Executar **depois** das telas principais para não retrabalhar.

---

## Entities

Nenhuma.

---

## Back-end

Nenhum.

---

## Front-end

1. **Layout** — largura de conteúdo única no shell; remover larguras fixas conflitantes por página (`40rem` / `64rem` / etc.) em favor do container do layout.
2. **Toque** — ações principais (Concluir, Abortar, nav inferior) ≥ 44×44px.
3. **Atalhos** — `N` nova tarefa (quick add); `G` depois `H`/`T`/… navegação; `?` abre modal de ajuda com a lista; **ignorados** quando o foco está em input/textarea/select/contenteditable.
4. **Status** — texto e/ou ícone além da cor; verificar contraste AA dos tokens (ajustar tokens se necessário).
5. **`<title>`** — por rota, ex.: `Tarefas · Controle de Rotina`.
6. Checklist manual/automático: utilizável em 320px em todas as telas protegidas.

---

## Decisões fechadas (revisão)

1. Atalhos estilo Todoist reduzidos ao conjunto acima (sem mapa completo de `G`+todas as letras no MVP além de Hoje/Tarefas/Capacidade/Histórico).
2. PWA fora.
3. Sem lib de hotkeys se um listener no shell resolver; se adicionar lib, justificar — preferir sem.
4. Contraste AA obrigatório nos tokens semânticos usados em badge/texto.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Com foco em `<input>`, atalho `N` **não** abre quick add | F2 | Não quebrar digitação |
| T2 | Sequência `G` `H` navega para Hoje (fora de campo) | F2 | Navegação |
| T3 | `?` abre dialog de ajuda listando atalhos | F2 | Documentação |
| T4 | Cada rota protegida define `document.title` esperado | F3 | Critério da issue |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Breakpoints + touch + contraste** | Container único; 44px; AA nos tokens/badges |
| **F2** | **Atalhos + modal `?`** | Listener no shell; **T1–T3** |
| **F3** | **Títulos por rota + varredura 320px** | `useEffect`/helper de title; ajustes finais mobile; **T4** |

### Ordem

```text
F1 → F2 → F3
```
