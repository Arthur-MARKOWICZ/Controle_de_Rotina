# Tasks

## 1. Persistência

- [x] 1.1 Criar `V24__create_objetivo.sql` com tabela `objetivo`, `ck_objetivo_status`, `ck_objetivo_datas` e `idx_objetivo_usuario_status` (ver design); verificar que `mvn test` sobe o contexto e aplica a migration limpa
- [x] 1.2 Criar `StatusObjetivo`, entidade `Objetivo` (com `concluir`, `arquivar`, `reabrir`, `atualizar`) e `ObjetivoRepository` + `ObjetivoSpecs` (`doUsuario`, `comStatus`); verificar com teste de entidade que cada transição deixa as datas na combinação aceita pelo `CHECK` (inclui arquivar concluído mantendo `concluidoEm` e reabrir limpando as duas)

## 2. Regras de negócio no service

- [x] 2.1 Criar exceções em `shared/exception`: `ObjetivoNaoEncontradoException` (404), `ObjetivoJaConcluidoException`, `ObjetivoArquivadoException`, `ObjetivoJaArquivadoException`, `ObjetivoJaAtivoException` (409), com os `title` da spec; verificar no `ObjetivoServiceTest` o tipo concreto lançado em cada caso
- [x] 2.2 Criar DTOs `CriarObjetivoRequest`, `AtualizarObjetivoRequest` (desserializador com `prazoPresente`, trim, data malformada → exceção do Jackson) e `ObjetivoResponse` (`@Nullable` em `descricao`, `prazo`, `concluidoEm`, `arquivadoEm`); verificar com teste do desserializador: campo ausente, `prazo: null`, data válida e data malformada
- [x] 2.3 Implementar `ObjetivoService` com `criar`, `buscarPorId`, `atualizar`, `concluir`, `arquivar`, `reabrir` e validação de prazo em BRT (`PeriodoBRT.ZONE` + `Clock`); verificar `ObjetivoServiceTest` com `Clock` fixo: ≥ 1 feliz, ≥ 3 anomalias (outro usuário, reenviar prazo vencido igual, virada 23:30 BRT, concluir duas vezes sem mudar `concluidoEm`) e ≥ 3 erros (`PrazoNoPassadoException`, `ObjetivoArquivadoException`, `ObjetivoJaAtivoException`, `ObjetivoNaoEncontradoException`)
- [x] 2.4 Implementar `listar` com padrão `ATIVO` e ordem padrão por status quando o `Pageable` vier sem `sort`; verificar no `ObjetivoServiceTest` o `Sort` aplicado para cada status e que um `sort` do cliente é preservado

## 3. API `/objetivos`

- [x] 3.1 Criar `ObjetivoController` (`POST`, `GET` lista com `PagedModel`, `GET /{id}`, `PATCH /{id}`, `POST /{id}/concluir|arquivar|reabrir`) com `normalizarPageable` no padrão de projeto; verificar `ObjetivoIT` cobrindo todos os cenários da spec, com `status` + `$.title` (e `$.errors[*].property` para validação) e checando no banco que nada mudou nos casos de erro
- [x] 3.2 Adicionar `/objetivos` como path SPA + API em `frontend/frontend-web/vite.config.ts`, `frontend/frontend-web/nginx.conf` (location igual a `/projetos` e lista de prefixos no cabeçalho) e `frontend/frontend-web/API_PROXY.md`; verificar o checklist do `API_PROXY.md` consistente nos três arquivos

## 4. Contrato OpenAPI

- [x] 4.1 Regenerar `contract/openapi.json` (`cd backend && mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true`); verificar que `prazo` sai com `format: date`, os quatro campos anuláveis com `null` no tipo e `prazoPresente` ausente
- [x] 4.2 Regenerar `frontend/frontend-web/src/api/schema.d.ts` (`npm run gerar:contrato`) e `mcp/src/schema.d.ts` (`pnpm gerar:contrato`); verificar que `npm run build` no front e `pnpm build` no MCP passam

## 5. Integração

- [x] 5.1 Rodar `cd backend && mvn test` completo; verificar suíte verde e gate JaCoCo (`jacoco.minimo.*`) respeitado, elevando o limite se o piso medido subir

## Workflow follow-up

- Revisar e arquivar a change (`/openspec-archive-change`) após implementação.
- Desbloqueia #90 (UI) e #91 (vínculos).
