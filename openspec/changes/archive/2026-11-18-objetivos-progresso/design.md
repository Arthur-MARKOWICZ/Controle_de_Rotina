# Design

## Context

Motivação em `proposal.md`; comportamento em `specs/objetivo/spec.md`.

Nesta branch: `objetivo` + vínculos (`V25`, `ObjetivoVinculoService.listarItens`). O conjunto canônico está **inline** em `listarItens`, não extraído. `ObjetivoResponse` não tem `progresso`. `objetivo_habito` é só PK composta. `substituirObjetivosHabito` faz delete all + insert, no molde de `TagVinculoService`.

Saldo de tarefa hoje: `ExecucaoRepository.somarDuracaoMinutosPorTarefasEStatus` soma `bloco.duracaoMinutos` por id da própria tarefa — não pelos filhos. Minutos de hábito já existem em `HabitoHistoricoService.minutosDedicados` (timer bruto ou `minutosInformados`). `DiaSemana.de(LocalDate)` e `PeriodoBRT` cobrem weekday e fuso. Flyway em `V25`. Sem cache. Injeção por construtor.

## Goals / Non-Goals

**Goals:**

- Um resolvedor de conjunto canônico reusado por `GET .../itens` e pelo progresso, em lote na listagem.
- Cálculo puro (fácil de testar com `Clock` fixo) + consultas agregadas, sem N+1 por objetivo da página.
- `created_at` estável no vínculo de hábito; PATCH que reenvia os mesmos ids não reinicia a janela.

**Non-Goals:**

- Barra na UI; progresso por item no GET de itens; `created_at` em `objetivo_tarefa` / `objetivo_projeto`; mudar `substituir*` de tarefa e projeto; persistir percentual; MCP tools.

## Decisions

### 1. `ObjetivoProgressoService` + extração do conjunto

`ObjetivoVinculoService` passa a expor o conjunto (tarefas raiz com origem, hábitos com `created_at` do vínculo, projetos) para um id e para um lote de ids. `listarItens` só mapeia isso para o DTO atual.

`ObjetivoProgressoService` (package `objetivo`, `Clock`) recebe o conjunto já resolvido + o `Objetivo` (prazo, status, datas) e devolve `ObjetivoProgressoResponse` ou `null`. `ObjetivoService` chama o lote depois de `findAll` da página e no `from` de detalhe/mutações. Create sem vínculos → `null` sem consulta extra.

Alternativa descartada: calcular dentro de `ObjetivoVinculoService` — mistura escrita de join com regra de tempo. Alternativa descartada: coluna persistida — a % muda com execução, pausa, meia-noite e vínculo; a issue pede leitura.

### 2. DTO aninhado em `ObjetivoResponse`

```
ObjetivoProgressoResponse(int percentual, int minutosConcluidos, int minutosTotais)
ObjetivoResponse(..., @Nullable ObjetivoProgressoResponse progresso)
```

Os três ints são obrigatórios quando o objeto existe. O objeto inteiro é `@Nullable` (OpenAPI `null` no tipo, campo em `required`). `percentual = min(100, (int) Math.floor(100.0 * concluidos / totais))`.

Todo endpoint que hoje devolve `ObjetivoResponse` passa a carregar progresso — um `from` só, sem ramo especial. Mobile continua subset; não mexer no Kotlin.

### 3. Consultas em lote, filtro da janela na memória

Por página (até 100, default 20):

1. joins dos objetivo ids da página
2. tarefas raiz ativas diretas + herdadas (já existente), **menos** `CANCELADA` no somatório (continuam no GET itens)
3. filhos vivos: `findByParentIdInAndDeletedAtIsNull` (método novo)
4. `somarDuracaoMinutosPorTarefasEStatus` com ids raiz+filhos; atribuir ao pai
5. hábitos `ATIVO` e não deletados; `created_at` do join
6. uma query de cumprimentos `CONCLUIDO` com `habitoId IN (...)` e `plano.data` entre o menor início e o maior fim das janelas da página; agregar na memória com `minutosDedicados` (extrair o estático de `HabitoHistoricoService` para um sítio comum, ex. método de pacote em `execucao` ou copiar a fórmula num `HabitoMinutos` no módulo hábito — **uma** função, dois callers)

Dias esperados: loop inclusivo `inicio`→`fim` com `DiaSemana.de(dia)` ∈ `habito.diasSemana`. Escala pessoal (janela de meses, não anos). Alternativa descartada: SQL `generate_series` por par — mais difícil de testar com `Clock`.

`CANCELADA` permanece no GET itens (status visível) e some só do progresso. Soft-delete já some dos dois.

### 4. `created_at` só em `objetivo_habito` + diff no substituir

`V26__objetivo_habito_created_at.sql`:

```sql
ALTER TABLE objetivo_habito
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
```

Linhas existentes ficam com o instante do deploy. DEFAULT permanece como rede de segurança; a app grava `clock.instant()` no insert.

Entidade `ObjetivoHabito` ganha o campo. `substituirObjetivosHabito`:

- `atuais` = pares do hábito
- validar ids como hoje (`incoming - atuais` precisa estar `ATIVO`)
- apagar só `atuais - incoming`
- inserir só `incoming - atuais` com `created_at` agora
- pares na interseção **não** são tocados

`deleteAllByHabitoId` deixa de ser usado nesse fluxo; entra `deleteByHabitoIdAndObjetivoIdIn`. Tarefa e projeto continuam delete+insert.

Alternativa descartada: `habito.created_at` como início — hábito antigo zera a %. Alternativa descartada: janela = só hoje — a #95 precisa de história.

### 5. Contrato

Regen `contract/openapi.json` (`ContratoOpenApiIT -Dcontrato.atualizar=true`) e os dois `schema.d.ts`. Fixtures Java/TS que montam `ObjetivoResponse` ganham `progresso` (em geral `null`). Front não troca o placeholder do card.

## Risks / Trade-offs

- [Barra de hábito desce à meia-noite BRT] → Aceito; spec + testes com `Clock` na virada.
- [Reativar hábito pausado derruba a %] → Aceito; não há log de pausa.
- [Timer antigo sem `iniciadoEm` conta 0 min dedicado] → Mesma regra do histórico de hábito; não reinventar.
- [GET itens de projeto grande] → O lote da listagem carrega as mesmas tarefas; escala pessoal. Paginar itens continua fora.
- [ITs/fixtures de `ObjetivoResponse` quebram] → Atualizar na tarefa do DTO, `progresso` nulo.

## Migration Plan

Migration aditiva (`V26`). Deploy normal. Rollback: reverter o deploy; a coluna pode ficar. Vínculos de hábito já existentes passam a ter janela a partir do instante do `ALTER` (`NOW()`), não da criação original do hábito — documentado na spec como “data em que o par foi criado” daqui pra frente.

## Open Questions

Nenhuma. Decisões de produto (janela, minutos dedicados, pausado fora, floor, hoje no total, freeze no encerramento) estão na spec.
