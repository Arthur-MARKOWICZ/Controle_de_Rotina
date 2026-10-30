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

## Pós-MVP — endurecimento (issues [#14](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/14)–[#19](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/19))

Contrato de erro, segurança de sessão, concorrência, testes de núcleo e jobs resilientes. Rodar **antes** do épico de regras do planner (#20).

| Ordem | Plano (arquivo) | Issue | Status | Dependências | Por que nesta posição |
|------:|-----------------|------:|--------|--------------|------------------------|
| 20 | [`api-problem-detail.md`](../plans/negocio/api-problem-detail.md) | [#14](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/14) | feito | MVP domínio | Base de 409/validação para o resto |
| 21 | [`seguranca-jwt-cookie.md`](../plans/negocio/seguranca-jwt-cookie.md) | [#15](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/15) | feito | usuario | Segredo/cookie/`iss`/`aud` |
| 22 | [`seguranca-refresh-token.md`](../plans/negocio/seguranca-refresh-token.md) | [#16](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/16) | feito | #15 | Reuso de refresh + limpeza |
| 23 | [`concorrencia-escritas.md`](../plans/negocio/concorrencia-escritas.md) | [#17](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/17) | feito | #14 | Corridas cadastro/plano/execução/XP |
| 24 | [`testes-nucleo-unitario.md`](../plans/negocio/testes-nucleo-unitario.md) | [#18](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/18) | feito | xp, jwt (#15 ideal) | Unitários sem Testcontainers |
| 25 | [`job-geracao-atraso.md`](../plans/negocio/job-geracao-atraso.md) | [#19](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/19) | feito | planner, #17 ideal | Jobs paginados + lock + fuso |

```text
problem-detail → jwt-cookie → refresh-token
              ↘ concorrencia → testes-nucleo
                            ↘ job-geracao-atraso
```

---

## Pós-MVP — regras do planner (épico [#20](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/20))

Índice do épico (não executável sozinho): [`planner-regras-epico.md`](../plans/negocio/planner-regras-epico.md).  
Ordem = subtarefas #21–#28 (igual à issue pai).

| Ordem | Plano (arquivo) | Issue | Status | Dependências | Por que nesta posição |
|------:|-----------------|------:|--------|--------------|------------------------|
| 26 | [`planner-saldo-restante.md`](../plans/negocio/planner-saldo-restante.md) | [#21](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/21) | feito | planner, execução | Senão o horizonte multiplica trabalho já feito |
| 27 | [`planner-janela-multidia.md`](../plans/negocio/planner-janela-multidia.md) | [#22](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/22) | feito | #21, capacidade | Distribuição pelos dias |
| 28 | [`planner-conflito-prazo.md`](../plans/negocio/planner-conflito-prazo.md) | [#23](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/23) | feito | #22 | Aviso “não cabe até o prazo” |
| 29 | [`tarefa-duracao-minutos.md`](../plans/negocio/tarefa-duracao-minutos.md) | [#24](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/24) | feito | tarefa, #22 ideal | Minutos + teto/piso de bloco |
| 30 | [`tarefa-atraso-automatico.md`](../plans/negocio/tarefa-atraso-automatico.md) | [#25](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/25) | feito | tarefa, #19 ideal | Status ATRASADA no job diário |
| 31 | [`planner-replanejamento.md`](../plans/negocio/planner-replanejamento.md) | [#26](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/26) | feito | #22, execução | Proposta com aceite do usuário |
| 32 | [`tarefa-sem-prazo.md`](../plans/negocio/tarefa-sem-prazo.md) | [#27](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/27) | feito | tarefa, #22 | Prazo opcional + score/alocação |
| 33 | [`xp-tempo-efetivo.md`](../plans/negocio/xp-tempo-efetivo.md) | [#28](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/28) | feito | xp, execução | XP sem premiar estimativa inflada |

```text
saldo → janela-multidia → conflito-prazo → duracao-minutos
                      ↘ atraso-automatico
                      ↘ replanejamento
                      ↘ tarefa-sem-prazo → xp-tempo-efetivo
```

---

## Pós-MVP — calendário (issue [#38](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/38))

Leitura agregada do horizonte multi-dia (semana + mês), só plano aprovado + lente de prazos.

| Ordem | Plano (arquivo) | Issue | Status | Dependências | Por que nesta posição |
|------:|-----------------|------:|--------|--------------|------------------------|
| 34 | [`calendario/calendario.md`](../plans/negocio/calendario/calendario.md) | [#38](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/38) | pendente | planner (+ janela), capacidade, tarefa, shell/nav | Auditar onde caíram blocos e quando vencem prazos |

```text
núcleo MVP + regras planner → calendario (#38)
```

---

## Pós-MVP — projetos (issue [#40](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/40))

Agrupar tarefas, reservar minutos no template semanal (motor em duas fases) e, em seguida, link opcional GitHub + UI.

| Ordem | Plano (arquivo) | Issue | Status | Dependências | Por que nesta posição |
|------:|-----------------|------:|--------|--------------|------------------------|
| 35 | [`projetos/projetos.md`](../plans/negocio/projetos/projetos.md) | [#40](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/40) | pendente | tarefa, capacidade, planner (+ replanejamento); ideal após calendário | Núcleo: CRUD, vínculo, reserva, motor |
| 36 | [`projetos/github-link.md`](../plans/negocio/projetos/github-link.md) | [#40](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/40) | pendente | `projetos.md` | Link issue/PR sem OAuth/sync |
| 37 | [`ui-projetos.md`](../plans/ui/ui-projetos.md) | [#40](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/40) | pendente | `projetos.md`; ideal + `github-link.md` | Telas, filtro, reserva na capacidade, chip no plano |

```text
calendario (#38) → projetos (#40) → github-link → ui-projetos
```

---

## Próximo passo agora

Fila MVP (domínio + UI #2–#13): **concluída**.  
Pós-MVP endurecimento (#14–#19) e regras do planner (#21–#28): **concluída**.

**Próximo:** [`calendario/calendario.md`](../plans/negocio/calendario/calendario.md) (issue #38).  
Em seguida na fila: projetos (#40) — [`projetos/projetos.md`](../plans/negocio/projetos/projetos.md).

---

## Fora desta fila

Não abrir plano só por estes itens, a menos que o núcleo de planejamento já esteja sólido:

- sync de calendário externo (Google/Outlook), notificações avançadas, IA no planner;
- recuperação de senha / confirmação de e-mail;
- equipes, rede social, hábitos complexos, RPG completo.

Regra da visão: se não melhora “tarefas → plano útil”, questionar.

---

## Como atualizar este docs

- Ao **criar** um plano: salve em `plans/negocio/` ou `plans/ui/`, troque “próximo a criar” / “pendente” e aponte o arquivo.
- Ao **concluir** um slice: marque **feito** e mova o destaque “próximo” para a linha seguinte.
- Se um plano for **partido** (ex.: `planner-v1` e `replanejamento`), insira linhas novas preservando a ordem lógica.
