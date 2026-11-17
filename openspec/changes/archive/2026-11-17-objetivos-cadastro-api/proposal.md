# Proposal

## Why

O usuário não tem onde registrar o que quer alcançar (ex.: "perder 5 kg") e manter isso visível. Esta change cria a base do módulo de objetivos (issue #89, parte de #65): a entidade e a API `/objetivos`. Vínculo com tarefas/hábitos/projetos (#91), progresso (#93) e histórico (#95) dependem dela.

## What Changes

- Nova entidade **objetivo** do usuário: `titulo` (obrigatório, até 80), `descricao` opcional (até 500), `prazo` opcional (data, sem horário), `status` (`ATIVO` | `CONCLUIDO` | `ARQUIVADO`), `concluidoEm` e `arquivadoEm`.
- Novos endpoints em `/objetivos`: criar, listar (paginado, filtro por status, padrão `ATIVO`), detalhar, editar (PATCH parcial), concluir, arquivar e reabrir.
- Ciclo de vida explícito: o objetivo nunca se conclui sozinho. Concluir só a partir de `ATIVO`; arquivar a partir de `ATIVO` ou `CONCLUIDO` (mantém `concluidoEm`); arquivado não pode ser concluído sem reabrir; reabrir sempre volta a `ATIVO` e limpa as duas datas. Transição inválida → 409 com `title` específico.
- Edição permitida em qualquer status; `prazo: null` no PATCH remove o prazo; prazo no passado (BRT) rejeitado na criação e na edição quando o prazo muda.
- Prefixo `/objetivos` no proxy do front (`vite.config.ts`, `nginx.conf`, `API_PROXY.md`), compartilhado entre SPA e API.
- `contract/openapi.json` e `schema.d.ts` (web e MCP) regenerados.

Fora do escopo: tags, ordenação manual, exclusão, vínculo com outros itens, progresso, histórico e UI.

## Capabilities

### New Capabilities
- `objetivo`: cadastro de objetivos do usuário e seu ciclo de vida (ativo, concluído, arquivado, reabertura), com listagem paginada por status.

### Modified Capabilities
<!-- Nenhuma: nenhuma capability existente muda de requisito. -->

## Impact

- **Backend:** novo package `com.controlerotina.objetivo` (entity, repository, DTOs, service, controller); migration `V24`; novas exceções em `shared/exception`; reuso de `PrazoNoPassadoException`.
- **Banco:** nova tabela `objetivo` com FK para `usuario`.
- **API/contrato:** novo prefixo `/objetivos`; `contract/openapi.json`, `frontend/frontend-web/src/**/schema.d.ts` e `mcp/**/schema.d.ts` regenerados.
- **Front (infra apenas):** proxy em `vite.config.ts` e `nginx.conf` (exige rebuild da imagem Docker do frontend) e lista em `API_PROXY.md`.
