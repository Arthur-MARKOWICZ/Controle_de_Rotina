# Feature — IA e nav: tarefas primeiro (1º slice do épico `ui-tarefas-primeiro-epico.md`)

Escopo: rota inicial `/tarefas`; mover a agenda do dia de `/` para `/plano`; reorganizar sidebar e bottom bar (primário / secundário / terciário); atualizar títulos, atalhos `G *` e redirects pós-login / catch-all.  
Fora deste slice: create só com título; CTAs “organizar dia” na lista; redesign visual da lista; mudança de regras do planner; novos endpoints.  
Dependências: `ui-v2-shell-nav.md` (feito); `ui-a11y-atalhos.md` (atalhos existentes).

---

## Conceito

O shell hoje trata **Hoje** (`/`) como primeiro destino. Este slice só muda **onde o usuário chega** e **como a nav prioriza destinos** — o conteúdo de `HomePage` permanece, mas passa a viver em `/plano` com rótulo **Plano**.

### Hierarquia

| Nível | Destinos | Onde aparece |
|-------|----------|--------------|
| Primário | Tarefas (`/tarefas`), Projetos (`/projetos`) | Sidebar (topo) + bottom bar |
| Secundário | Plano (`/plano`), Hábitos (`/habitos`) | Sidebar (grupo abaixo do primário) + bottom bar |
| Terciário | Capacidade, Calendário, Histórico | Sidebar (grupo inferior) + sheet **Mais** no mobile |

Bottom bar (5 slots): **Tarefas · Projetos · Plano · Hábitos · Mais**.  
Mais: Capacidade, Calendário, Histórico, Perfil, Tema, Sair (Projetos **sai** do Mais — já está na bottom bar).

Sidebar: três grupos visuais (rótulos opcionais `aria`-friendly ou só espaçamento + ordem; se houver heading, `aria-labelledby` / `<p class="…">` visual discreto — sem cards).

---

## Front-end

1. **Rotas** (`App.tsx`)  
   - `/` → `<Navigate to="/tarefas" replace />` (autenticado, dentro do shell).  
   - `/plano` → `HomePage` (mesmo componente de hoje).  
   - Catch-all autenticado `*` → `/tarefas` (não mais `/`).  
   - Pós-login / pós-cadastro: navegar para `/tarefas` (onde hoje for `/`).
2. **Nav** (`AppShell.tsx`, `MenuMais.tsx`) — listas conforme tabela; remover item **Hoje**; adicionar **Plano** → `/plano`.
3. **Títulos** (`documentTitle.ts`) — `/plano` = “Plano”; `/` não precisa de título próprio (redirect). Atualizar testes de título.
4. **Atalhos** (`atalhos.ts`) — `G H` → `/plano` (descrição: “Ir para Plano”); opcional `G P` → `/projetos` se não conflitar; manter `G T` tarefas, `G C` capacidade, `G I` histórico. Atualizar `AtalhosAjudaDialog` / testes (`G H` deixa de ir a `/`).
5. **Links internos** que apontam para `/` como “Hoje” (onboarding, empty states, CTAs “voltar ao plano”) → `/plano` com copy “Plano” onde fizer sentido **neste slice** (só strings óbvias de nav/atalho/onboarding que quebrem; CTAs novos de “organizar dia” ficam no slice `ui-planner-sob-demanda.md`).
6. **Ativo na nav** — `NavLink` de Plano com `end` adequado em `/plano`; Tarefas permanece ativo em `/tarefas` e drawers filhos (já existe).
7. Sem mudança de tokens/ícones além do ícone já usado em Hoje reaproveitado em Plano (`SunHorizon` ok ou trocar para ícone de lista/calendário se o rótulo “Plano” pedir — **decisão:** manter `SunHorizon` neste slice para diff mínimo).

---

## Decisões fechadas (revisão)

1. Landing = `/tarefas`; `/` só redireciona.
2. Agenda do dia = `/plano`; label **Plano** (não “Hoje”).
3. Bottom: Tarefas, Projetos, Plano, Hábitos + Mais; terciário só no Mais (e grupo inferior na sidebar).
4. Sem backend neste slice.
5. Atalho `G H` = Plano (`/plano`).

---

## Testes automatizados

Harness: Vitest + Testing Library (`AppShell.test.tsx`, `documentTitle.test.ts`, atalhos).

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Sidebar: ordem Tarefas, Projetos antes de Plano/Hábitos; Capacidade/Calendário/Histórico presentes | F1 | Hierarquia |
| T2 | Bottom: Tarefas, Projetos, Plano, Hábitos + Mais; Mais contém Capacidade, Calendário, Histórico (sem Projetos duplicado como único caminho) | F1 | Mobile |
| T3 | Rota `/` autentica e termina em `/tarefas` | F2 | Landing |
| T4 | `/plano` renderiza o conteúdo que era da Home (smoke: heading/região do plano) | F2 | Não perder a tela |
| T5 | `G H` navega para `/plano`; ajuda descreve “Plano” | F3 | Regressão de atalho |
| T6 | `tituloPorRota('/plano')` / rótulo da barra superior coerentes | F3 | Título |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Nav sidebar + bottom + Mais** | Listas, grupos, Projetos na bottom; **T1–T2** |
| **F2** | **Rotas `/plano`, redirect `/`, catch-all, pós-login** | `App.tsx` + auth navigate; **T3–T4** |
| **F3** | **Títulos, atalhos, links “Hoje” óbvios → Plano** | `documentTitle`, `atalhos`, testes; **T5–T6** |

### Ordem

```text
F1 → F2 → F3
```
