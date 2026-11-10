# Tasks

## 1. Persistência e catálogo

- [x] 1.1 Criar migration Flyway (`V18` ou próximo) com `tag` + `tarefa_tag` / `habito_tag` / `projeto_tag`, unicidade de nome por usuário (case-insensitive) e FKs; verificar app sobe e migration aplica limpa
- [x] 1.2 Implementar entity/repository/service/controller `/tags` (GET lista, POST, PATCH, DELETE hard + limpa vínculos) com auth por JWT; verificar testes de integração: create, list só próprias, nome duplicado 409, delete remove vínculos
- [x] 1.3 Documentar prefixo `/tags` em `vite.config.ts`, `nginx.conf` e `API_PROXY.md` (SPA+API shared); verificar checklist do `API_PROXY.md` consistente nos três

## 2. Vínculos e listagens (API)

- [x] 2.1 Estender create/update/response de tarefa com `tagIds` / `tags` (presença de campo no PATCH; batch enrich na listagem); verificar IT: criar com tags, patch substitui conjunto, listagem sem N+1 óbvio e com `tags` preenchidas
- [x] 2.2 Idem para hábito (create/update/list/get); verificar IT espelhando 2.1
- [x] 2.3 Idem para projeto (create/update/list/get); verificar IT + cenário de não-herança: filtrar tarefas por tag do projeto não inclui tarefa sem a tag
- [x] 2.4 Filtro `tagId`(s) + ordenação matchScore (E=2 antes de OU=1) em GET `/tarefas`, `/habitos`, `/projetos`; verificar IT: união, E antes de OU, uma tag = sem distinção de score

## 3. UI catálogo e picker

- [x] 3.1 Client `tagApi` + página `/tags` (listar, criar, editar nome/cor, apagar com confirmação) e link em `MenuMais`/sidebar; verificar testes de página e navegação para `/tags`
- [x] 3.2 Componente picker reutilizável (selecionar existentes, create inline com cor default, atalho Gerenciar); verificar testes unitários do picker
- [x] 3.3 Integrar picker + chips em formulário/drawer/card de tarefa; verificar testes de drawer/form e chip na lista
- [x] 3.4 Integrar picker + chips em hábito e projeto; verificar testes correspondentes

## 4. Filtro e ordenação na UI

- [x] 4.1 Filtro por tags na URL/listagem de tarefas (`listaUrl` + UI) e reordenação E→OU dentro de cada grupo de prazo (`listaGrupos`); verificar testes `listaUrl` / `listaGrupos` / página
- [x] 4.2 Filtro + ordenação E→OU nas listas de hábito e projeto; verificar testes das páginas

## 5. Integração

- [x] 5.1 Rodar suíte backend relevante (tag + tarefa/hábito/projeto) e suíte frontend (tags, picker, listas); corrigir regressões desta change

## Workflow follow-up

- Archive a change após review/implementação (`/openspec-archive-change`).
- Fechar checkboxes da issue #51 quando o comportamento estiver em main.
