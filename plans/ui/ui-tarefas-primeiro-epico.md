# Épico — Tarefas primeiro (TickTick-like) · planner sob demanda

Índice do épico (não executável sozinho). Cada slice tem plano próprio.  
Posicionamento de superfície: o app passa a ser **centrado em tarefas e projetos**; o planner deixa de ser o destino diário e vira **assistente sob demanda**.

---

## Problema

Hoje a hierarquia e a rota inicial empurram o usuário para **Hoje/plano** (`/` = agenda do dia). Tarefas e Projetos existem, mas competem com o planner como “produto principal”.  
O pedido: experiência de **tarefas no espírito TickTick** (lista como casa + criar com pouco atrito) e planner como **complemento**.

---

## Referência TickTick (corte fechado)

Entra neste épico **só**:

1. **Lista como casa** — após login o usuário cai em tarefas, não no plano do dia.
2. **Criar com título** — campo inline (ou barra mínima): digita o título e confirma; demais campos usam defaults e podem ser editados depois no drawer.
3. **Hierarquia de nav** — Tarefas e Projetos em destaque; Plano/Hábitos em segundo plano; Capacidade/Calendário/Histórico no terciário.

Fora da referência TickTick neste épico: Kanban, tags avançadas, Pomodoro, smart lists novas além das já existentes, Inbox separado, drag-and-drop, sync externo.

---

## Posicionamento (superfície vs diferencial)

| Camada | Papel |
|--------|--------|
| Superfície (IA / nav / home) | Tarefas + Projetos |
| Diferencial (motor) | Planner continua existindo — gera/ajusta plano quando o usuário **pede** |
| Gamificação | Inalterada (XP leve) |

Isso **não** apaga o motor: muda onde o produto “mora” no dia a dia. A visão em `assistente_planejamento_rotina_gamificado.md` deve ganhar um parágrafo de alinhamento quando este épico for priorizado (sem reescrever o documento inteiro neste slice).

---

## Decisões do épico (fechadas com o usuário)

1. **Rota inicial** = `/tarefas` (não mais `/` como agenda).
2. **Nav**  
   - Primário: Tarefas, Projetos  
   - Secundário: Plano, Hábitos  
   - Terciário: Capacidade, Calendário, Histórico (menu “Mais” / grupo inferior na sidebar)
3. **TickTick** = UI da lista + criar só com título (não smart lists novas nem features extras).
4. **Planner** = sob demanda: usuário vai a Plano / aciona CTA a partir de tarefas (ou capacidade); **não** é o destino diário padrão.
5. Formato = este índice + slices abaixo.
6. **Sem cache**; reutilizar shell v2, drawer de tarefa, quick-add context e APIs existentes (salvo defaults de create no slice de negócio).
7. Rota do plano do dia: **`/plano`** (conteúdo atual de `HomePage`); `/` redireciona para `/tarefas`.

---

## Slices (ordem)

| Ordem | Plano | Depende de | Entrega |
|------:|-------|------------|---------|
| 1 | [`ui-ia-nav-tarefas-primeiro.md`](ui-ia-nav-tarefas-primeiro.md) | shell v2 | Landing `/tarefas`, hierarquia de nav, `/` → `/tarefas`, plano em `/plano`, atalhos/labels |
| 2 | [`../negocio/tarefa-criar-so-titulo.md`](../negocio/tarefa-criar-so-titulo.md) | `tarefa.md` (+ minutos / sem prazo) | `POST /tarefas` aceita só `titulo` com defaults |
| 3 | [`ui-criar-tarefa-titulo.md`](ui-criar-tarefa-titulo.md) | 1, 2; drawer v2 | Campo inline estilo TickTick + quick-add mínimo |
| 4 | [`ui-planner-sob-demanda.md`](ui-planner-sob-demanda.md) | 1 | CTAs “Organizar meu dia” na lista; Plano como destino explícito; copy sem “casa do produto” |

```text
ui-ia-nav-tarefas-primeiro
        ↓
tarefa-criar-so-titulo → ui-criar-tarefa-titulo
        ↓
ui-planner-sob-demanda
```

`ui-planner-sob-demanda` pode começar em paralelo a 2–3 **depois** do slice 1 (precisa da rota `/plano` e da nav). Preferência serial: 1 → 2 → 3 → 4.

---

## Critério de pronto do épico

- Login / `*` autenticado levam a `/tarefas`, não à agenda.
- Bottom bar e sidebar refletem primário / secundário / terciário acima.
- Dá para criar tarefa **só com título** (Enter) e ver o item na lista; drawer permanece para detalhe.
- Não há fluxo que force o usuário a abrir o plano para “usar o app”; gerar/revisar plano é ação opcional com CTA claro.
- Testes dos quatro slices verdes; `npm test` / build front e ITs de tarefa do slice 2 verdes.
