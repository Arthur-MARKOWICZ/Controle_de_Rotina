# Ordem dos planos (MVP)

Guia para saber **qual plano criar / executar em seguida**.  
Referência de **tamanho e formato** de um plano: [`usuario.md`](usuario.md).

---

## Como usar este arquivo

1. Siga a coluna **Ordem** (dependências já estão embutidas).
2. Só avance para o próximo item quando o plano anterior estiver **feito** (ou conscientemente adiado).
3. Cada item da tabela = **um arquivo de plano** em `plans/` (um slice, no estilo de `usuario.md`).
4. Ao criar um plano novo, espelhe a estrutura de `usuario.md`: escopo / fora de escopo → entity → back → front → decisões → atividades B*/F* com ordem.

---

## Tamanho esperado de um plano (espelho de `usuario.md`)

Um plano deve caber em **um domínio / um slice vertical**, não no produto inteiro.

| Parte do plano | O que incluir |
|----------------|---------------|
| Escopo | O que entra **e** o que fica fora deste slice |
| Entity | Campos e tipos (tabela curta) |
| Back-end | Endpoints + regras de negócio fechadas |
| Front-end | Telas / fluxos mínimos do slice |
| Decisões | Lista curta do que já foi decidido na revisão |
| Atividades | Poucas fases (**B1…Bn**, **F1…Fn**), cada uma implementável numa sessão |

**Não** misturar no mesmo plano, por exemplo, cadastro de tarefa + motor do planner + XP.  
Se o escopo passar de ~3 atividades de back + ~3 de front, **quebrar** em outro plano.

---

## Ordem de execução

| Ordem | Plano (arquivo) | Status | Dependências | Por que nesta posição |
|------:|-----------------|--------|--------------|------------------------|
| 1 | [`usuario.md`](usuario.md) | feito | — | Auth mínima; base para qualquer dado por usuário |
| 2 | `tarefa.md` | feita | usuário | Entrada do produto (título, prazo, duração, prioridade, status) |
| 3 | `capacidade.md` | pendente | usuário | Disponibilidade por dia; entrada obrigatória do planner |
| 4 | `planner.md` | pendente | tarefa, capacidade | Motor determinístico: blocos + distribuição + conflitos |
| 5 | `aprovacao-plano.md` | pendente | planner | Usuário aprova / ajusta; sem reorganização silenciosa |
| 6 | `execucao.md` | pendente | aprovação | Iniciar / acompanhar / concluir blocos do plano aprovado |
| 7 | `xp.md` | pendente | execução | Gamificação leve ao concluir (fórmula ainda aberta) |
| 8 | `historico.md` | pendente | execução, xp | Progresso do dia / semana (tarefas, tempo, XP) |

Fluxo resumido:

```text
usuario → tarefa → capacidade → planner → aprovacao-plano → execucao → xp → historico
```

---

## Próximo passo agora

**Criar `plans/tarefa.md`**, no mesmo espírito de `usuario.md`.

Sugestão de escopo mínimo (ajustar na revisão do plano):

- CRUD de tarefas do usuário autenticado (`/me` implícito).
- Campos do MVP: título, descrição, prazo, duração estimada, prioridade, status.
- Fora deste slice: quebra em blocos, planner, recorrência, XP.

Capacidade vem em seguida porque o planner precisa das duas entradas (tarefas + horas disponíveis).

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
