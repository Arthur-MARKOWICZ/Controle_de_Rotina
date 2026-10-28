# Ordem dos planos (MVP)

Guia para saber **qual plano criar / executar em seguida**.  
Referência de **tamanho e formato** de um plano: [`usuario.md`](../plans/negocio/usuario.md).

---

## Onde ficam os planos

| Pasta | Conteúdo |
|-------|----------|
| [`plans/negocio/`](../plans/negocio/) | Slices de domínio / API (entities, regras, endpoints) |
| [`plans/ui/`](../plans/ui/) | Slices de UI/UX (layout, design system, telas, a11y) |

Não misturar negócio e UI no mesmo arquivo: API/domínio em `negocio/`, experiência visual em `ui/`.

---

## Como usar este arquivo

1. Siga a coluna **Ordem** (dependências já estão embutidas).
2. Só avance para o próximo item quando o plano anterior estiver **feito** (ou conscientemente adiado).
3. Cada item da tabela = **um arquivo de plano** em `plans/negocio/` ou `plans/ui/` (um slice, no estilo de `usuario.md`).
4. Ao criar um plano novo, siga a skill [`.agents/criar_plano/SKILL.md`](../.agents/criar_plano/SKILL.md) (estrutura + granularidade de B*/F*). Referência viva: `plans/negocio/planner.md`.

---

## Tamanho esperado de um plano

Um plano deve caber em **um domínio / um slice vertical**, não no produto inteiro.

| Parte do plano | O que incluir |
|----------------|---------------|
| Escopo | O que entra **e** o que fica fora deste slice |
| Entity | Campos e tipos (tabela curta) — planos de `negocio/` |
| Back-end | Endpoints + regras de negócio fechadas — planos de `negocio/` |
| Front-end | Telas / fluxos — mínimo no slice de negócio; detalhe visual em `ui/` |
| Decisões | Lista curta do que já foi decidido na revisão |
| Testes | Mínimo obrigatório (unitário / API / job) — o que **não pode faltar** |
| Atividades | **B1…Bn** / **F1…Fn** finas: B1 = entities+Flyway; motor separado; **no máx. 1 endpoint novo por B*** |

**Não** misturar no mesmo plano, por exemplo, cadastro de tarefa + motor do planner + XP.  
Muitas atividades finas são ok; o que manda partir o arquivo é **escopo de produto** largo, não a contagem de B*.

---

## Ordem de execução (negócio / API)

| Ordem | Plano (arquivo) | Status | Dependências | Por que nesta posição |
|------:|-----------------|--------|--------------|------------------------|
| 1 | [`usuario.md`](../plans/negocio/usuario.md) | feito | — | Auth mínima; base para qualquer dado por usuário |
| 2 | [`tarefa.md`](../plans/negocio/tarefa.md) | feita | usuário | Entrada do produto (título, prazo, duração, prioridade, status) |
| 3 | [`capacidade.md`](../plans/negocio/capacidade.md) | feito | usuário | Disponibilidade por dia; entrada obrigatória do planner |
| 4 | [`planner.md`](../plans/negocio/planner.md) | pendente (revisado) | tarefa, capacidade | Motor dia D + blocos + score + versões + ajuste/aprovação |
| 5 | [`execucao.md`](../plans/negocio/execucao.md) | feito | planner (aprovado) | Iniciar / acompanhar / concluir / abortar blocos do plano aprovado |
| 6 | [`xp.md`](../plans/negocio/xp.md) | feito (enrich HTTP + UI em `ui-xp-feedback.md`) | execução | Ledger + XP bloco/bônus tarefa + nível (curva 10×n) |
| 7 | [`historico.md`](../plans/negocio/historico.md) | feito | execução, xp | Resumo dia/semana (tarefas, blocos, tempo canônico, XP); agregação na leitura |

Fluxo resumido:

```text
usuario → tarefa → capacidade → planner (+ aprovação) → execucao → xp → historico
```

`aprovacao-plano.md` **não** é slice separado no MVP: ajuste e aprovação ficam em `planner.md`.

---

## Fila UI/UX (issue [#1](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/1))

Ordem alinhada à issue pai e subtarefas #2–#13. Um arquivo de plano por subtarefa em `plans/ui/`.

| Ordem | Plano | Issue | Status | Dependências | Por que nesta posição |
|------:|-------|------:|--------|--------------|------------------------|
| 8 | [`ui-layout-nav.md`](../plans/ui/ui-layout-nav.md) | [#2](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/2) | feito | usuário (rotas protegidas) | Shell único; base de navegação |
| 9 | [`ui-design-system.md`](../plans/ui/ui-design-system.md) | [#3](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/3) | feito | layout (ideal) | Tokens/botões/campos para o resto |
| 10 | [`ui-estados-feedback.md`](../plans/ui/ui-estados-feedback.md) | [#11](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/11) | feito | layout, design system | Toast/skeleton/confirm reutilizados depois |
| 11 | [`ui-home-agenda.md`](../plans/ui/ui-home-agenda.md) | [#5](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/5) | feito | planner, execução, estados | Núcleo “plano de hoje” |
| 12 | [`ui-revisao-rascunho.md`](../plans/ui/ui-revisao-rascunho.md) | [#6](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/6) | feito | home-agenda, estados | Diferencial: revisar e aprovar |
| 13 | [`ui-xp-feedback.md`](../plans/ui/ui-xp-feedback.md) | [#4](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/4) | feito | xp, layout, estados | Fecha F1–F2 + enrich `/me` e concluir |
| 14 | [`ui-quick-add.md`](../plans/ui/ui-quick-add.md) | [#7](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/7) | feito | tarefa, estados | Criação rápida |
| 15 | [`ui-lista-tarefas.md`](../plans/ui/ui-lista-tarefas.md) | [#8](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/8) | feito | quick-add, estados | Lista agrupada + URL + desfazer |
| 16 | [`ui-capacidade-visual.md`](../plans/ui/ui-capacidade-visual.md) | [#9](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/9) | feito | capacidade, home | Semana visual + ajuste do dia |
| 17 | [`ui-historico-grafico.md`](../plans/ui/ui-historico-grafico.md) | [#10](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/10) | feito | historico | Gráfico + vs período anterior |
| 18 | [`ui-a11y-atalhos.md`](../plans/ui/ui-a11y-atalhos.md) | [#12](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/12) | feito | telas acima | Polimento a11y/atalhos no fim |
| 19 | [`ui-auth-onboarding.md`](../plans/ui/ui-auth-onboarding.md) | [#13](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/13) | feito | auth + capacidade/tarefa/planner + quick-add | Login/perfil + primeiro acesso |

```text
layout → design-system → estados
  → home-agenda → revisao-rascunho
  → xp-feedback
  → quick-add → lista-tarefas
  → capacidade-visual → historico-grafico
  → a11y-atalhos → auth-onboarding
```

---

## Próximo passo agora

Fila de domínio MVP: histórico e XP (ledger + enrich HTTP/UI) feitos.

Fila UI/UX do MVP (**#2–#13**): **concluída**.

---

## Fora desta fila (MVP)

Não abrir plano só por estes itens, a menos que o núcleo de planejamento já esteja sólido:

- calendário completo, notificações avançadas, IA no planner;
- recuperação de senha / confirmação de e-mail;
- equipes, rede social, hábitos complexos, RPG completo.

Regra da visão: se não melhora “tarefas → plano útil”, questionar.

---

## Como atualizar este docs

- Ao **criar** um plano: salve em `plans/negocio/` ou `plans/ui/`, troque “próximo a criar” / “pendente” e aponte o arquivo.
- Ao **concluir** um slice: marque **feito** e mova o destaque “próximo” para a linha seguinte.
- Se um plano for **partido** (ex.: `planner-v1` e `replanejamento`), insira linhas novas preservando a ordem lógica.
