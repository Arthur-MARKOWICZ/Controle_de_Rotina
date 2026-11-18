# Tasks

## 1. Persistência do vínculo de hábito

- [x] 1.1 Criar `V26__objetivo_habito_created_at.sql` com `ALTER TABLE objetivo_habito ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`; verificar que `mvn test -Dtest=ObjetivoVinculoIT` sobe o contexto e aplica a migration limpa (linhas pré-existentes recebem `NOW()`)
- [x] 1.2 Incluir `createdAt` em `ObjetivoHabito` (construtor de insert com o instante; getter) e `deleteByHabitoIdAndObjetivoIdIn` no repositório; verificar compilação do módulo `objetivo`

## 2. Diff no substituir de hábito

- [x] 2.1 Mudar `substituirObjetivosHabito` para diff: apagar só `atuais - incoming`, inserir só `incoming - atuais` com `clock.instant()`, não tocar a interseção; injetar `Clock` se ainda não tiver; verificar `ObjetivoVinculoServiceTest`: reenviar os mesmos ids não chama delete nem save da linha existente; trocar A por B apaga A e insere B; `[]` apaga todos; ids novos em objetivo não `ATIVO` continuam `ObjetivoNaoAtivoException` sem alterar o banco

## 3. Conjunto canônico extraído

- [x] 3.1 Extrair de `listarItens` um resolvedor de conjunto (um id e um lote de ids) com tarefas raiz, hábitos (incluindo `created_at` do join) e projetos; `listarItens` só mapeia para o DTO; verificar que `ObjetivoVinculoIT` de itens permanece verde (mesma ordem DIRETO antes de VIA_PROJETO, dedupe, soft delete)

## 4. Cálculo de progresso

- [x] 4.1 Extrair `minutosDedicados` para um sítio único usado por `HabitoHistoricoService` e pelo progresso; adicionar query em lote de cumprimentos `CONCLUIDO` por `habitoId IN` e intervalo de `plano.data`; verificar que os testes atuais de `HabitoHistoricoService` / `HabitoHistoricoIT` continuam verdes
- [x] 4.2 Adicionar `findByParentIdInAndDeletedAtIsNull` em `TarefaRepository`; verificar teste de repositório ou uso no service com dois pais e um filho cada
- [x] 4.3 Criar `ObjetivoProgressoResponse` (`percentual`, `minutosConcluidos`, `minutosTotais`) e `ObjetivoProgressoService` (conjunto + `Objetivo` + `Clock` → objeto ou `null`, floor limitado a 100); verificar `ObjetivoProgressoServiceTest` com `Clock` fixo em BRT: ≥ 1 feliz (120 FINALIZADA + 30 pendente → 80%) e os cenários da spec — parcial 60/120, extra de hábito cobrindo buraco, misto, cancelada → null, soft delete, dedupe herança, sem itens → null, projeto vazio → null, pausado fora, reativar recoloca dias, meia-noite inclui hoje, virada de semana 23:30 BRT, freeze em `concluidoEm`, prazo vencido corta, execução antes do vínculo fora, 10/30 → 33%, 149/150 → 99, filhos somam na raiz, execuções acima da duração capadas

## 5. API

- [x] 5.1 Incluir `@Nullable ObjetivoProgressoResponse progresso` em `ObjetivoResponse`; `ObjetivoService` preenche via lote na listagem e no detalhe/mutações (create sem vínculos pode ir `null` sem consulta); corrigir testes/records que montam o response; verificar `ObjetivoServiceTest`: lista com um vazio e um completo; detalhe 80%; 404 de outro usuário inalterado
- [x] 5.2 Estender `ObjetivoIT` (e o IT de vínculo onde couber o PATCH do hábito) aos cenários HTTP da spec: detalhe 80%; listagem com null e 100; `progresso` null sem itens; PATCH hábito reenviando ids preserva a janela; objetivo de outro usuário → 404 `$.title` "Objetivo não encontrado" sem vazar progresso; checar no JSON que `progresso` vem nulo ou com os três campos

## 6. Contrato OpenAPI

- [x] 6.1 Regenerar `contract/openapi.json` (`cd backend && mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true`); verificar `progresso` em `ObjetivoResponse` com `null` no tipo, objeto com os três ints required, e `ObjetivoProgressoResponse` no spec
- [x] 6.2 Regenerar `frontend/frontend-web/src/api/schema.d.ts` (`npm run gerar:contrato`) e `mcp/src/schema.d.ts` (`pnpm gerar:contrato`); ajustar fixtures/testes TS que montam `ObjetivoResponse` (`progresso: null`); verificar `npm run build` no front e `pnpm build` no MCP. Não alterar o placeholder do card nem modelos Kotlin

## 7. Integração

- [x] 7.1 Rodar `cd backend && mvn test`; verificar suíte verde e gate JaCoCo (`jacoco.minimo.*`) respeitado, elevando o limite se o piso medido subir (nunca descer)

## Workflow follow-up

- Revisar e arquivar a change (`/openspec-archive-change`) após implementação.
- Desbloqueia #94 (barra no card e no detalhe sobre este `progresso`).
