# Tasks

## 1. Persistência e API Decidir

- [x] 1.1 Criar migration Flyway (`V19` ou próximo) com `item_decidir` + `decidir_tag`, índices `(usuario_id, tarefa_id)` e `(usuario_id, decidir_ate)`; verificar app sobe e migration aplica limpa
- [x] 1.2 Implementar package `decidir` (entity/repository/DTOs/service/controller `/decidir`: GET lista default abertos, GET promovidos, POST, GET id, PATCH só aberto, DELETE hard); verificar IT: create só título, list só próprias abertas, PATCH em aberto, DELETE some, 404 de outro usuário
- [x] 1.3 Endpoint `POST /decidir/{id}/promover` criando tarefa via service de tarefa (título + cópia descrição; duração/prazo/prioridade opcionais; herda projeto; seta `tarefaId`); verificar IT: promover mínimo, rejeitar se já promovido, PATCH em promovido rejeitado
- [x] 1.4 Documentar prefixo `/decidir` em `vite.config.ts`, `nginx.conf` e `API_PROXY.md` (SPA+API shared); verificar checklist do `API_PROXY.md` consistente nos três

## 2. Tags, projeto e hooks

- [x] 2.1 Estender `TagVinculoService` + hard delete de tag para `decidir_tag`; create/update/list Decidir com `tagIds`/`tags` e filtro `tagId` + matchScore E→OU; verificar IT: vínculo, filtro união, E antes de OU, apagar tag remove vínculos Decidir
- [x] 2.2 No soft delete de tarefa, zerar `tarefaId` dos itens Decidir linkados (restore não religa); verificar IT: soft delete reabre item; restore mantém `tarefaId` null
- [x] 2.3 No arquivar projeto, zerar `projetoId` nos itens Decidir daquele projeto; verificar IT: item permanece com `projetoId` null

## 3. UI Decidir

- [x] 3.1 Client `decidirApi` + rota `/decidir` + item **Decidir** na nav principal (`AppShell` sidebar + bottom) e `documentTitle`; verificar testes de shell (link Decidir presente e ordem) e título da rota
- [x] 3.2 Página `/decidir`: captura por título, lista de abertos, empty state, destaque visual de `decidirAte` vencido; verificar testes de página (create na lista, empty state, destaque vencido)
- [x] 3.3 Ações promover (dialog/drawer com campos opcionais de tarefa) e descartar com confirmação; visão “Já decididos” somente leitura; verificar testes: promover remove da fila aberta; descartar some; promovidos não editáveis
- [x] 3.4 Picker de tags + projeto opcional + filtro por tags na URL; chips na lista/detalhe; verificar testes de filtro/URL e associação de tag

## 4. Integração

- [x] 4.1 Rodar suíte backend relevante (decidir + tag + soft delete tarefa + arquivar projeto) e suíte frontend (shell + página Decidir); corrigir regressões desta change

## Workflow follow-up

- Archive a change após review/implementação (`/openspec-archive-change`).
- Fechar a issue #50 quando o comportamento estiver em main.
