# Proposal

## Why

Tarefas, hábitos e projetos só se classificam por projeto (quando há) e prioridade/status. Falta uma taxonomia transversal — no estilo TickTick — para rotular itens ortogonais ao container “projeto” e filtrar a lista com relevância. A issue [#51](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/51) pede tags nos três tipos + filtro.

## What Changes

- Catálogo de tags por usuário: `nome` (único) + `cor`; sem hierarquia neste corte.
- Vínculo N:N independente em **tarefa**, **hábito** e **projeto** (sem herança projeto → tarefa/hábito).
- CRUD do catálogo (`/tags`); create inline no picker com cor default; renomear/cor/apagar na gestão.
- Apagar tag = **hard delete** + remove todos os vínculos (com confirmação na UI).
- Filtro por tags nas listagens: união dos matches; ordenação **match E** (todas as tags pedidas) antes de **match OU** (alguma); na lista de tarefas, essa prioridade vale **dentro** de cada grupo de prazo existente.
- UI: página `/tags` (acessível via “Mais” / nav secundária) + atalho de gerenciar no picker; chips nos formulários/cards; filtro nas listas de tarefa, hábito e projeto.
- Proxy front: novo prefixo `/tags` em Vite, Nginx e `API_PROXY.md`.

Fora de escopo: hierarquia de tags; sync TickTick; herança de tags do projeto; soft delete de tag; tags no planner/calendário/MCP neste corte; smart lists só-por-tag além do filtro nas listas atuais.

## Capabilities

### New Capabilities

- `tag`: catálogo de tags do usuário, vínculos N:N com tarefa/hábito/projeto, filtro e ordenação por relevância de match nas APIs de listagem.
- `ui-tag`: gestão `/tags`, picker (criar inline + editar), chips, filtro nas listas e ordenação E→OU nos grupos de prazo das tarefas.

### Modified Capabilities

- (nenhuma — specs existentes não cobrem classificação por tags)

## Impact

- Backend: novo módulo `tag` (entity/migration/controller/service); tabelas de vínculo; extensão de DTOs/filtros de `Tarefa`, `Habito`, `Projeto`; Flyway após `V17`.
- Frontend: API client, página Tags, MenuMais/sidebar, drawers/forms de tarefa/hábito/projeto, filtros URL, `listaGrupos` (prioridade de match), CSS/testes.
- Proxy: `vite.config.ts`, `nginx.conf`, `API_PROXY.md` — prefixo `/tags`.
- Issue #51: fecha os quatro checkboxes do body.
