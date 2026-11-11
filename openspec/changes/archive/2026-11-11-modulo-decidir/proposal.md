# Proposal

## Why

Nem tudo que o usuário captura já é uma tarefa planejável (duração, prazo, execução). A issue [#50](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/50) pede um módulo **Decidir**: itens ambíguos (ex. “comprar assinatura”, “verificar se X funciona”) que precisam de uma decisão antes de entrar no motor de planejamento. Sem isso, a lista de tarefas vira depósito de coisas ainda não clarificadas.

## What Changes

- Novo domínio **Decidir**: item com `titulo` obrigatório; `descricao` e `decidirAte` opcionais; `projetoId` opcional; tags N:N do catálogo existente.
- Ciclo de vida sem status `DESCARTADO`: aberto = `tarefaId` null; promovido = `tarefaId` preenchido (somente leitura); descartar = **hard delete**.
- **Promover** cria uma `Tarefa` (híbrido: título + cópia da descrição; duração/prazo/prioridade opcionais como no create de tarefa), copia tags, herda `projetoId`, grava `tarefaId` no item.
- Soft delete da tarefa linkada **reabre** o item (`tarefaId` → null); item volta à fila.
- Projeto removido/arquivado (conforme regra do projeto) **zera** `projetoId` no item; não apaga o item.
- `decidirAte` vencido: só ordenação/destaque visual; sem status novo.
- API autenticada em `/decidir` + UI mínima em `/decidir` com item na **nav principal** (“Decidir”).
- Proxy Vite/Nginx/`API_PROXY.md` para o prefixo `/decidir` (SPA + API no mesmo path, padrão Accept como `/tags`).
- Extensão do módulo de tags: vínculo e hard delete também cobrem itens Decidir; filtro/ordenação E→OU na listagem de Decidir.

Fora de escopo: planner/execução/XP direto no item; snooze; MCP; sync externo; herança de tags do projeto; soft delete do item Decidir; issue #59 (pendência tarefas↔projetos) como feature separada.

## Capabilities

### New Capabilities

- `decidir`: CRUD de itens para decidir, promover → tarefa, descartar (hard delete), reabrir ao soft-delete da tarefa, vínculo opcional a projeto, listagem abertos vs promovidos.
- `ui-decidir`: página `/decidir` na nav principal — captura, lista de abertos, promover/descartar, tags/projeto, destaque de `decidirAte` vencido, acesso a já decididos.

### Modified Capabilities

- `tag`: vínculos N:N e hard delete passam a incluir itens Decidir; listagem de Decidir aceita filtro/ordenação por tags no mesmo contrato das demais listagens.

## Impact

- Backend: package `decidir` (entity/migration/controller/service); tabela de vínculo `decidir_tag` (ou equivalente); extensão de `TagVinculoService` / hard delete de tag; hook no soft delete de `Tarefa` para reabrir item; limpeza de `projetoId` quando o projeto some/arquiva; Flyway após `V18`.
- Frontend: rota `/decidir`, item na nav (`AppShell`), API client, lista + ações promover/descartar, reuso do picker de tags e seletor de projeto; testes de shell/nav.
- Proxy: `vite.config.ts`, `nginx.conf`, `API_PROXY.md`.
- Issue #50: fecha o módulo pedido após implementação.
