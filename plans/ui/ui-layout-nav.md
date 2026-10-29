# Feature — Layout e navegação unificada (UI/UX · issue #2)

Escopo: shell único para rotas protegidas (`AppShell` + `Outlet`); sidebar no desktop e barra inferior no mobile; item ativo com `NavLink` / `aria-current`; remover navegações duplicadas das páginas.  
Fora deste slice: design system completo de botões/campos (`ui-design-system.md`); barra de XP (`ui-xp-feedback.md`); responsividade fina / atalhos (`ui-a11y-atalhos.md`); auth pública (login/cadastro).

Dependências: `usuario.md` (rotas protegidas). Issue: [#2](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/2) · pai [#1](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/1).

---

## Entities

Nenhuma. Só front.

---

## Back-end

Sem endpoints novos. Sem alteração de contrato.

---

## Front-end

1. **`AppShell`** — layout route no React Router envolvendo rotas autenticadas; `<Outlet />` para o conteúdo.
2. **Nav** — itens: **Hoje** (`/`), **Tarefas**, **Capacidade**, **Histórico**; rodapé da sidebar: **Perfil** e **Sair** (reutiliza logout existente).
3. **Ativo** — `NavLink` com `aria-current="page"` e estilo de item selecionado.
4. **Breakpoints** — desktop: sidebar esquerda; `< 640px`: barra inferior (mesmos destinos; Perfil/Sair acessíveis sem perder logout).
5. **Slot XP** — região no topo da shell (vazia ou placeholder) reservada para `ui-xp-feedback.md`; não implementar barra de XP aqui.
6. **Limpeza** — remover headers/navs locais de `HomePage`, `TarefasPage`, `CapacidadePage`, `HistoricoPage`, `PerfilPage`, `TarefaFormPage` (links “Início”, “Voltar” redundantes, `home-nav` / `home-panel` órfãos).
7. **Largura** — conteúdo usável em **320px** sem scroll horizontal neste shell (sem redesenhar cada tela).

---

## Decisões fechadas (revisão)

1. Um único layout para todas as rotas protegidas; páginas públicas (login/cadastro) fora do shell.
2. Destinos fixos: Hoje, Tarefas, Capacidade, Histórico + Perfil/Sair.
3. Mobile = barra inferior a partir de 640px; desktop = sidebar.
4. Sem libs de UI novas.
5. Espaço de XP só reservado; feedback de XP é slice seguinte na fila de produto (após design system / estados, ver `ordem_execucao`).

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Harness: Vitest + Testing Library (já usado no front).

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Rotas protegidas renderizam `AppShell` com links Hoje/Tarefas/Capacidade/Histórico | F1 | Shell único |
| T2 | Rota ativa expõe `aria-current="page"` no item correspondente | F2 | Acessibilidade / indicação visual |
| T3 | Páginas deixam de depender dos links locais antigos (ajustar testes de `HomePage`, `HistoricoPage`, etc. que quebrarem) | F3 | Regressão |

### Unitários / API

Nenhum (sem back).

---

## Atividades

### Backend

Nenhuma.

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **AppShell + rota layout** | Componente + wiring em `App.tsx`; Outlet; placeholder do slot XP; **T1** |
| **F2** | **Nav desktop/mobile + ativo** | Sidebar / bottom bar; `NavLink` + `aria-current`; Perfil/Sair; **T2** |
| **F3** | **Remover navs duplicadas** | Limpar páginas; corrigir classes órfãs usadas só para nav; atualizar testes de página; **T3** |

### Ordem

```text
F1 → F2 → F3
```
