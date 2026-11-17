# Tasks

Comandos a partir de `backend`, salvo quando indicado.

## 1. Persistência

- [x] 1.1 Criar `V25__create_objetivo_vinculo.sql` com `objetivo_tarefa`, `objetivo_habito` e `objetivo_projeto` (PK composta, FKs `ON DELETE CASCADE`, índice no lado do item, como no design); verificar que `mvn test -Dtest=ObjetivoIT` sobe o contexto e aplica a migration limpa
- [x] 1.2 Criar entidades `ObjetivoTarefa`, `ObjetivoHabito`, `ObjetivoProjeto` (`@IdClass`, só os dois UUIDs) e repositórios (`deleteAllByTarefaId` / `HabitoId` / `ProjetoId`, `findByTarefaIdIn` e equivalentes, `findByObjetivoId`); adicionar `findByUsuarioIdAndIdIn` em `ObjetivoRepository`; verificar compilação do módulo `objetivo`

## 2. Serviço de vínculo

- [x] 2.1 Criar `ObjetivoNaoAtivoException` (409, title `"Objetivo não está ativo"`) e `SubtarefaObjetivoException` (400, title `"Subtarefa não pode ser vinculada a objetivo"`) em `shared/exception`; verificar no teste do service o tipo concreto lançado
- [x] 2.2 Criar `ObjetivoResumoResponse` (`id`, `titulo`, `status`), enum `OrigemVinculoObjetivo` (`DIRETO`, `VIA_PROJETO`) e DTOs `ObjetivoItensResponse` / item de tarefa (`projetoId` e `projetoNome` `@Nullable`) / hábito / projeto; verificar que os records de item não aceitam `objetivos` nulos (lista vazia)
- [x] 2.3 Implementar `ObjetivoVinculoService.substituirObjetivosTarefa|Habito|Projeto` (LinkedHashSet, 404 se id de outro usuário, 409 só em `incoming - atuais` não `ATIVO`, delete-all + insert); verificar `ObjetivoVinculoServiceTest`: ≥ 1 feliz (dois objetivos na tarefa), ≥ 3 anomalias (ids repetidos → uma linha; reenviar o mesmo conjunto; reenviar objetivo já ligado depois de concluído; `[]` limpa sem erro) e ≥ 3 erros (`ObjetivoNaoEncontradoException`, `ObjetivoNaoAtivoException` em concluído novo, `ObjetivoNaoAtivoException` em arquivado novo) — estado inalterado nos erros
- [x] 2.4 Implementar `listarItens` / conjunto canônico (diretos filtrados por soft delete e `parent_id` nulo; tarefas raiz dos projetos ligados, dedupe com `DIRETO` ganhando; hábitos não herdam); verificar no mesmo teste: três tipos DIRETO; VIA_PROJETO; DIRETO + VIA_PROJETO → uma entrada DIRETO; subtarefa do projeto ausente; hábito não herda; soft-deleted ausente e linha de join intacta; objetivo inexistente → `ObjetivoNaoEncontradoException`

## 3. Tarefa, hábito e projeto

- [x] 3.1 Adicionar `objetivoIds` opcional nos `Criar*Request` e `objetivoIds` + `objetivoIdsPresente` (hidden) nos `Atualizar*Request` de tarefa, hábito e projeto, no desserializador ao lado de `tagIds` (`null` JSON → lista vazia); verificar testes dos desserializadores: campo ausente, `[]`, `null`, lista com ids
- [x] 3.2 Incluir `List<ObjetivoResumoResponse> objetivos` (nunca nulo) em `TarefaResponse`, `HabitoResponse` e `ProjetoResponse`; overloads de `from` que não recebem objetivos passam `List.of()`; corrigir testes/records que quebrarem; verificar `TarefaResponseTest` (e equivalentes): item sem vínculo → lista vazia; nulo vira vazia
- [x] 3.3 Em `TarefaService`, recusar create/PATCH de subtarefa com `objetivoIds` não vazio (`SubtarefaObjetivoException`) **antes** de `substituir*`; no create e no PATCH com `objetivoIdsPresente` chamar `substituirObjetivosTarefa`; na listagem/detalhe usar `mapearObjetivosPorTarefa`; verificar `TarefaServiceTest` + `TarefaIT`: create com dois objetivos; PATCH substitui; PATCH sem o campo mantém; subtarefa com ids → 400 e nenhum vínculo; listagem traz só o direto (não o do projeto); objetivo de outro usuário → 404 sem persistir a tarefa
- [x] 3.4 Espelhar create/PATCH/listagem em `HabitoService` e `ProjetoService`; verificar `HabitoIT` / `ProjetoIT`: PATCH `objetivoIds: []` limpa; ids repetidos no create de projeto → um vínculo; desvincular de objetivo arquivado → 200

## 4. Endpoint de itens

- [x] 4.1 Expor `GET /objetivos/{id}/itens` no `ObjetivoController` delegando a `ObjetivoVinculoService.listarItens` (404 pelo dono); verificar que o path não exige proxy novo (`API_PROXY.md` já tem `/objetivos`)
- [x] 4.2 Criar `ObjetivoVinculoIT` cobrindo os cenários HTTP da spec: 200 com os três grupos; vazio; VIA_PROJETO; DIRETO ganha da herança; soft delete some e restaurar volta; arquivar objetivo mantém os itens; objetivo de outro usuário → 404 `$.title` "Objetivo não encontrado"; subtarefa com `objetivoIds` → 400; ligar a concluído → 409 "Objetivo não está ativo" sem alterar o banco

## 5. Contrato OpenAPI

- [x] 5.1 Regenerar `contract/openapi.json` (`cd backend && mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true`); verificar `objetivos` required em `TarefaResponse` / `HabitoResponse` / `ProjetoResponse`, `objetivoIds` opcional nos creates/PATCHes, `objetivoIdsPresente` ausente, `projetoId`/`projetoNome` anuláveis no item de tarefa, endpoint `/objetivos/{id}/itens`
- [x] 5.2 Regenerar `frontend/frontend-web/src/api/schema.d.ts` (`npm run gerar:contrato`) e `mcp/src/schema.d.ts` (`pnpm gerar:contrato`); verificar `npm run build` no front e `pnpm build` no MCP. Não alterar modelos Kotlin

## 6. Integração

- [x] 6.1 Rodar `cd backend && mvn test`; verificar suíte verde e gate JaCoCo (`jacoco.minimo.*`) respeitado, elevando o limite se o piso medido subir (nunca descer)

## Workflow follow-up

- Revisar e arquivar a change (`/openspec-archive-change`) após implementação.
- Desbloqueia #92 (UI do seletor e do detalhe) e #93 (progresso sobre o mesmo conjunto canônico).
