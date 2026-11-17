# Design

## Context

Ver `proposal.md` (Why) e `specs/contrato-api/spec.md` (requisitos). Restrições que moldam a solução:

- Backend em Spring Boot 4.1.1 / Java 25, com 69 records `*Request`/`*Response`, sem nenhuma anotação de nulidade. A `SecurityConfig` termina em `anyRequest().authenticated()`. Os ITs já sobem a aplicação com Testcontainers (Postgres).
- O build Docker do front usa `frontend/frontend-web` como contexto, e o do backend usa `backend`. Nenhum dos dois enxerga um arquivo na raiz do repositório.
- `npm run build` no front roda `tsc -b`, que typechecka também os testes.
- O MCP repassa o JSON das respostas ao LLM (`JSON.stringify`). O mobile usa `kotlinx.serialization` com `ignoreUnknownKeys`, então os modelos são subconjuntos deliberados dos schemas.
- Não existe arquivo de perfil `prod`: a produção roda com o `application.yml` base (`SPRING_PROFILES_ACTIVE=prod` sem arquivo próprio).

## Goals / Non-Goals

**Goals:**
- Fazer com que divergência entre backend e clientes falhe no CI, e não em runtime.
- Manter mínimo o código próprio de geração: uma configuração no backend, um customizer, um IT e scripts `openapi-typescript`.
- Não alterar os nomes de tipos que o front já importa em ~50 arquivos.

**Non-Goals:**
- Gerar funções de cliente HTTP (`openapi-fetch` e similares). Os `*Api.ts` continuam escritos à mão.
- Descrever erros (`ProblemDetail`) por endpoint no spec além do que o springdoc infere.
- Versionar a API (`/v2`) ou manter compatibilidade com o formato antigo de paginação.

## Decisions

### D1. springdoc 3.2.0 só com `webmvc-api`
Usamos `springdoc-openapi-starter-webmvc-api:3.2.0`, que é compilado contra o Boot 4.1.2, sem o artefato de UI. Fixamos `springdoc.api-docs.version=openapi_3_1`, porque o 3.1 representa nulidade como `type: [X, "null"]`, que o `openapi-typescript` traduz para `X | null`.
*Alternativa:* escrever o spec à mão (contract-first). Descartada porque duplicaria os 69 records e criaria uma quarta fonte de verdade.

### D2. Spec gerado por um IT e versionado em `contract/openapi.json`
`ContratoOpenApiIT` (`@SpringBootTest` + Testcontainers, no mesmo padrão dos ITs atuais) autentica um usuário, faz `GET /v3/api-docs` e normaliza o JSON: ordena as chaves, imprime de forma indentada e **remove `servers`**, que traz a porta aleatória do teste. Depois compara o resultado com `contract/openapi.json`:
- Com `-Dcontrato.atualizar=true`, o IT grava o arquivo em vez de comparar. O comando documentado é `cd backend && mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true`.
- Sem a flag, uma diferença faz o teste falhar com esse comando na mensagem.

Como a verificação vive no `mvn test`, o CI já a cobre sem job novo.
*Alternativas:* o `springdoc-openapi-maven-plugin` exigiria a aplicação no ar com Postgres durante o `mvn verify`. Gerar o spec só no CI, como artefato, não daria um arquivo versionado para os clientes consumirem no build local.

### D3. Endpoint do spec desligado por padrão
No `application.yml` base vai `springdoc.api-docs.enabled=false`, o que vale para produção, já que ela não tem arquivo de perfil. O endpoint é ligado em `src/test/resources/application.yml` e em `application-local.yml`. A `SecurityConfig` não muda: mesmo quando ligado, `/v3/api-docs` exige autenticação, e o IT faz login antes.
*Alternativa:* `permitAll` em `/v3/api-docs` com o endpoint desligado em produção. Descartada porque abre a superfície sem ganho.

### D4. Obrigatoriedade por papel do schema (resposta × requisição)
- **Nulidade:** os componentes de record que podem ser nulos recebem `@org.jspecify.annotations.Nullable`. O JSpecify já vem transitivamente no Spring 7. Ligamos `springdoc.model-converters.java-nullable-property-customizer.enabled=true` (springdoc #3382) para virarem `nullable`.
- **Respostas:** um `OpenApiCustomizer` próprio (`ContratoObrigatoriedadeCustomizer`) percorre `paths.*.*.responses.*.content.*.schema`, segue os `$ref` transitivamente (inclusive `items`, `allOf` e propriedades) e define `required = todas as propriedades` em cada schema alcançado. Isso espelha o Jackson, que serializa todos os campos, nulos inclusive (o projeto não usa `NON_NULL`).
- **Requisições:** schemas alcançados só por `requestBody` ficam como o springdoc gera, com `required` vindo de `@NotNull`/`@NotBlank`/`@NotEmpty`. Assim o `PATCH` parcial continua válido.
- **PATCH com presença** (`Atualizar{Tarefa,Habito,Decidir,Projeto}Request`): as flags internas `*Presente` ficam fora do contrato (`@Schema(hidden = true)`), e o campo que aceita `null` para limpar (ex.: `projetoId`, `tagIds`) recebe `@Nullable`. Os demais `null` em PATCH continuam significando "não alterar" e não entram no contrato. *(Ajuste feito na implementação.)*
- Um schema usado nos dois papéis é tratado como resposta. Esses casos são revisados manualmente na tarefa de nulidade, e não se espera nenhum.

*Alternativa:* `PropertyCustomizer` marcando todo componente de record como obrigatório. Descartada porque não sabe distinguir requisição de resposta e quebraria os `PATCH`.

### D5. Paginação explícita com `PagedModel<T>`
Os quatro controllers (`TarefaController`, `HabitoController`, `ProjetoController`, `DecidirController`) passam a devolver `PagedModel<T>` (`new PagedModel<>(page)`) em vez de `Page<T>`. O tipo de retorno explícito garante que o springdoc documente exatamente o JSON emitido, e o springdoc já marca as propriedades do `PagedModel` como required (#3360).
*Alternativa:* `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` global, mantendo `Page<T>` nas assinaturas. Descartada porque a assinatura continuaria mentindo sobre o formato e o springdoc poderia documentar `PageImpl`.

### D6. Tipos TS gerados e **commitados**; aliases no `*Api.ts`
- O front ganha o script `gerar:contrato` (`openapi-typescript ../../contract/openapi.json -o src/api/schema.d.ts`), e o MCP ganha o equivalente (`../contract/openapi.json -o src/schema.d.ts`). A versão de `openapi-typescript` é **fixa** (sem `^`), para a saída não mudar entre máquinas.
- Os `.d.ts` são commitados porque o build Docker do front não enxerga `contract/` (ver Context).
- `tagApi.ts` e `tarefaApi.ts` trocam as declarações por aliases que mantêm os nomes atuais, por exemplo `export type TagResumo = components['schemas']['TagResumoResponse']`. Os tipos de página (`PageTarefa`, `PageHabito`, `PageProjeto` e o equivalente de Decidir) viram `components['schemas']['PagedModelXxx']`. Tipos exclusivos do front (`SortCampo`, `ListarTarefasParams`) continuam escritos à mão.
- Habito, Projeto e Decidir continuam com os itens tipados à mão, então a página deles usa o genérico `Pagina<T>` (`src/api/pagina.ts`), com `PageMetadata` gerado, em vez de `PagedModelXxx` (que traria o item gerado junto). *(Ajuste feito na implementação.)*
- No MCP, `Prioridade`, `StatusTarefa`, `CreateTarefaInput` e `UpdateTarefaInput` viram aliases.

*Alternativa:* gerar os tipos no `postinstall` ou no build. Descartada pelo contexto Docker e porque esconderia o diff de tipos no PR.

### D7. Checagem de tipos desatualizados no CI
No job `validate` do `deploy.yml`, depois dos testes, o CI roda `npm run gerar:contrato` (front) e `pnpm gerar:contrato` (MCP) e depois `git diff --exit-code -- frontend/frontend-web/src/api/schema.d.ts mcp/src/schema.d.ts`. Que o `openapi.json` está atualizado já é garantido pelo D2.

### D8. Teste de contrato do Kotlin por descritores de serialização
Um `jvmTest` (`ContratoModelosTest`) lê `contract/openapi.json`. O caminho vem por `systemProperty` configurada no `shared/build.gradle.kts` a partir do `rootProject`, que só existe em JVM. O teste usa uma tabela explícita *modelo Kotlin → schema* (`Tarefa → TarefaResponse`, `Pagina<T> → PagedModel…`) e, para cada par, percorre o `serialDescriptor`:
- todo elemento do modelo existe nas `properties` do schema;
- um elemento não anulável no modelo é `required` e não aceita `null` no schema;
- enums: o conjunto de valores é igual nos dois lados.

As mensagens nomeiam o modelo e o campo ou valor. Modelos sem par na tabela são ignorados, e adicionar um par fica como checklist do PR.
*Alternativa:* gerar `Modelos.kt`. Fora de escopo pela issue, porque o ecossistema de geração para KMP é imaturo.

### D9. Ordem de implementação
1. springdoc + D3 + D4 + IT (D2): primeiro `openapi.json` versionado, ainda com `Page`.
2. D5: o diff do `openapi.json` no PR mostra a quebra de paginação isolada.
3. D6 e D7 (front e MCP), incluindo as telas que leem `totalElements`/`totalPages`.
4. Mobile: modelos de página + D8.

## Risks / Trade-offs

- [`@Nullable` faltando num campo que pode ser nulo] → o TS confia em `string` e quebra em runtime. *Mitigação:* a tarefa de nulidade parte dos ~60 campos `= null` do `Modelos.kt` e dos `| null` / `?:` do front e confere cada um com o código que monta o record (`from(...)`).
- [Saída do springdoc não determinística, por exemplo a ordem de `required`] → IT intermitente. *Mitigação:* normalizar no IT, ordenando chaves e também os arrays `required` e `enum`.
- [O customizer de obrigatoriedade não seguir algum `$ref` (genéricos, `oneOf`)] → campo de resposta opcional no TS. *Mitigação:* o customizer tem testes unitários próprios (regra 1 + 3 + 3) com `$ref` aninhado, `items`, ciclo e schema compartilhado.
- [APK antigo instalado quebra na listagem paginada] → lista de tarefas e lembrete de prazo errados no celular. *Mitigação:* ver Migration Plan.
- [`openapi-typescript` com versões diferentes local × CI] → diff falso no D7. *Mitigação:* versão fixa e lockfiles commitados.
- [Nomes de schema gerados para genéricos (`PagedModelTarefaResponse`) mudarem com upgrade do springdoc] → aliases quebram no build. Aceito: é exatamente o tipo de mudança que deve aparecer no PR de upgrade.

## Migration Plan

1. Fazer o merge na `main`. O deploy publica backend e front juntos (mesmo `docker compose`), e o MCP local é atualizado com `git pull` + build.
2. Logo depois do deploy, gerar e instalar o APK novo (`mobile.yml` / `assembleDebug`). Entre o deploy e a instalação, a lista de tarefas e o lembrete de prazo do app antigo ficam incorretos.
3. **Rollback:** reverter o merge e refazer o deploy. O APK antigo volta a funcionar, e o APK novo fica quebrado até ser reinstalado o anterior. Não há migração de banco.

## Open Questions

- A lista exata de campos `@Nullable` só sai da revisão na tarefa correspondente. Isso não muda a abordagem.
