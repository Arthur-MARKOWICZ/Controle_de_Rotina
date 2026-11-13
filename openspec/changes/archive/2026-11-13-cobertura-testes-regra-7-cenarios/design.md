# Design

## Context

Estado observado (ver proposal.md — Why, e a issue #78 para o mapa completo):

- **Backend**: `backend/pom.xml` só tem `spring-boot-maven-plugin` e `maven-surefire-plugin` (que inclui `**/*Test.java` e `**/*IT.java`, ou seja, ITs com Testcontainers rodam em `mvn test`). Sem JaCoCo.
- Erros de domínio são `ErrorResponseException` com `title`/`detail` e propriedades específicas (ex.: `TagNomeDuplicadoException` → 409, `title="Tag já existe"`, propriedade `nome`). **Não existe** campo `code` nem `type` customizado no ProblemDetail hoje — o identificador estável do erro é o `title` (+ propriedade específica, quando houver).
- **Frontend web**: Vitest 5 configurado em `vite.config.ts` (`jsdom`, `globals`, `setup.ts`). `npm run build` = `tsc -b && vite build` typechecka testes → mocks via `vi.mocked(...)`.
- **MCP**: pnpm, Vitest 4, `vitest.config.ts` em `node`. Não roda no `deploy.yml`.
- **Mobile**: KMP com alvo `jvm()` só para `commonTest`; `ktor-client-mock` já é dependência de teste. CI próprio em `mobile.yml` (`:shared:jvmTest`), disparado só por mudanças em `mobile/**`.
- **CI**: `deploy.yml` job `validate` roda `mvn -B test`, `npm ci && npm test && npm run build`; o `deploy` depende dele.

## Goals / Non-Goals

**Goals:**
- Ferramentas de cobertura + gate nas 4 stacks sem quebrar o CI no dia da introdução.
- Fechar as lacunas listadas na issue em ordem de risco (🔴 antes de 🟡).
- Entregar em PRs pequenos, um por módulo/linha da issue, todos sob esta change.

**Non-Goals:**
- Mudar contratos de API ou comportamento de produto. Se um teste revelar bug, corrige-se no mesmo PR só quando não muda contrato; caso contrário abre-se issue separada e o teste fica `@Disabled`/`it.skip` com link.
- Introduzir campo `code` no ProblemDetail (seria mudança de contrato; fora de escopo).
- Testes E2E (Playwright/Espresso), mutation testing, testes de iOS.
- Cobrir `index.ts` do MCP (bootstrap stdio) — fica excluído da cobertura.

## Decisions

### D1. Identificador de erro nos asserts = status + `title` (+ propriedade específica)
Como não há `code`, testes de erro do backend verificam `status` **e** `jsonPath("$.title")` (e a propriedade específica quando existir, ex. `$.nome`, `$.tarefaId`). Em testes unitários de service, verifica-se o **tipo concreto** da exceção. Alternativa considerada: adicionar `code` a todas as exceções — rejeitada por ser mudança de contrato e escopo grande; pode ser feita depois sem reescrever os testes de status.

### D2. Contagem de cenários por unidade, não por arquivo
O mínimo de 7 é somado entre teste unitário e IT que exercitam a unidade. Evita duplicar ITs só para atingir números e permite que serviços pequenos sejam cobertos pelo IT do endpoint. Arquivos de teste com <7 cenários listados na issue são completados **quando a unidade que testam não atinge 7 no total**.

### D3. Ferramentas de cobertura
- **Backend**: `jacoco-maven-plugin` com `prepare-agent` + `report` + `check`, todos vinculados à fase `test` (o `check` depois do `report`), porque o CI roda `mvn test` e não `mvn verify` — assim o gate vale no comando atual. Exclusões: `*Application`, `**/dto/**` só se forem records sem lógica, classes geradas.
- **Frontend/MCP**: `@vitest/coverage-v8` (versão alinhada ao Vitest de cada pacote: 5.x no front, 4.x no MCP), `coverage.reporter = ['text-summary','html','lcov']`, `coverage.thresholds` no config, script `test:coverage`. Excluir `*.test.*`, `src/test/**`, `main.tsx`, tipos, `mcp/src/index.ts`.
- **Mobile**: plugin `org.jetbrains.kotlinx.kover` no `:shared`, task `koverHtmlReportJvm`/`koverVerify`. Escolhido por ser o padrão Kotlin e suportar KMP; JaCoCo em KMP exige configuração manual.

Alternativa: Istanbul no front — rejeitado, v8 é mais rápido e nativo do Vitest.

### D4. Gate em catraca (ratchet), alvo 70/60
Na introdução (fase 0), mede-se a cobertura atual de cada stack e o limite inicial é `min(70/60, baseline arredondado para baixo)`. A cada PR de módulo, o limite é elevado para o novo piso medido (arredondado para baixo). A change só é concluída quando todas as stacks estiverem com limite ≥70% linhas / ≥60% branches. Alternativa: começar já em 70/60 — rejeitada porque provavelmente quebraria o deploy imediatamente.

### D5. MCP no job `validate`
Usar `corepack enable` (já vem no Node 24) com o campo `packageManager` do `mcp/package.json` — evita uma action nova de terceiro — e a etapa `cd mcp && pnpm install --frozen-lockfile && pnpm test:coverage && pnpm typecheck`. Fica no mesmo job para bloquear o deploy (requisito da spec). Actions pinadas por SHA, como as existentes.

### D6. Relatórios como artefato
`actions/upload-artifact` (mesmo SHA usado em `mobile.yml`) com `if: ${{ !cancelled() }}`, coletando `backend/target/site/jacoco`, `frontend/frontend-web/coverage`, `mcp/coverage`. Mobile publica `mobile/shared/build/reports/kover` no `mobile.yml`.

### D7. Padrões de teste por camada
- **Backend IT**: reutilizar a base existente de IT com Testcontainers; para "estado não corrompido", reconsultar via repositório após a chamada.
- **Backend unit**: Mockito + `verify(repo, never()).save(...)` para anomalias.
- **Front**: `renderHook` para `usePlanoDoDia`/contexts; APIs mockadas com `vi.mocked`. Para cada `*Api.test.ts`, matriz padrão: 2xx, 400, 401→refresh, 404, 409, 500, HTML do proxy/JSON inválido.
- **Mobile**: `MockEngine` para `RotinaApi` com a mesma matriz (2xx, 401→refresh, 404, 409, timeout, JSON inválido, corpo vazio).
- **Concorrência** (XP, execução): seguir o padrão de `XpConcessaoConcorrenciaIT` / `PostgresAdvisoryLockIT` (executor + latch).
- **Tempo/BRT**: usar o `Clock` injetável já usado no projeto; nunca `now()` real nos asserts de limite (meia-noite, virada de semana/mês/ano).

## Risks / Trade-offs

- [CI mais lento com JaCoCo + MCP + ITs extras] → cobertura só instrumenta (overhead pequeno); MCP é rápido; monitorar o `timeout-minutes: 45`.
- [Gate quebra deploy por oscilação pequena] → arredondar limite para baixo ao inteiro; elevar só após merge.
- [Testes frágeis por depender de texto de `title`] → centralizar os títulos esperados como constantes nos testes, ou ler da própria exceção quando instanciável.
- [Volume grande de trabalho numa change só] → tarefas organizadas por módulo, cada grupo = 1 PR independente; a change fica "in-progress" até o último.
- [Bug real encontrado durante os testes] → ver Non-Goals: corrigir se não muda contrato; senão issue separada.

## Migration Plan

1. PR de infra (grupo 1 das tasks): ferramentas + baseline + gate em catraca + MCP no CI + docs.
2. PRs por módulo do backend 🔴, depois 🟡, cada um subindo o limite do backend.
3. PRs de front, MCP e mobile em paralelo aos de backend (arquivos disjuntos).
4. PR final elevando todos os limites a ≥70/60 e marcando critérios de aceite da issue.

Rollback: remover/abaixar `thresholds` é a única mudança com efeito no deploy; reverter o commit do gate restaura o CI anterior.

## Open Questions

- ~~Versão do Kover~~: resolvido — 0.9.9 (0.9.4 quebra com Kotlin 2.4: `compileKotlinTask` inexistente).
- Se `koverVerify` deve rodar também em PRs de `mobile.yml` (sim por padrão) ou só em `main`.
