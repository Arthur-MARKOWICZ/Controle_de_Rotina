# Épico — UI/UX v2: visual moderno + interação fluida

Índice do épico (não executável sozinho). Cada slice tem plano próprio em `plans/ui/ui-v2-*.md`.  
Gerado com a skill `.cursor/skills/ui-ux-pro-max` (design system + buscas `ux`, `react`, `icons`, `typography`, `color`).

Problema relatado: a UI está **com cara de velha** e a UX está **travada**.

---

## Diagnóstico (código atual)

### Por que parece velha

| Sintoma | Onde |
|---------|------|
| Paleta bege/pergaminho (`--bg: #f3efe6`, `--panel: #fffdf8`) + gradiente radial no fundo | `styles/tokens.css`, `styles/app-shell.css` |
| Marca em serifa editorial (Fraunces) + Source Sans 3 | `index.html`, `.app-shell__brand` |
| Nenhum ícone no app inteiro (nav, ações, estados só com texto) | `layout/AppShell.tsx`, páginas |
| Sem elevação: cards só com borda 1px; um único `--shadow-sm` | `styles/card.css`, `tokens.css` |
| Hover de botões secundários em bege (`--warning-bg`) | `styles/buttons.css` |
| Raios pequenos (`0.35–0.75rem`), botões densos, hierarquia tipográfica fraca | `tokens.css`, `buttons.css` |
| Quase sem transições (só toast, skeleton e 2 barras) | `styles/*.css` |

### Por que parece travada

| Sintoma | Onde |
|---------|------|
| Home carrega em **cascata**: plano+capacidade → reservas+projetos → datas da proposta → execuções (4 rodadas sequenciais) | `HomePage.tsx` `carregarDia` |
| Toda ação espera o servidor e **recarrega**: iniciar/concluir/abortar chamam `listarExecucoes` de novo, embora a API já devolva a execução | `HomePage.tsx` `handleIniciar/Concluir/Abortar` |
| Remover bloco = `DELETE` + `buscarPlano` antes de qualquer feedback | `HomePage.tsx` `handleRemover` |
| Trocar dia da proposta troca o conteúdo inteiro por skeleton | `HomePage.tsx` `handleSelecionarDiaProposta` |
| Modal de confirmação até para aprovar o plano (ação central do produto) | `HomePage.tsx` `handleAprovar` |
| Criar/editar tarefa ou projeto sai da lista para outra página e volta (perde scroll/filtros visuais) | `TarefaFormPage`, `ProjetoFormPage` |
| Filtros de tarefas exigem abrir painel + “Aplicar filtros” | `TarefasPage.tsx` |
| Restaurar/excluir tarefa recarrega a lista inteira (`setRecarregarEm`) | `TarefasPage.tsx` |
| `HomePage.tsx` com 886 linhas e ~25 `useState` → difícil evoluir sem regressão | `HomePage.tsx` |

---

## Direção visual (saída da skill, verificada e ajustada)

Busca: `"productivity planner daily schedule" --design-system --density 6 --motion 3 --variance 4`.  
Produto casado: **Productivity Tool** → estilo **Flat Design + Micro-interactions** (secundário: Minimalism & Swiss).

**Aproveitado:** estilo flat limpo, teal como primária + laranja como acento, Plus Jakarta Sans, ícones Phosphor, transições 150–200ms, checklist (foco visível, reduced-motion, contraste, 375/768/1024/1440).

**Descartado (não se aplica a app logado):** padrão de landing “Product Demo + Features”, snippet GSAP/ScrollTrigger (dependência desnecessária), `On Primary: #000` (texto preto em teal fica estranho), teal `#0D9488` como fundo de botão (3,74:1 com branco — reprova AA).

**Ajuste de contraste (medido):** primária `#0F766E` (5,47:1 com branco); laranja `#EA580C` só com texto escuro `#0F172A` (5,02:1) — nunca texto branco (3,56:1).

Detalhe de tokens: [`ui-v2-fundacao-visual.md`](ui-v2-fundacao-visual.md).

---

## Decisões do épico (fechadas com o usuário)

1. Direção visual = recomendação da skill (flat moderno, teal + laranja, Plus Jakarta Sans), com o ajuste de contraste acima.
2. Ícones = `@phosphor-icons/react` (única dependência nova do épico).
3. UX = **UI otimista com rollback** + toast de erro; usar a resposta do endpoint em vez de refetch; paralelizar cargas.
4. Criar/editar **tarefa** = **drawer lateral** sobre a lista, mantendo a URL (`/tarefas/nova`, `/tarefas/:id`). Projetos: criação em drawer; `/projetos/:id` segue como hub (#46).
5. Trabalho fatiado em vários planos, executados em ordem.
6. **Sem cache** (regra do projeto): nada de TanStack Query/SWR; estado local + hooks próprios. Otimismo ≠ cache: só antecipa a resposta da mutação em curso.
7. Sem endpoints novos e sem mudança de contrato de API em todo o épico.
8. Sem biblioteca de componentes (Radix/MUI/shadcn); primitives próprias sobre `<dialog>` nativo.

---

## Slices (ordem)

| Ordem | Plano | Depende de | Entrega |
|------:|-------|------------|---------|
| 1 | [`ui-v2-fundacao-visual.md`](ui-v2-fundacao-visual.md) | — | Tokens novos (claro/escuro), fonte, ícones, botões/campos/cards/badges/motion |
| 2 | [`ui-v2-shell-nav.md`](ui-v2-shell-nav.md) | 1 | Sidebar/bottom bar com ícones, topo com título + “Nova tarefa” + XP, `PageHeader` |
| 3 | [`ui-v2-hoje-fluida.md`](ui-v2-hoje-fluida.md) | 1, 2 | Home sem cascata, ações otimistas, bloco ativo em destaque, menos modais |
| 4 | [`ui-v2-tarefas-drawer.md`](ui-v2-tarefas-drawer.md) | 1, 2 | Drawer de criar/editar, filtros instantâneos, ações otimistas na lista |
| 5 | [`ui-v2-capacidade.md`](ui-v2-capacidade.md) | 1, 2 | Grade semanal com stepper, barra de salvar fixa, ações otimistas |
| 6 | [`ui-v2-telas-secundarias.md`](ui-v2-telas-secundarias.md) | 1, 2, 4 (drawer) | Calendário, Histórico, Perfil, Login/Cadastro, criação de projeto em drawer |

```text
fundacao-visual → shell-nav → hoje-fluida
                            ↘ tarefas-drawer → telas-secundarias
                            ↘ capacidade
```

`ui-projetos-experiencia.md` (#46) continua válido; executar **depois** de `ui-v2-fundacao-visual.md` para já nascer no visual novo. O drawer de criação de projeto entra em `ui-v2-telas-secundarias.md`.

---

## Critério de pronto do épico

- Nenhuma tela usa a paleta bege, Fraunces ou fundo com gradiente.
- Navegação e ações principais têm ícone + texto (ou ícone com nome acessível).
- Home: no máximo **2 rodadas** de requisição no carregamento inicial; iniciar/concluir/abortar/remover dão feedback visual **antes** da resposta do servidor.
- Criar/editar tarefa não tira o usuário da lista.
- Contraste ≥ 4,5:1 para texto normal nos dois temas (teste automatizado sobre os tokens).
- `prefers-reduced-motion` respeitado em toda animação nova.
- `npm run build`, `npm run lint` e `npm test` verdes ao fim de cada slice.
