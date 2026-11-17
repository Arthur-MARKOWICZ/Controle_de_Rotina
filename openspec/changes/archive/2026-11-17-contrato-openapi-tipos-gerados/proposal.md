# Proposal

## Why

A API tem três clientes com tipos escritos à mão em duas linguagens: web (TypeScript), Android (Kotlin, `Modelos.kt`) e MCP (TypeScript). Toda mudança de DTO precisa ser repetida manualmente, e uma divergência só aparece em runtime. Além disso, as listagens devolvem `Page<T>` serializado diretamente, um formato que o Spring Data não garante entre versões. Esse formato também não bate com o que os clientes declaram: `PageTarefa` cobre só parte dos campos. Ref.: issue #76 (OPP-023).

## What Changes

- O backend passa a gerar um **spec OpenAPI 3.1** (springdoc-openapi 3.2.0, sem Swagger UI). O spec fica versionado em `contract/openapi.json`, e um teste do `mvn test` falha quando o spec gerado diverge do arquivo versionado.
- Regra de nulidade no contrato: todo campo de schema de **resposta** é `required`, e só os anotados com `@Nullable` (JSpecify) aceitam `null`. Nos corpos de **requisição**, só é `required` o que a validação exige, para que `PATCH` parcial continue válido.
- O endpoint `/v3/api-docs` fica **desligado por padrão**: ligado só nos perfis de teste e `local`, nunca em produção.
- **BREAKING:** as listagens paginadas (`GET /tarefas`, `/habitos`, `/projetos`, `/decidir`) passam a responder `{ content, page: { size, number, totalElements, totalPages } }` (`PagedModel`, `VIA_DTO`). Somem da raiz `totalElements`, `totalPages`, `last`, `number`, `pageable`, `sort` etc. Os três clientes são ajustados na mesma change.
- Front web: tipos gerados com `openapi-typescript` em `src/api/schema.d.ts`. Os domínios **tag** e **tarefa** e os tipos de página passam a ser aliases dos tipos gerados, mantendo os nomes atuais.
- MCP: tipos gerados com `openapi-typescript`, usados em `api-client.ts`.
- Mobile: um teste `jvmTest` valida `Modelos.kt` contra `contract/openapi.json`, cobrindo campos, nulidade e valores de enum. Não há geração de código Kotlin.
- CI: regenera os tipos TS e falha se houver diff em relação ao commitado.

Fora de escopo: trocar os clientes HTTP atuais, gerar cliente Kotlin, migrar os demais domínios do front para tipos gerados e publicar Swagger UI.

## Capabilities

### New Capabilities
- `contrato-api`: contrato OpenAPI da API como fonte única de tipos. Cobre a geração e o versionamento do spec, as regras de obrigatoriedade/nulidade, o formato de paginação, a exposição do endpoint do spec, o uso dos tipos gerados pelos clientes e as checagens que impedem divergência entre spec, backend e clientes.

### Modified Capabilities
<!-- Nenhuma: nenhuma spec existente fixa o formato JSON da paginação; `pendencia` só exige que o filtro respeite a paginação, o que continua valendo. -->

## Impact

- **Backend:** `pom.xml` (springdoc), config de paginação `VIA_DTO`, `PropertyCustomizer` de obrigatoriedade, `@Nullable` nos records de resposta/requisição, `application.yml` / `application-local.yml` / `src/test/resources/application.yml` e um IT novo do contrato. Os ITs que verificam campos de página na raiz passam a ler `$.page.*`.
- **Contrato:** novo diretório `contract/` com `openapi.json`.
- **Front web:** `openapi-typescript` (devDependency), um script de geração, `src/api/schema.d.ts`, `tagApi.ts`, `tarefaApi.ts`, os tipos de página de `habitoApi.ts`, `projetoApi.ts` e `decidirApi.ts` e as telas que leem `totalElements`/`totalPages` (Tarefas, Hábitos, Projetos, Decidir, Onboarding), além das fixtures de teste.
- **MCP:** `openapi-typescript`, `src/schema.d.ts` e `api-client.ts`. O texto devolvido por `listar_tarefas` muda de forma, porque repassa o JSON da página.
- **Mobile:** `Pagina<T>`/`PaginaTarefas` em `Modelos.kt`, `ExecutorLembrete.kt` (que usa `totalElements`) e um teste novo de contrato. **Um APK antigo instalado quebra na listagem paginada após o deploy do backend**, então um APK novo precisa ser instalado logo em seguida.
- **CI:** `deploy.yml` (checagem de tipos gerados) e `mobile.yml` (o teste de contrato roda no `jvmTest` já existente).
