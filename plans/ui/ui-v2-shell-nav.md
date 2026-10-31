# Feature — UI v2: shell e navegação (2º slice do épico `ui-v2-epico.md`)

Escopo: sidebar e bottom bar com ícones; barra superior com título da página, botão global “Nova tarefa” e XP compacto; componente `PageHeader` reutilizável; menu de conta (Perfil/Tema/Sair) no lugar dos links soltos; Perfil acessível no mobile sem ocupar a bottom bar.  
Fora deste slice: conteúdo das páginas (slices 3–6); novas rotas; atalhos de teclado novos (os existentes em `useAtalhosTeclado` continuam).  
Dependências: `ui-v2-fundacao-visual.md` (tokens/ícones), `ui-layout-nav.md` e `ui-a11y-atalhos.md` (feitos).

---

## Conceito

Hoje o shell é uma lista de links de texto, a marca em serifa ocupa o topo da sidebar, o XP fica sozinho numa faixa, e cada página inventa o próprio cabeçalho (`home-topo`, `tarefas-header`, …) — o botão “Nova tarefa” aparece em lugares diferentes. O shell v2 concentra **o que é global** (onde estou, criar tarefa, meu XP, minha conta) num lugar só, e as páginas ficam só com o próprio conteúdo.

---

## Front-end

1. **Sidebar (≥ 640px)** — largura 15rem; topo com marca (texto “Rotina” + ícone `CheckCircle` em `--accent`, sem serifa); itens com ícone 20px + rótulo; ativo com fundo `--accent-soft`, texto `--accent`, ícone `weight="fill"` e `aria-current="page"` (já existente); hover `--surface-muted`; rodapé com o **menu de conta**.
2. **Bottom bar (< 640px)** — 5 destinos com ícone + rótulo curto: Hoje, Tarefas, Calendário, Capacidade, **Mais**. “Mais” abre um sheet (`<dialog>` ancorado embaixo) com Projetos, Histórico, Perfil, Tema e Sair. Alvos ≥ 44px; respeita `env(safe-area-inset-bottom)`.
3. **Barra superior** (todas as larguras) — esquerda: título da página atual (mesma fonte de `documentTitle.ts`, sem duplicar mapa de rotas); direita: XP compacto (nível + barra fina + ícone `Lightning` em `--cta`; tooltip/`aria-label` com XP atual/próximo nível) e botão primário **Nova tarefa** (`Plus`; no mobile vira `.btn--icon` com `aria-label="Nova tarefa"`) que abre o `QuickAddTarefaDialog` já existente (mesmo estado usado pelo atalho `n`).
4. **Menu de conta** — botão com avatar de iniciais (do `/me`) + nome; abre popover com Perfil, seletor de tema (reutiliza `ThemePreferenceField`/`themePreference`) e Sair. Fecha com Esc/clique fora; foco volta ao gatilho.
5. **`PageHeader`** — componente `layout/PageHeader.tsx` com `title`, `description?` e `actions?`; usado pelas páginas a partir dos slices 3–6 (aqui: só criar e aplicar em Perfil e Histórico, que não têm UX nova).
6. **Remover duplicidade** — botão “Nova tarefa” local da Home (`home-topo`) e da página Tarefas, e os `QuickAddTarefaDialog` duplicados em `HomePage`/`TarefasPage`, passam a usar o do shell; para a Home continuar sabendo que uma tarefa foi criada, o shell expõe `onTarefaCriada` via contexto leve (`layout/QuickAddContext.tsx`: `abrir()` + assinatura de “criada”), sem cache.
7. **Transição de rota** — conteúdo do `Outlet` entra com fade 120ms (`--dur-fast`); desligado em reduced-motion. Sem animação de saída.
8. **Foco ao navegar** — ao trocar de rota, foco vai para o `h1` da página (anúncio para leitor de tela), mantendo o comportamento de título da aba.

---

## Decisões fechadas (revisão)

1. Bottom bar com 4 destinos + “Mais”; Projetos/Histórico/Perfil/Sair dentro de “Mais”.
2. “Nova tarefa” global vive **só** no topo do shell (e no atalho `n`); páginas não repetem o botão, exceto empty states.
3. XP compacto no topo; o painel detalhado de XP continua onde já está (Perfil/Histórico).
4. Sem sidebar recolhível neste slice.
5. Tema trocável pelo menu de conta (além do Perfil).
6. Contexto do quick-add não guarda dados — só abre o dialog e notifica criação.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Itens de nav têm nome acessível (rótulo) e o ativo mantém `aria-current="page"`; ícones são `aria-hidden` | F1 | Regressão de `AppShell.test.tsx` + a11y |
| T2 | Mobile: “Mais” abre o sheet com Projetos, Histórico, Perfil e Sair; Esc fecha e devolve o foco | F2 | Destinos não podem sumir no mobile |
| T3 | Botão “Nova tarefa” do topo abre o `QuickAddTarefaDialog`; atalho `n` continua abrindo o mesmo dialog | F3 | Um só ponto de criação |
| T4 | Criar tarefa pelo shell notifica a Home (callback do contexto chamado) | F3 | Home recarrega elegíveis como hoje (`elegiveisTick`) |
| T5 | Menu de conta: Sair chama `logout`; troca de tema altera `data-theme` | F4 | Logout e tema não podem regredir |
| T6 | Ao navegar, foco vai para o `h1` da nova página | F5 | Anúncio de rota |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Sidebar com ícones** | Marca, itens com ícone, estado ativo, `app-shell.css` reescrito; **T1** |
| **F2** | **Bottom bar + “Mais”** | 4 destinos + sheet; safe-area; **T2** |
| **F3** | **Topo: título + XP compacto + Nova tarefa** | `QuickAddContext`; remover botão/dialog duplicado da Home; **T3, T4** |
| **F4** | **Menu de conta** | Avatar de iniciais, popover Perfil/Tema/Sair; **T5** |
| **F5** | **`PageHeader` + foco/transição de rota** | Componente; aplicar em Perfil e Histórico; foco no `h1`; fade do Outlet; **T6** |

### Ordem

```text
F1 → F2
F1 → F3 → F4 → F5
```
