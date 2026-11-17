# Tasks

## 1. Backend: springdoc e endpoint do spec

- [x] 1.1 Adicionar `springdoc-openapi-starter-webmvc-api:3.2.0` ao `backend/pom.xml` e verificar que `cd backend && mvn -q dependency:tree | grep springdoc` lista a versão 3.2.0 e que `mvn test` continua verde.
- [x] 1.2 Configurar `springdoc.api-docs.enabled=false`, `springdoc.api-docs.version=openapi_3_1` e `springdoc.model-converters.java-nullable-property-customizer.enabled=true` no `application.yml`, e `springdoc.api-docs.enabled=true` em `src/test/resources/application.yml` e em `application-local.yml` (D3). Verificar com IT: endpoint desligado na config base → `/v3/api-docs` não devolve o spec; ligado com token → 200 JSON; ligado sem token → 401.

## 2. Backend: obrigatoriedade e nulidade

- [x] 2.1 Implementar `ContratoObrigatoriedadeCustomizer` (D4) e verificar com teste unitário de ≥ 7 cenários (1 + 3 + 3): schema de resposta com `required` completo; `$ref` aninhado, `items` e schema compartilhado; ciclo de `$ref` sem loop infinito; schema só de requisição intocado; `$ref` inexistente, `paths` vazio e resposta sem `content` sem exceção nem alteração.
- [x] 2.2 Anotar com `@org.jspecify.annotations.Nullable` os componentes de records de resposta que podem ser nulos, partindo dos campos `= null` de `Modelos.kt` e dos `| null`/`?:` do front e conferindo cada um com o `from(...)` correspondente. Verificar que `TarefaResponse.prazo` sai `required` e anulável e `TarefaResponse.id` sai `required` e não anulável (cenários do spec).

## 3. Backend: spec versionado

- [x] 3.1 Criar `ContratoOpenApiIT` (D2): login, `GET /v3/api-docs`, normalização (chaves ordenadas, `required`/`enum` ordenados, sem `servers`), comparação com `contract/openapi.json` e gravação com `-Dcontrato.atualizar=true`. Verificar que gerar duas vezes produz arquivos idênticos e que alterar um campo à mão no JSON faz o teste falhar com o comando de regeneração na mensagem.
- [x] 3.2 Gerar e commitar `contract/openapi.json` com `cd backend && mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true`. Verificar que `CriarTagRequest` tem `nome` em `required` e `AtualizarTagRequest` não tem `required`.

## 4. Backend: paginação `PagedModel` (BREAKING)

- [x] 4.1 Trocar o retorno de `listar` em `TarefaController`, `HabitoController`, `ProjetoController` e `DecidirController` para `PagedModel<T>` (D5). Atualizar os ITs que leem campos de página na raiz para `$.page.*` e cobrir os cenários do spec: página intermediária, lista vazia, página além do fim e ausência de `totalElements`/`last`/`pageable`/`sort` na raiz. Verificar com `cd backend && mvn test`.
- [x] 4.2 Regenerar `contract/openapi.json` e verificar no diff que os schemas `PageXxx` deram lugar a `PagedModelXxx` com `page` obrigatório.

## 5. Front web: tipos gerados e paginação

- [x] 5.1 Adicionar `openapi-typescript` com versão fixa e o script `gerar:contrato` (D6), e gerar `src/api/schema.d.ts`. Verificar que o arquivo existe e que `npm run gerar:contrato` executado duas vezes não produz diff.
- [x] 5.2 Trocar os tipos de `tagApi.ts` e `tarefaApi.ts` por aliases dos tipos gerados, mantendo os nomes exportados, e corrigir os pontos de uso que o `tsc` apontar. Verificar com `cd frontend/frontend-web && npm run build` e `npm test`.
- [x] 5.3 Trocar `PageTarefa`, `PageHabito`, `PageProjeto` e o tipo de página de Decidir por aliases `PagedModelXxx`, e migrar `TarefasPage`, `HabitosPage`, `ProjetosPage`, `DecidirPage`, `OnboardingChecklist`, as atualizações otimistas e `test/revisaoFixtures.ts` para `page.totalElements`/`page.totalPages`. Verificar que os testes dessas telas cobrem contagem, paginação e lista vazia com o formato novo, e que `npm run test:coverage` respeita o limite.

## 6. MCP: tipos gerados

- [x] 6.1 Adicionar `openapi-typescript` (versão fixa, igual à do front) e o script `gerar:contrato` gerando `src/schema.d.ts`. Trocar `Prioridade`, `StatusTarefa`, `CreateTarefaInput` e `UpdateTarefaInput` por aliases. Verificar com `cd mcp && pnpm typecheck && pnpm test:coverage`, incluindo o teste do `listar_tarefas` com a página no formato novo.

## 7. Mobile: paginação e teste de contrato

- [x] 7.1 Migrar `Pagina<T>` e `PaginaTarefas` em `Modelos.kt` para `content` + `page` e calcular `last` a partir de `page.number`/`page.totalPages`. Ajustar `ExecutorLembrete.kt` e os demais usos. Verificar com `cd mobile && ./gradlew :shared:jvmTest`, incluindo os cenários de última página e página vazia.
- [x] 7.2 Criar `ContratoModelosTest` (D8), com o caminho do `openapi.json` vindo de `systemProperty` no `shared/build.gradle.kts`. Verificar com ≥ 7 cenários: modelos atuais passam; campo inexistente no schema, enum com valor faltando ou sobrando e campo não anulável × anulável falham nomeando modelo e campo; schema ausente na tabela e arquivo de contrato ausente produzem mensagem clara. Verificar com `:shared:jvmTest :shared:koverVerifyJvm`.

## 8. CI e documentação

- [x] 8.1 Adicionar ao job `validate` do `deploy.yml` a regeneração dos tipos do front e do MCP seguida de `git diff --exit-code` (D7). Verificar alterando `contract/openapi.json` sem regenerar os tipos num branch de teste: o job falha.
- [x] 8.2 Documentar o fluxo "mudou DTO → regenerar spec → regenerar tipos" com os comandos em `AGENTS.md` e nos READMEs de backend, front e MCP, e registrar a decisão em `Decisoes_arqutetura.md`. Verificar que os comandos documentados rodam como escritos.
- [x] 8.3 Elevar os limites de cobertura das stacks que subiram (catraca do `AGENTS.md`) e verificar que `mvn test`, `npm run test:coverage`, `pnpm test:coverage` e `koverVerifyJvm` passam com os novos pisos.

## 9. Integração

- [x] 9.1 Com backend no perfil `local`, front em `npm run dev` e MCP apontando para ele, verificar de ponta a ponta as listas de Tarefas, Hábitos, Projetos e Decidir (contagem e paginação), o CRUD de tags e a ferramenta `listar_tarefas` do MCP. Verificar também que `/v3/api-docs` sem perfil `local` não devolve o spec.

## Workflow follow-up

- Após o deploy em produção, gerar e instalar o APK novo imediatamente (ver Migration Plan no `design.md`).
- Abrir follow-up para migrar os demais domínios do front e do MCP para tipos gerados.
- Arquivar a change depois do merge.
