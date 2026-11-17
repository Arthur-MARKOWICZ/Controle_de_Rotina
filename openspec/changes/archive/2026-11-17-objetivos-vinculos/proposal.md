# Proposal

## Why

O objetivo fica solto: não se liga ao que o usuário executa. Sem esse vínculo a #93 não tem conjunto para calcular progresso, e a #92 não tem o que mostrar no detalhe. A API de cadastro (#89) já existe nesta branch.

## What Changes

- Relação **N:N** entre objetivo e tarefa, hábito e projeto (`objetivo_tarefa`, `objetivo_habito`, `objetivo_projeto`), no molde das tags.
- `objetivoIds` no create/PATCH de tarefa, hábito e projeto: presença substitui o conjunto (vazio limpa, ausente não mexe). Ids repetidos no array contam uma vez.
- **BREAKING:** respostas de tarefa, hábito e projeto passam a incluir `objetivos: ObjetivoResumo[]` com os vínculos **diretos** (lista vazia quando não há). Herança não entra nesse campo.
- Vínculo **novo** só em objetivo `ATIVO`. Concluído e arquivado mantêm os que já tinham; tentativa de incluir um não-ativo que ainda não estava no conjunto → 409 `"Objetivo não está ativo"`. Objetivo/item de outro usuário → 404. Subtarefa não vincula.
- **Herança só na leitura:** tarefa conta para os objetivos do seu projeto além dos diretos; se chegar pelos dois caminhos, conta uma vez e a origem é `DIRETO`.
- `GET /objetivos/{id}/itens` devolve os itens agrupados (`tarefas`, `habitos`, `projetos`) com origem `DIRETO` | `VIA_PROJETO`. Soft-deleted não aparece. Subtarefas não entram (nem como diretas, nem via projeto). Não é paginado.
- `contract/openapi.json` e `schema.d.ts` (web e MCP) regenerados.

Fora do escopo: UI (#92), progresso (#93), histórico (#95), filtro `?objetivoId=` nas listagens, ferramentas novas no MCP, itens Decidir, XP ao concluir objetivo.

## Capabilities

### New Capabilities
<!-- Nenhuma: o vínculo pertence à capability `objetivo` já criada pela #89. -->

### Modified Capabilities
- `objetivo`: passa a cobrir o vínculo N:N com tarefa, hábito e projeto, a herança via projeto na leitura e a listagem de itens do objetivo.

## Impact

- **Backend:** `ObjetivoVinculoService` no package `objetivo`; entidades/repositórios das três tabelas de join; `ObjetivoResumoResponse` e DTO de itens; `objetivoIds` / `objetivoIdsPresente` nos requests de tarefa, hábito e projeto; `objetivos` nas responses; `GET /objetivos/{id}/itens`; `ObjetivoNaoAtivoException`.
- **Banco:** migration `V25` com as três tabelas de vínculo.
- **API/contrato:** campos novos em create/update/response de tarefa, hábito e projeto; endpoint de itens; regen de `contract/openapi.json` e `schema.d.ts` (web e MCP).
- **Frontend UI, MCP tools, mobile:** sem feature nova. O mobile não precisa alterar modelos (são subset do contrato). A página `/objetivos` (#90) não usa vínculos.
