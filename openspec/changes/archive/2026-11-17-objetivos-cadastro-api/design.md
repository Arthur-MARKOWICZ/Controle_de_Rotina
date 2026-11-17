# Design

## Context

Motivação em `proposal.md`; comportamento em `specs/objetivo/spec.md`.

O módulo `projeto` já resolve quase tudo o que objetivo precisa e serve de molde: controller fino com `PagedModel` e `normalizarPageable`, service com `Clock` injetado, `findByIdAndUsuarioId` → 404, exceções `ErrorResponseException` em `shared/exception` com `title` fixo, `Specification` para filtro por status, migration com `CHECK` de status e índice `(usuario_id, status)`. O PATCH com campo de data que pode ser limpo já existe em `AtualizarDecidirRequest` (`decidirAtePresente` via `node.has(...)`). O fuso BRT está centralizado em `historico.PeriodoBRT.ZONE`.

## Goals / Non-Goals

**Goals:**
- Seguir o padrão do módulo `projeto` sem introduzir abstração nova.
- Deixar o banco garantir a coerência entre `status` e as datas de encerramento.
- Deixar `concluidoEm` / `arquivadoEm` prontos para o histórico (#95) sem antecipar a #95.

**Non-Goals:**
- Tags, `ordem`/reordenação, exclusão, esperas, vínculos (#91), progresso (#93), histórico (#95), UI (#90).
- Ferramentas novas no MCP (só o `schema.d.ts` é regenerado).

## Decisions

### Package e camadas
Novo package `com.controlerotina.objetivo` com `entity/Objetivo`, `entity/StatusObjetivo`, `repository/ObjetivoRepository` (`JpaRepository` + `JpaSpecificationExecutor`, `findByIdAndUsuarioId`), `repository/ObjetivoSpecs` (`doUsuario`, `comStatus`), `dto/CriarObjetivoRequest`, `dto/AtualizarObjetivoRequest`, `dto/ObjetivoResponse`, `service/ObjetivoService`, `controller/ObjetivoController` em `@RequestMapping("/objetivos")`.

Endpoints:

| Método | Path | Sucesso |
|---|---|---|
| POST | `/objetivos` | 201 |
| GET | `/objetivos?status=&page=&size=&sort=` | 200 `PagedModel<ObjetivoResponse>` |
| GET | `/objetivos/{id}` | 200 |
| PATCH | `/objetivos/{id}` | 200 |
| POST | `/objetivos/{id}/concluir` | 200 |
| POST | `/objetivos/{id}/arquivar` | 200 |
| POST | `/objetivos/{id}/reabrir` | 200 |

Alternativa descartada: `PATCH { status }` genérico. Ações explícitas seguem o padrão de projeto (`/arquivar`, `/reativar`) e deixam cada 409 ligado a uma ação. O nome `reabrir` (e não `reativar`, como em projeto) vem da issue e cobre também o caso concluído.

### Transições na entidade, validação no service
`Objetivo` expõe `concluir(Instant)`, `arquivar(Instant)`, `reabrir(Instant)` e `atualizar(...)`, que apenas aplicam o estado. O service verifica o status atual e lança a exceção certa antes de chamar a entidade, como `ProjetoService.arquivar`.

| Ação \ status | ATIVO | CONCLUIDO | ARQUIVADO |
|---|---|---|---|
| concluir | `concluidoEm = agora` | `ObjetivoJaConcluidoException` | `ObjetivoArquivadoException` |
| arquivar | `arquivadoEm = agora` | `arquivadoEm = agora`, mantém `concluidoEm` | `ObjetivoJaArquivadoException` |
| reabrir | `ObjetivoJaAtivoException` | limpa as duas datas | limpa as duas datas |

Novas exceções (todas `ErrorResponseException`): `ObjetivoNaoEncontradoException` (404 "Objetivo não encontrado"), `ObjetivoJaConcluidoException` (409 "Objetivo já concluído"), `ObjetivoArquivadoException` (409 "Objetivo arquivado", detail orienta reabrir antes), `ObjetivoJaArquivadoException` (409 "Objetivo já arquivado"), `ObjetivoJaAtivoException` (409 "Objetivo já ativo").

### `prazo` como `LocalDate`
Coluna `DATE`, `format: date` no contrato. "Hoje" é `LocalDate.now(clock.withZone(PeriodoBRT.ZONE))`. Regra no service:
- criar: `prazo != null && prazo.isBefore(hoje)` → `PrazoNoPassadoException`;
- editar: só quando `prazoPresente && prazo != null && !prazo.equals(atual) && prazo.isBefore(hoje)`.

Alternativa descartada: `Instant`, como em tarefa — obrigaria a inventar um horário para algo que é um dia e complicaria a virada de dia nos testes.

### PATCH com `prazo` limpável
`AtualizarObjetivoRequest` usa `@JsonDeserialize` próprio, no molde de `AtualizarDecidirRequest`: `prazoPresente = node.has("prazo")`; ausente mantém, `null` limpa, data troca. O componente `prazoPresente` fica `@Schema(hidden = true)`. Um `prazo` fora de `yyyy-MM-dd` precisa virar 400 "Requisição inválida": o desserializador converte com o próprio Jackson (ou captura `DateTimeParseException` e relança como exceção do Jackson) para cair em `HttpMessageNotReadableException`, não em 500. `titulo` e `descricao` seguem o trim de `AtualizarProjetoRequest`; `descricao` em branco limpa (como `Projeto.atualizar`).

### Coerência status × datas no banco
Migration `V24__create_objetivo.sql`:

```sql
CREATE TABLE objetivo (
    id            UUID PRIMARY KEY,
    usuario_id    UUID NOT NULL,
    titulo        VARCHAR(80) NOT NULL,
    descricao     VARCHAR(500),
    prazo         DATE,
    status        VARCHAR(20) NOT NULL,
    concluido_em  TIMESTAMPTZ,
    arquivado_em  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_objetivo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_objetivo_status CHECK (status IN ('ATIVO', 'CONCLUIDO', 'ARQUIVADO')),
    CONSTRAINT ck_objetivo_datas CHECK (
        (status = 'ATIVO'     AND concluido_em IS NULL AND arquivado_em IS NULL) OR
        (status = 'CONCLUIDO' AND concluido_em IS NOT NULL AND arquivado_em IS NULL) OR
        (status = 'ARQUIVADO' AND arquivado_em IS NOT NULL)
    )
);

CREATE INDEX idx_objetivo_usuario_status ON objetivo (usuario_id, status);
```

O índice atende ao filtro da listagem; a ordenação acontece sobre o subconjunto de um usuário e um status (poucas linhas), então não justifica índice composto com `prazo`/datas.

### Ordem padrão da listagem
O controller só normaliza `page`/`size` (mesmos limites de projeto: padrão 20, máximo 100) e repassa o `sort` do cliente quando houver. Se o `Pageable` vier sem ordenação, o service aplica a ordem do status efetivo (depois do padrão `ATIVO`), porque ela depende de regra de negócio:
- `ATIVO`: `Sort.Order.asc("prazo").nullsLast()`, `asc("createdAt")`;
- `CONCLUIDO`: `desc("concluidoEm")`, `desc("createdAt")`;
- `ARQUIVADO`: `desc("arquivadoEm")`, `desc("createdAt")`.

`nullsLast` já é usado em `DecidirController`.

### Response e contrato
`ObjetivoResponse(id, titulo, descricao, prazo, status, concluidoEm, arquivadoEm, createdAt, updatedAt)`, com `descricao`, `prazo`, `concluidoEm` e `arquivadoEm` anotados `@Nullable` para o contrato não declarar "nunca nulo". Em `CriarObjetivoRequest`, só `titulo` é obrigatório (`@NotBlank @Size(max = 80)`).

### Proxy
`/objetivos` é path compartilhado SPA + API (a #90 cria a página `/objetivos`): `spaApiSharedProxy` em `vite.config.ts`, um `location ^~ /objetivos` no `nginx.conf` igual ao de `/projetos` e a linha correspondente em `API_PROXY.md`.

## Risks / Trade-offs

- [Desserializador próprio deixa passar data malformada como 500] → cenário "Prazo em formato inválido" no IT exige 400 "Requisição inválida".
- [`CHECK` de datas rejeita estado que o service deixou passar por bug] → é o objetivo: falha alto (500) em vez de gravar inconsistência; os testes de transição cobrem todos os caminhos válidos.
- [Reabrir apaga `concluidoEm`, perdendo a data original] → decisão de produto aceita; quem reabre e conclui de novo ganha data nova.
- [Ordem padrão diferente por status pode surpreender quem consome a API] → documentada na spec; cliente pode mandar `sort` explícito.
- [`nginx.conf` alterado exige rebuild da imagem do frontend] → registrado no `API_PROXY.md` e nas tasks.

## Migration Plan

Migration aditiva (tabela nova, sem alteração em tabelas existentes). Rollback: reverter o deploy; a tabela vazia pode ficar ou ser removida manualmente, pois nenhum outro módulo depende dela nesta change.
