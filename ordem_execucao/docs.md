# Ordem dos planos (MVP)

Guia para saber **qual plano criar / executar em seguida**.  
Referência de **tamanho e formato** de um plano: [`usuario.md`](usuario.md).

---

## Como usar este arquivo

1. Siga a coluna **Ordem** (dependências já estão embutidas).
2. Só avance para o próximo item quando o plano anterior estiver **feito** (ou conscientemente adiado).
3. Cada item da tabela = **um arquivo de plano** em `plans/` (um slice, no estilo de `usuario.md`).
4. Ao criar um plano novo, siga a skill [`.agents/criar_plano/SKILL.md`](../.agents/criar_plano/SKILL.md) (estrutura + granularidade de B*/F*). Referência viva: `plans/planner.md`.

---

## Tamanho esperado de um plano

Um plano deve caber em **um domínio / um slice vertical**, não no produto inteiro.

| Parte do plano | O que incluir |
|----------------|---------------|
| Escopo | O que entra **e** o que fica fora deste slice |
| Entity | Campos e tipos (tabela curta) |
| Back-end | Endpoints + regras de negócio fechadas |
| Front-end | Telas / fluxos mínimos do slice |
| Decisões | Lista curta do que já foi decidido na revisão |
| Testes | Mínimo obrigatório (unitário / API / job) — o que **não pode faltar** |
| Atividades | **B1…Bn** / **F1…Fn** finas: B1 = entities+Flyway; motor separado; **no máx. 1 endpoint novo por B*** |

**Não** misturar no mesmo plano, por exemplo, cadastro de tarefa + motor do planner + XP.  
Muitas atividades finas são ok; o que manda partir o arquivo é **escopo de produto** largo, não a contagem de B*.

---

## Ordem de execução

| Ordem | Plano (arquivo) | Status | Dependências | Por que nesta posição |
|------:|-----------------|--------|--------------|------------------------|
| 1 | [`usuario.md`](usuario.md) | feito | — | Auth mínima; base para qualquer dado por usuário |
| 2 | `tarefa.md` | feita | usuário | Entrada do produto (título, prazo, duração, prioridade, status) |
| 3 | `capacidade.md` | feito | usuário | Disponibilidade por dia; entrada obrigatória do planner |
| 4 | [`planner.md`](../plans/planner.md) | pendente (revisado) | tarefa, capacidade | Motor dia D + blocos + score + versões + ajuste/aprovação |
| 5 | [`execucao.md`](../plans/execucao.md) | pendente (revisado) | planner (aprovado) | Iniciar / acompanhar / concluir / abortar blocos do plano aprovado |
| 6 | `xp.md` | pendente | execução | Gamificação leve ao concluir (fórmula ainda aberta) |
| 7 | `historico.md` | pendente | execução, xp | Progresso do dia / semana (tarefas, tempo, XP) |

Fluxo resumido:

```text
usuario → tarefa → capacidade → planner (+ aprovação) → execucao → xp → historico
```

`aprovacao-plano.md` **não** é slice separado no MVP: ajuste e aprovação ficam em `planner.md`.

---

## Próximo passo agora

**Implementar / seguir** [`plans/planner.md`](../plans/planner.md) (quarto slice), se ainda não estiver feito.  
Plano do quinto slice já revisado: [`plans/execucao.md`](../plans/execucao.md) (após planner aprovado).

---

## Fora desta fila (MVP)

Não abrir plano só por estes itens, a menos que o núcleo de planejamento já esteja sólido:

- calendário completo, notificações avançadas, IA no planner;
- recuperação de senha / confirmação de e-mail;
- equipes, rede social, hábitos complexos, RPG completo.

Regra da visão: se não melhora “tarefas → plano útil”, questionar.

---

## Como atualizar este docs

- Ao **criar** um plano: troque “próximo a criar” / “pendente” e aponte o arquivo.
- Ao **concluir** um slice: marque **feito** e mova o destaque “próximo” para a linha seguinte.
- Se um plano for **partido** (ex.: `planner-v1` e `replanejamento`), insira linhas novas preservando a ordem lógica.
