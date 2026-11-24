# Design

## Context

Motivação em `proposal.md`; comportamento em `specs/objetivo/spec.md`.

O módulo `objetivo` (cadastro #89) já existe nesta branch: tabela `objetivo`, `/objetivos` CRUD e ciclo de vida, proxy SPA+API. Não há join com tarefa/hábito/projeto.

O molde de vínculo é `TagVinculoService`: três tabelas com PK composta, `substituir*` (delete all + insert), `mapear*Por*` em lote na listagem, `tagIds` / `tagIdsPresente` no PATCH. Tags **não** herdam do projeto; objetivos **herdam** na leitura. Por isso a escrita copia tags e a leitura tem um segundo caminho.

Flyway está em `V24`. Sem cache. Injeção por construtor.

## Goals / Non-Goals

**Goals:**

- Reusar o padrão de tags na escrita, sem endpoint `POST /objetivos/{id}/tarefas`.
- Um método que resolve o conjunto canônico do objetivo (diretos + tarefas raiz herdadas, sem duplicar), para `GET .../itens` e para a #93 não inventar outra query.
- Enrich em lote nas listagens de tarefa/hábito/projeto, no mesmo loop de `mapearTagsPor*`.

**Non-Goals:**

- Filtro `?objetivoId=` nas listagens; paginação de itens; UI; progresso; MCP tools; tabela genérica `(tipo, entidade_id)`.

## Decisions

### 1. Três tabelas de join, não uma polimórfica

```sql
CREATE TABLE objetivo_tarefa (
    objetivo_id UUID NOT NULL,
    tarefa_id   UUID NOT NULL,
    CONSTRAINT pk_objetivo_tarefa PRIMARY KEY (objetivo_id, tarefa_id),
    CONSTRAINT fk_objetivo_tarefa_objetivo FOREIGN KEY (objetivo_id) REFERENCES objetivo (id) ON DELETE CASCADE,
    CONSTRAINT fk_objetivo_tarefa_tarefa FOREIGN KEY (tarefa_id) REFERENCES tarefa (id) ON DELETE CASCADE
);
CREATE INDEX idx_objetivo_tarefa_tarefa ON objetivo_tarefa (tarefa_id);
```

O mesmo para `objetivo_habito` (`habito_id`) e `objetivo_projeto` (`projeto_id`). Migration `V25__create_objetivo_vinculo.sql`.

PK composta torna "vincular o mesmo par duas vezes" um no-op no insert. Índice no lado do item atende `mapearObjetivosPorTarefa` (IN da página). Alternativa descartada: uma tabela `objetivo_vinculo(tipo, entidade_id)` — foge de `tarefa_tag` e perde FK.

Soft delete da tarefa **não** apaga a linha. O GET de itens filtra `deleted_at IS NULL`; restaurar reaparece.

### 2. `ObjetivoVinculoService` no package `objetivo`

Entidades `ObjetivoTarefa` / `ObjetivoHabito` / `ObjetivoProjeto` no molde de `TarefaTag` (`@IdClass`, só os dois UUIDs). Repositórios com `deleteAllByTarefaId`, `findByTarefaIdIn`, `findByObjetivoId`.

O service espelha `TagVinculoService`:

- `substituirObjetivosTarefa|Habito|Projeto(usuarioId, entidadeId, objetivoIds)`
- `listarObjetivosTarefa|...` e `mapearObjetivosPorTarefa|...` → `List<ObjetivoResumoResponse>` (`id`, `titulo`, `status`)
- `listarItens(usuarioId, objetivoId)` → `ObjetivoItensResponse`

`ObjetivoService` não conhece joins. `TarefaService` / `HabitoService` / `ProjetoService` chamam `substituir*` no create e, no PATCH, só se `objetivoIdsPresente`. Listagens chamam `mapear*` junto com as tags.

Validar ids (LinkedHashSet, `findByUsuarioIdAndIdIn` em `ObjetivoRepository`): tamanho diferente → `ObjetivoNaoEncontradoException`. Para cada id em `novos = incoming - atuais`, `status != ATIVO` → `ObjetivoNaoAtivoException` (409, title `"Objetivo não está ativo"`, detail `"Só é possível vincular a um objetivo ativo."`). Não reusar `ObjetivoArquivadoException` (detail fala em concluir).

Subtarefa: o service de tarefa recusa **antes** de chamar `substituir*` — create com `parentId` e `objetivoIds` não vazio, ou PATCH de item com `parentId != null` e `objetivoIdsPresente` com algum id → `SubtarefaObjetivoException` (400, title `"Subtarefa não pode ser vinculada a objetivo"`). Lista vazia ou campo ausente na subtarefa passa.

### 3. Write nos requests existentes

`CriarTarefaRequest`, `CriarHabitoRequest`, `CriarProjetoRequest`: `List<UUID> objetivoIds` opcional (não entra em `required` no OpenAPI).

PATCH: `objetivoIds` + `objetivoIdsPresente` (`@Schema(hidden = true)`), no desserializador ao lado de `tagIds`. `null` no JSON vira lista vazia (limpa), como tags.

Alternativa descartada: só `POST /objetivos/{id}/tarefas` — a #92 precisa do mesmo drawer's `objetivoIds` que o picker de tags.

### 4. Response do item: só direto

`TarefaResponse`, `HabitoResponse`, `ProjetoResponse` ganham `List<ObjetivoResumoResponse> objetivos` obrigatório, nunca nulo (lista vazia). Overloads de `from(...)` que hoje não passam tags passam `List.of()` também para objetivos; o `from` completo recebe os dois.

Herança **não** entra aqui: se entrasse, o PATCH reenviaria um objetivo herdado e o persistiria como direto (ou um uncheck no form da #92 desligaria o projeto).

`objetivoIds` no JSON de resposta, como a issue nomeou, foi descartado em favor de resumos — o form precisa de `titulo` sem segundo GET.

N+1: um `findByTarefaIdIn` por página, igual tags.

Mobile: `VerificadorContrato` permite subset; `Tarefa` Kotlin não precisa do campo novo.

### 5. Conjunto canônico e `GET /objetivos/{id}/itens`

`ObjetivoController`: `GET /{id}/itens` (path já coberto pelo proxy `/objetivos`). 404 via `buscarDoUsuario`. Não é `PagedModel`.

```
ObjetivoItensResponse
  tarefas:  [{ id, titulo, status, origem, projetoId, projetoNome }]
  habitos:  [{ id, titulo, status, origem }]
  projetos: [{ id, nome, status, origem }]
origem: DIRETO | VIA_PROJETO
```

`projetoId` / `projetoNome` nas tarefas alimentam o selo "via projeto X" da #92; nulos quando não há projeto. Hábitos e projetos são sempre `DIRETO`.

Resolução do conjunto (um método, reusado pela #93):

1. joins diretos do objetivo
2. tarefas: `deleted_at IS NULL` e `parent_id IS NULL`
3. hábitos: `deleted_at IS NULL`
4. projetos: todos os ligados (arquivado continua)
5. tarefas raiz dos projetos em (4), mesmo filtro de delete/parent, **menos** as já em (2)
6. (2) → `DIRETO`; (5) → `VIA_PROJETO`

Quatro consultas, não N+1. Sem paginação: escala pessoal. Ordem: `created_at` crescente em cada grupo; em `tarefas`, `DIRETO` antes de `VIA_PROJETO` (estável para o teste de dedupe).

### 6. Contrato

Regen `contract/openapi.json` (`ContratoOpenApiIT -Dcontrato.atualizar=true`) e os dois `schema.d.ts`. `ObjetivoResumoResponse` e o response de itens: campos de resposta todos em `required`; `projetoId` / `projetoNome` com `@Nullable`. `objetivoIdsPresente` hidden, fora do spec. `objetivoIds` opcional nos creates e PATCHes.

## Risks / Trade-offs

- [Replace-set reenvia objetivo já concluído e 409 se validar o conjunto inteiro] → Validar ATIVO só em `incoming - atuais`.
- [TarefaResponse.from explode overloads] → Encadear default `List.of()` como já é feito com tags/esperas.
- [GET itens cresce se um projeto tiver muitas tarefas] → Aceito neste corte; paginar é a #92/#93 se doer.
- [ITs de tarefa/hábito/projeto que montam o record quebram] → Atualizar na mesma tarefa do DTO, `objetivos` vazio.
- [`objetivos` required no OpenAPI vs mobile] → Verificador aceita modelo menor; não mexer no Kotlin.

## Migration Plan

Migration aditiva (três tabelas novas). Deploy normal. Rollback: reverter o deploy; as tabelas vazias podem ficar. Nenhum backfill — itens existentes passam a devolver `objetivos: []`.
