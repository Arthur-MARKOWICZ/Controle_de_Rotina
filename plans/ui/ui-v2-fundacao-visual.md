# Feature — UI v2: fundação visual (1º slice do épico `ui-v2-epico.md`)

Escopo: nova paleta clara/escura (tokens), tipografia Plus Jakarta Sans, escala de raio/sombra/motion, ícones Phosphor, restyle das classes base (`.btn`, `.field`, `.card`, `.badge`, chips, skeleton, toast, dialog) e remoção do fundo com gradiente.  
Fora deste slice: estrutura do shell/nav (`ui-v2-shell-nav.md`); mudanças de fluxo/UX de qualquer tela; redesenho de layout de páginas específicas; endpoints.  
Dependências: `ui-design-system.md` (feito — classes base e contrato CSS existentes), `ThemeProvider` (feito — tema claro/escuro/sistema).

---

## Conceito

Trocar a “pele” do app inteiro **sem tocar em JSX de páginas** sempre que possível: os nomes de tokens atuais (`--bg`, `--panel`, `--surface`, `--ink`, `--muted`, `--line`, `--accent`, …) **são mantidos** e recebem valores novos. Assim as ~15 folhas de estilo já migradas para tokens herdam o visual novo de uma vez; só entram tokens **novos** onde não havia equivalente.

Estilo (skill): **Flat Design + micro-interações** — superfícies brancas/neutras, um acento forte, sombras discretas só para elevação real (cards flutuantes, drawer, dialog, toast), hover/press por cor e não por deslocamento de layout.

---

## Tokens

### Mantidos com valores novos

| Token | Claro | Escuro | Observação |
|-------|-------|--------|------------|
| `--bg` | `#F8FAFC` | `#0B1220` | Fundo liso (sem gradiente) |
| `--bg-accent` | `#F1F5F9` | `#0F172A` | Só para faixas/áreas secundárias |
| `--bg-spot` | removido | removido | Usado apenas no gradiente; apagar usos |
| `--panel` | `#FFFFFF` | `#111A2E` | Cards |
| `--surface` | `#FFFFFF` | `#111A2E` | Inputs, popovers |
| `--surface-muted` | `#F1F5F9` | `#1E293B` | Hover de linha, trilhas de barra |
| `--ink` | `#0F172A` | `#F1F5F9` | Texto principal |
| `--muted` | `#475569` | `#94A3B8` | Texto secundário (≥ 7:1 nos dois) |
| `--line` | `#E2E8F0` | `#1E293B` | Bordas/divisores |
| `--accent` | `#0F766E` | `#2DD4BF` | Primária |
| `--accent-hover` | `#115E59` | `#5EEAD4` | |
| `--accent-soft` | `#CCFBF1` | `#134E4A` | Item ativo, badges de foco |
| `--focus` | `#0D9488` | `#2DD4BF` | Anel de foco |
| `--success` / `--success-bg` | `#15803D` / `#DCFCE7` | `#4ADE80` / `#14321F` | |
| `--warning` / `--warning-bg` | `#B45309` / `#FEF3C7` | `#FBBF24` / `#3A2A0B` | Deixa de ser usado como hover genérico |
| `--danger` / `--danger-bg` / `--danger-border` | `#B91C1C` / `#FEE2E2` / `#FECACA` | `#F87171` / `#3B1414` / `#7F1D1D` | `#DC2626` reprovava T1 (3,95:1 sobre `#FEE2E2`) |
| `--priority-*`, `--status-*` | derivam dos semânticos acima | idem | Mesma estrutura de hoje |

### Novos

| Token | Valor | Uso |
|-------|-------|-----|
| `--on-accent` | claro `#FFFFFF` · escuro `#0B1220` | Texto sobre `--accent` (substitui `color: var(--surface)` em `.btn`) |
| `--cta` / `--cta-hover` | `#EA580C` / `#C2410C` (claro) · `#FB923C` / `#FDBA74` (escuro) | XP, conquistas, destaque do bloco ativo |
| `--on-cta` | `#0F172A` (ambos) | Texto sobre `--cta` (nunca branco — 3,56:1) |
| `--radius-sm/md/lg/xl/full` | `0.375rem / 0.5rem / 0.75rem / 1rem / 999px` | Botões `md`, cards `lg`, drawer/dialog `xl`, chips `full` |
| `--shadow-sm/md/lg` | sombras neutras de baixa opacidade (derivadas de `--ink` com `color-mix`) | card hover / popover / drawer-dialog |
| `--dur-fast` / `--dur-base` / `--dur-slow` | `120ms / 180ms / 260ms` | Hover / entrada de elementos / drawer |
| `--ease-out` / `--ease-in` | `cubic-bezier(0.2, 0, 0, 1)` / `cubic-bezier(0.4, 0, 1, 1)` | Entrada / saída (saída mais rápida que entrada) |
| `--icon-sm` / `--icon-md` / `--icon-lg` | `16 / 20 / 24` (px) | Tamanho único por contexto |
| `--font-sans` | `"Plus Jakarta Sans", system-ui, sans-serif` | Fonte única |
| `--text-xs…--text-2xl` | `0.75 / 0.875 / 1 / 1.125 / 1.375 / 1.75rem` | Escala tipográfica |

Remover: tokens e regras só usados pelo gradiente (`--bg-spot`, `background: radial-gradient…` em `:root` e `.app-shell`), `--neutral-warm` (substituir por `--surface-muted`).

---

## Front-end

1. **Fonte** — `index.html`: trocar Fraunces + Source Sans 3 por Plus Jakarta Sans (400/500/600/700, `display=swap`). `:root { font-family: var(--font-sans) }`. `.app-shell__brand` perde a serifa.
2. **Tokens** — reescrever `styles/tokens.css` conforme tabelas (claro em `:root`, escuro em `:root[data-theme="dark"]`).
3. **Fundo** — `:root` e `.app-shell` com `background: var(--bg)` liso; a linha divisória da sidebar passa a ser `border-right` (remover o truque de `background-image` em `.app-shell`).
4. **Botões** (`buttons.css`) — altura mínima 40px (`--sm` 32px); raio `--radius-md`; peso 600; texto `--on-accent`; secundário = fundo `--surface` + borda `--line`, hover `--surface-muted` (não mais bege); ghost = sem borda, hover `--surface-muted`; danger mantém; nova variante `--cta` (fundo `--cta`, texto `--on-cta`); estado `:active` com escurecimento (sem `transform` que mexa layout); `transition: background-color, border-color, color var(--dur-fast) var(--ease-out)`; suporte a ícone (`.btn > svg { width: var(--icon-sm) }`) e variante `.btn--icon` quadrada com `aria-label` obrigatório.
5. **Campos** (`field.css`) — altura 40px, raio `--radius-md`, borda `--line`, foco = borda `--accent` + anel `--focus` 3px translúcido; label 500 `--text-sm`; hint/erro `--text-xs`.
6. **Card** (`card.css`) — raio `--radius-lg`, borda `--line`, sem sombra por padrão; `.card--interactive` com hover `--shadow-sm` + borda `--accent-soft`.
7. **Badge/chip** — raio `--radius-full`, `--text-xs`, peso 600; chips de filtro com estado `aria-pressed` visível por fundo **e** ícone de check (não só cor).
8. **Feedback** (`feedback.css`) — toast com raio `--radius-lg`, `--shadow-lg`, ícone por tipo; skeleton com `--surface-muted`; dialog nativo com `--radius-xl`, backdrop `color-mix(--ink 40%)` e animação de entrada `--dur-base` (fade + scale 0.98→1).
9. **Motion** — segue o padrão já usado em `feedback.css`/`app-shell.css`: cada arquivo que declara `transition`/`animation` tem o próprio bloco `@media (prefers-reduced-motion: reduce)` zerando-os (sem regra global com `!important`, que violaria o contrato CSS).
10. **Ícones** — `npm i @phosphor-icons/react`. Componente nenhum por cima: importar direto (`import { CalendarBlank } from '@phosphor-icons/react'`) com `size` vindo dos tamanhos de token e `weight="regular"` (ativo: `"fill"` só no item de nav selecionado). Decorativo ao lado de texto → `aria-hidden`. Mapa de ícones do épico:

| Uso | Ícone Phosphor |
|-----|----------------|
| Hoje | `SunHorizon` |
| Tarefas | `CheckSquare` |
| Projetos | `FolderSimple` |
| Calendário | `CalendarBlank` |
| Capacidade | `Gauge` |
| Histórico | `ChartLineUp` |
| Perfil | `UserCircle` |
| Sair | `SignOut` |
| Nova tarefa | `Plus` |
| Iniciar / Concluir / Abortar | `Play` / `Check` / `Stop` |
| Remover / Excluir | `X` / `Trash` |
| Editar | `PencilSimple` |
| Filtros | `FunnelSimple` |
| XP | `Lightning` |
| Conflito/aviso | `WarningCircle` |
| Fechar drawer/dialog | `X` |

11. **Higiene** — nenhum hex fora dos blocos de tokens (contrato atual); nenhum `!important`; apagar classes que ficarem órfãs.

---

## Decisões fechadas (revisão)

1. Nomes de tokens existentes são preservados; só valores mudam (+ tokens novos da tabela).
2. Fonte única Plus Jakarta Sans; sem serifa no app.
3. Primária `#0F766E` (claro) / `#2DD4BF` (escuro); laranja só com texto escuro.
4. Sombras apenas em elementos elevados (popover, drawer, dialog, toast, card interativo no hover).
5. Ícones Phosphor, peso `regular`, tamanhos 16/20/24.
6. Sem GSAP, sem Framer Motion: só CSS transitions/keyframes.
7. Tema escuro continua obrigatório e com a mesma troca via `data-theme`.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Contraste: teste que lê `tokens.css`, resolve os pares (`--ink`/`--bg`, `--ink`/`--panel`, `--muted`/`--panel`, `--on-accent`/`--accent`, `--on-cta`/`--cta`, `--danger`/`--danger-bg`, `--success`/`--success-bg`, `--warning`/`--warning-bg`) nos **dois** temas e exige ≥ 4,5:1 | F1 | Evita regressão de acessibilidade ao mexer em cor |
| T2 | Contrato CSS existente (`design-system.css.test.ts`) continua verde: sem `!important`, sem hex fora de tokens, tema escuro definido | F1–F2 | Critério já adotado no projeto |
| T3 | Contrato: nenhum `radial-gradient`/`linear-gradient` em `:root`/`.app-shell` e nenhuma referência a `Fraunces` no CSS | F1 | Garante a troca visual (“cara de velha”) |
| T4 | Smoke (`design-system.smoke.test.tsx`): páginas principais montam e CTAs seguem acessíveis por `getByRole` | F2 | Restyle não pode quebrar fluxo |
| T5 | Ícone decorativo ao lado de texto não entra na árvore acessível (`aria-hidden`) e `.btn--icon` sem texto tem nome acessível | F3 | Regra de a11y da skill |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Tokens + fonte + fundo** | `tokens.css` (claro/escuro + novos), `index.html` (fonte), `base.css` (fonte, fundo liso, reduced-motion), remover gradientes/`--bg-spot`/`--neutral-warm`; **T1, T2, T3** |
| **F2** | **Classes base** | `buttons.css` (variantes + `--cta` + `--icon` + estados), `field.css`, `card.css` (+`--interactive`), `badge.css`, chips, `feedback.css` (toast/skeleton/dialog); varrer CSS de páginas por overrides que briguem com a base; **T2, T4** |
| **F3** | **Ícones Phosphor** | Dependência + uso nos botões de ação já existentes (Iniciar/Concluir/Abortar/Remover/Editar/Excluir/Filtros/Nova tarefa) sem mudar fluxo; `aria-hidden` nos decorativos; **T5** |

### Ordem

```text
F1 → F2 → F3
```
