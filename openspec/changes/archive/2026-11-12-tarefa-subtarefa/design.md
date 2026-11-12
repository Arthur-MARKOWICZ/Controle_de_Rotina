# Design

## Context

See proposal.md — Why. `Tarefa` é plana (`Tarefa.java`, tabela desde `V3`, `parent_id` inexistente). `POST /tarefas` aceita título + campos opcionais; listagem é `Page` via Specs. O motor (`PlannerGeracaoService`) carrega `findByUsuarioIdAndDeletedAtIsNullAndStatusIn` e aloca **cada** candidata com saldo > 0. Job de atraso é um `UPDATE` em massa por prazo (`atualizarStatusQuandoPrazoPassou`) — hoje pegaria o pai se o rótulo estiver no passado. Sessão (`TarefaSlotAvulsoService`) só checa status elegível. Lista: `TarefasPage` pagina 20, agrupa por prazo; `ehFiltroBasicoTodas` já distingue visão “todas” sem filtros (hoje só empty state). Drawer único (`TarefaDrawer`). Flyway em `V19`. Sem cache; injeção por construtor; Problem Detail. Prefixo `/tarefas` já no proxy.

## Goals / Non-Goals

**Goals:**

- Mesma entity/tabela; um `parent_id` nullable + invariantes no service.
- Pai como contêiner: duração persistida recalculada; folhas no planner/atraso/sessão.
- Lista em dois modos (árvore vs card solto) só no front, com queries mínimas (`somenteRaizes` / `parentId`).
- Cascatas no **backend** (UI confirma; API não depende do diálogo).

**Non-Goals:**

- Neto, reparentar, `ordem` arrastável, prefixo HTTP novo, tools MCP novas, herança de tags, checklist.

## Decisions

### 1. Coluna `parent_id` na mesma tabela

```text
tarefa.parent_id UUID NULL
  FK tarefa(id)
  INDEX (usuario_id, parent_id)
```

- Migration `V20__tarefa_parent.sql`.
- Alternativa rejeitada: tabela `subtarefa` — duplicaria status, sessão, tags e o motor.
- CHECK de um nível **não** vai no SQL (filho de filho exige trigger); o service recusa `parent.parentId != null`.

### 2. Create continua `POST /tarefas`

- `CriarTarefaRequest.parentId` opcional.
- Pai: mesmo usuário, `deletedAt == null`, `parentId == null`, sem execução `CONCLUIDO` em bloco daquele `tarefaId`.
- Sem `projetoId` no body → copia o do pai; `projetoId` enviado prevalece. Tags não copiam.
- Pai `FINALIZADA` + create válido → pai volta a `PENDENTE` depois de persistir o filho.
- Alternativa rejeitada: `POST /tarefas/{id}/subtarefas` — prefixo extra e dois creates.

`AtualizarTarefaRequest` **não** ganha `parentId` (reparentar fora).

### 3. Duração do pai persistida, não só na leitura

Após create/PATCH de duração/delete/restore de filho, `recomputarDuracaoPai`:

`duracaoMinutos = sum(filhos não deletados.duracaoMinutos)`

- Se a soma for 0 (último filho sumiu), **manter** o valor anterior (pai vira folha planejável).
- PATCH `duracaoMinutos` no pai com filhos não deletados → 400 Problem Detail.
- `restanteMinutos` do pai: 0 para sessão (pai inelegível); a UI mostra a soma como duração, não como restante de timer.

### 4. Folha = sem filhos não deletados

Helper único (query `EXISTS` / ids de pais da página) usado em:

- `PlannerGeracaoService` — tira pais da lista `elegiveis` **antes** do saldo.
- `TarefaSlotAvulsoService` / iniciar-concluir — 409 se não for folha (espelha `TarefaNaoElegivelException`).
- Job de atraso — o `UPDATE` ganha `AND NOT EXISTS (filho não deletado)`.

Score permanece `ScoreV1` na folha. Prazo/prioridade do pai não entram no motor.

### 5. Cascata no `TarefaService`

| API | Efeito |
|-----|--------|
| PATCH pai `FINALIZADA` | filhos ativos → `FINALIZADA`; sem XP (`xp.md`: PATCH não concede) |
| PATCH pai `CANCELADA` | filhos ativos → `CANCELADA` |
| PATCH pai para aberto | filhos `FINALIZADA` **intocados** |
| PATCH filho para aberto e pai `FINALIZADA` | pai → `PENDENTE` |
| `ExecucaoService` finaliza último filho | pai → `FINALIZADA` (mesmo gancho de progresso) |
| DELETE pai | soft delete filhos ativos |
| POST restore pai | restore **todos** os filhos deletados daquele `parent_id` |
| POST restore filho | restore o pai se deletado; irmãos intocados |

GET por id de pai deletado continua devolvendo o pai (contrato atual); filhos vêm no GET filtrado `parentId` + `incluirDeletadas` se a UI precisar.

### 6. Listagem: plana na API, árvore no front

`GET /tarefas` permanece lista plana. Acrescentar:

- `parentId` query opcional — filhos daquele pai.
- `somenteRaizes=true` — `parent_id IS NULL`.

Response: `parentId`, `parentTitulo` (batch, como `projetoNome`), `temFilhos` (boolean). GET `/{id}` do pai **não** precisa embutir array; o drawer lista com `GET /tarefas?parentId={id}&size=100`.

**Front**

- Visão árvore: `visao === 'todas'` e nenhum filtro isolador (`prioridade`, prazo, duração, projeto, tags). Chamar `somenteRaizes=true`; expandir busca `parentId`. Expand state: `Set` em memória no `TarefasPage`.
- Visão filtrada (qualquer `visao` ≠ todas, ou filtro isolador): GET atual. Se `parentId` está no set de ids da página → aninhar; senão card solto com “Subtarefa de {parentTitulo}”.
- `visao=excluidas`: plana (restaurar filho já reabre o pai na API).

Alternativa rejeitada: árvore só com a página atual sem `somenteRaizes` — filho na página 2 some no expandir.

### 7. UI: drawer e confirmação

- Seção “Subtarefas” no `TarefaDrawer` modo editar, raiz, não deletada: lista + create (título + duração no mínimo; prazo/prioridade/projeto opcionais como o create atual).
- Duração do pai com `temFilhos`: read-only no `TarefaForm`.
- `useConfirmDialog` antes de PATCH `FINALIZADA`/`CANCELADA` e DELETE quando `temFilhos`. Copy alinhada às specs.
- Card do pai: botão expandir (Caret); sem Iniciar/Concluir (`temFilhos` ou `parentId` para o indicador).

### 8. Exceções

Novas `ErrorResponseException` no pacote `shared.exception`, padrão do módulo:

- Neto / pai filho → 400
- Pai com bloco `CONCLUIDO` → 409
- PATCH duração no pai-contêiner → 400
- Sessão no pai → 409 (reusar ou especializar `TarefaNaoElegivelException`)

## Risks / Trade-offs

- [Paginação de raízes vs total de tarefas] → `somenteRaizes` muda `totalElements`; aceitável. MCP sem o flag continua vendo tudo.
- [Pai reaberto com filhos todos `FINALIZADA`] → motor ignora (sem folha); usuário precisa reabrir um filho ou criar outro. Comportamento combinado.
- [Job de atraso: UPDATE com NOT EXISTS] → um pouco mais caro; volume é por usuário/dia, índice `(usuario_id, parent_id)` segura.
- [Soma persistida vs filhos editados em paralelo] → uma transação no service; sem cache.

## Migration Plan

1. `V20` nullable `parent_id` + índice + FK. Sem backfill (todas as linhas já são raízes).
2. DTOs + regras de create/cascata/duração + ITs.
3. Planner, job, sessão.
4. Front: API → drawer → lista árvore → filtro solto → diálogos.
5. Rollback: coluna nullable; clientes velhos ignoram campos novos; reverter o filtro de folhas no motor se preciso.

## Open Questions

Nenhuma que altere spec ou fatia de tasks. Ordem visual dos filhos = `createdAt` asc (sem campo `ordem` neste corte).
