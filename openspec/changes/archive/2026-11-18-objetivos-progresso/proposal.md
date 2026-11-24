# Proposal

## Why

O usuário precisa ver quanto avançou no objetivo sem informar nada à mão. Os vínculos (#91) já definem o conjunto; falta o progresso automático ponderado por tempo (issue #93). Sem isso a barra da #94 não tem o que mostrar.

## What Changes

- **BREAKING:** `ObjetivoResponse` (detalhe e listagem) passa a incluir `progresso`, objeto anulável com `percentual`, `minutosConcluidos` e `minutosTotais`. Sem itens que somem tempo (`minutosTotais = 0`) o objeto é `null`, nunca 0%.
- Progresso = minutos concluídos ÷ minutos totais do conjunto canônico (diretos + tarefas raiz herdadas, sem duplicar), calculado na leitura, sem coluna persistida e sem cache.
- Tarefa raiz: total = `duracaoMinutos`; feito = a duração se `FINALIZADA`, senão minutos canônicos das execuções `CONCLUIDO` da raiz e dos filhos vivos, limitados à duração. `CANCELADA` fica fora.
- Hábito `ATIVO`: total = `duracaoMinutos` × dias esperados na janela; feito = minutos dedicados do histórico (timer ou informados) na mesma janela, limitados ao total. `PAUSADO` e soft-delete ficam fora.
- Janela do hábito: data civil BRT de `objetivo_habito.created_at` até `min(hoje, prazo se já passou, dia de concluidoEm/arquivadoEm)`. Hoje sempre entra no total.
- Migration: `created_at` em `objetivo_habito` (`NOT NULL DEFAULT NOW()`). `substituirObjetivosHabito` vira diff para não resetar a janela quando o PATCH reenvia os mesmos ids.
- `contract/openapi.json` e `schema.d.ts` (web e MCP) regenerados.

Fora do escopo: barra na UI (#94), seletor e detalhe de itens (#92), histórico/curva (#95), progresso por item no `GET .../itens`, filtro `?objetivoId=`, ferramentas novas no MCP, XP ao concluir objetivo.

## Capabilities

### New Capabilities
<!-- Nenhuma: o progresso pertence à capability `objetivo` já criada pela #89. -->

### Modified Capabilities
- `objetivo`: passa a expor progresso ponderado por tempo na listagem e no detalhe, com janela do hábito a partir do vínculo e congelamento no encerramento.

## Impact

- **Backend:** calculadora de progresso no package `objetivo`; `ObjetivoProgressoResponse` aninhado em `ObjetivoResponse` (`@Nullable`); extração do conjunto canônico para reuso em lote na listagem; `ObjetivoHabito.createdAt`; `substituirObjetivosHabito` deixa de apagar e recriar pares que já existiam.
- **Banco:** migration aditiva em `objetivo_habito` (`created_at`).
- **API/contrato:** campo novo em `ObjetivoResponse`; regen de `contract/openapi.json` e `schema.d.ts` (web e MCP).
- **Frontend UI, MCP tools, mobile:** sem feature nova. O card continua com o placeholder da #90 até a #94. O mobile não precisa alterar modelos (são subset do contrato). Testes/fixtures que montam `ObjetivoResponse` passam a incluir `progresso`.
