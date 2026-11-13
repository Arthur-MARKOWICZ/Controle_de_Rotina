# Proposal

## Why

O mapeamento da issue #78 mostrou cobertura de testes desigual entre as 4 stacks (backend Spring, frontend web, MCP e mobile KMP): módulos como `auth`, `xp`, `execucao`, `decidir`, `tag`, `calendario` e `historico` têm poucos cenários de erro, ~19 classes de lógica do backend e dezenas de arquivos do front não têm teste direto, nenhuma stack mede cobertura e os testes do MCP nem rodam no CI. Sem uma regra mínima e sem medição, regressões em regras de negócio (ownership, conflito de versão, idempotência de XP, refresh de token) passam despercebidas.

## What Changes

- Institui a **regra mínima de testes ("1 + 3 + 3")** como Definition of Done: toda unidade com lógica (service, motor, job, validator, filtro, endpoint, hook, componente com comportamento, utilitário, ViewModel) MUST ter ≥1 caminho feliz, ≥3 anomalias e ≥3 erros esperados.
- Define o que cada tipo de cenário precisa verificar: erro → status **e** identificador do problema (ProblemDetail `title`/`type` ou propriedade específica; exceção de domínio específica; mensagem na UI); anomalia → **estado não corrompido** (banco/tela inalterados).
- Adiciona **medição de cobertura** nas 4 stacks: JaCoCo (backend), `@vitest/coverage-v8` (frontend e MCP), Kover (mobile `:shared`).
- Inclui os **testes do MCP no CI** (`deploy.yml`, job `validate`) e publica os relatórios de cobertura como artefatos do CI.
- Adiciona **gate mínimo de cobertura** no CI (inicial: 70% linhas / 60% branches), com subida gradual.
- Documenta a regra em `AGENTS.md` e nos READMEs de teste.
- Completa a suíte por módulo, priorizando 🔴 (auth, xp, execucao, decidir, tag, calendario/historico, planner) e depois 🟡 (capacidade, habito, projeto, usuario/config), além de frontend (`usePlanoDoDia`, hábito, tarefa, decidir, espera, contexts), MCP e mobile (`RotinaApi`).
- Nenhuma mudança de comportamento de produto/API. Bugs encontrados ao escrever os testes são corrigidos no próprio PR do módulo, ou viram issue separada se mudarem contrato.

## Capabilities

### New Capabilities
- `qualidade-testes`: política de qualidade da suíte automatizada do projeto — regra mínima de cenários por unidade, o que asserts de erro/anomalia devem verificar, medição de cobertura por stack, execução de todas as suítes no CI e gate de cobertura.

### Modified Capabilities
<!-- Nenhuma: nenhum requisito de produto muda; os testes novos verificam os requisitos já existentes. -->

## Impact

- **Backend**: `backend/pom.xml` (plugin JaCoCo com `report` e `check`); novos/expandidos testes em `backend/src/test/java/com/controlerotina/**`.
- **Frontend web**: `frontend/frontend-web/package.json` (`@vitest/coverage-v8`, script `test:coverage`), `vite.config.ts` (`test.coverage`); novos testes em `src/**`. `tsc -b` typechecka os testes — mocks MUST usar `vi.mocked(...)`.
- **MCP**: `mcp/package.json` (`@vitest/coverage-v8`), `mcp/vitest.config.ts`; novos testes em `mcp/src/**`.
- **Mobile**: `mobile/gradle/libs.versions.toml` + `mobile/shared/build.gradle.kts` (plugin Kover); testes em `mobile/shared/src/commonTest` (já tem `ktor-client-mock`).
- **CI**: `.github/workflows/deploy.yml` (etapa MCP com pnpm, cobertura e upload de relatórios), `.github/workflows/mobile.yml` (Kover).
- **Docs**: `AGENTS.md`, `frontend/frontend-web/README.md`, `mcp/README.md`, `mobile/README.md`.
- Tempo de CI aumenta (instrumentação de cobertura + MCP); sem novas dependências de runtime.
