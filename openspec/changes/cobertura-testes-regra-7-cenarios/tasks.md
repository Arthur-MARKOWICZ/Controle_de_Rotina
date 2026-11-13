# Tasks

> Cada grupo = 1 PR independente (ver design.md — Migration Plan). Regra em todos os grupos de testes: por unidade, ≥1 caminho feliz, ≥3 anomalias (com assert de estado não corrompido) e ≥3 erros (status + `title`/propriedade do ProblemDetail ou tipo concreto da exceção). Ao fim de cada grupo, rodar a cobertura da stack e elevar o limite do gate para o novo piso (arredondado para baixo), nunca reduzindo.

## 1. Infra de cobertura, CI e documentação

- [x] 1.1 Adicionar `jacoco-maven-plugin` ao `backend/pom.xml` (`prepare-agent`, `report`, `check` na fase `test`; exclusões de `*Application` e classes sem lógica) e verificar que `mvn -B test` gera `backend/target/site/jacoco/index.html`
- [x] 1.2 Adicionar `@vitest/coverage-v8` (5.x) ao frontend, bloco `test.coverage` em `vite.config.ts` (reporters `text-summary`, `html`, `lcov`; exclusões de testes, `src/test/**`, `main.tsx`, tipos) e script `test:coverage`; verificar que `npm run test:coverage` gera `coverage/` e que `npm run build` continua verde
- [x] 1.3 Adicionar `@vitest/coverage-v8` (4.x) ao MCP, `coverage` em `mcp/vitest.config.ts` (excluir `src/index.ts`), script `test:coverage` e campo `packageManager` com a versão do pnpm; verificar que `pnpm test:coverage` gera `mcp/coverage/`
- [x] 1.4 Adicionar plugin Kover em `mobile/gradle/libs.versions.toml` e `mobile/shared/build.gradle.kts` (variante JVM); verificar que `./gradlew :shared:koverHtmlReportJvm` gera relatório em `mobile/shared/build/reports/kover`
- [x] 1.5 Medir a baseline de linhas/branches das 4 stacks, registrar os números na descrição do PR e configurar os thresholds iniciais como `min(70/60, baseline arredondado para baixo)`; verificar que subir temporariamente um threshold acima da baseline faz o comando falhar
- [ ] 1.6 Incluir no job `validate` do `deploy.yml`: pnpm via `corepack enable` + `packageManager`, `cd mcp && pnpm install --frozen-lockfile && pnpm typecheck && pnpm test:coverage`; trocar backend/front para gerar cobertura; verificar num PR que um teste do MCP quebrado falha o job e bloqueia o `deploy`
- [ ] 1.7 Publicar relatórios de cobertura como artefato (`upload-artifact` com `if: ${{ !cancelled() }}`) no `deploy.yml` (backend, front, MCP) e rodar `koverVerify` + publicar relatório Kover no `mobile.yml`; verificar que o artefato aparece numa execução com falha
- [x] 1.8 Documentar a regra "1 feliz + 3 anomalias + 3 erros", o que cada tipo verifica (D1, D7) e os comandos de cobertura em `AGENTS.md`, e referenciar em `frontend/frontend-web/README.md`, `mcp/README.md` e `mobile/README.md`; verificar que os comandos documentados rodam como escritos

## 2. Backend 🔴 — auth

- [ ] 2.1 Completar `AuthServiceLoginTest`/`AuthService`: login ok; e-mail inexistente, senha errada, usuário inativo; refresh reutilizado, refresh expirado, logout duplo (anomalia: tokens/refresh no banco inalterados); verificar ≥7 cenários com `mvn -Dtest='Auth*' test`
- [ ] 2.2 Criar testes de `JwtAuthenticationFilter`: token válido; sem header, `Bearer` malformado, token expirado, assinatura inválida, token revogado (contexto de segurança vazio, cadeia segue sem autenticar); verificar ≥7 cenários
- [ ] 2.3 Criar testes de `RefreshCookieFactory`, `RefreshTokenService` e `RefreshTokenLimpezaJob` (atributos do cookie, rotação, token de outro usuário, limpeza só de expirados/revogados, lista vazia) e completar `AccessTokenRevogacaoServiceTest`, `AuthRateLimitIT`, `AuthRateLimitFilterTest`, `IpRateLimiterTest`, `JwtSecretValidatorTest`; verificar ≥7 por unidade e subir o gate do backend

## 3. Backend 🔴 — xp e execucao

- [ ] 3.1 Testes de erro/anomalia em `XpConcessaoService`/`XpMotor`: evento duplicado → `XpEventoDuplicadoException` (saldo inalterado), origem inexistente, XP zero/negativo, concorrência (completar `XpConcessaoConcorrenciaIT`); completar `HabitoXpTest`, `XpProgressoResponseTest` se tiverem lógica; verificar ≥7 cenários com `mvn -Dtest='Xp*' test`
- [ ] 3.2 Completar `ExecucaoIniciarIT`, `ExecucaoAbortarIT`, `ExecucaoConsultaIT`, `ExecucaoConcluirIT`, `ExecucaoPersistenciaIT`: execução de outro usuário, abortar já concluída, iniciar com plano não aprovado, bloco inexistente, dia sem plano, chamada duplicada (estado da execução inalterado); verificar ≥7 por endpoint e subir o gate

## 4. Backend 🔴 — decidir, tag, calendario, historico

- [ ] 4.1 Testes de `DecidirService` e `/promover`: promover ok; já promovido, item de outro usuário, item deletado; payload inválido (400 + `title`), 404, 409; verificar ≥7 cenários e que nenhuma tarefa extra é criada nas anomalias
- [ ] 4.2 Testes de `TagService`: nome duplicado com case/acento diferente (409 `TagNomeDuplicadoException`), tag de outro usuário, nome vazio/longo demais, excluir tag vinculada (vínculos removidos), lista vazia; completar `TagVinculoIT`; verificar ≥7 cenários
- [ ] 4.3 Testes de `CalendarioService`/`CalendarioIT`/`CalendarioAgregadorTest` e `HistoricoService`/`HistoricoAgregadorTest`/`HistoricoSemanaIT`/`HistoricoDiaIT`: período invertido, período acima do limite, semana que não começa na segunda (`SemanaInicioInvalidoException`), virada de mês/ano em BRT com `Clock` fixo, usuário sem dados; completar `PeriodoBRTTest`; verificar ≥7 por unidade e subir o gate

## 5. Backend 🔴 — planner (consulta, aprovação, proposta)

- [ ] 5.1 Testes de `PlanoConsultaService`, `PlanoAprovacaoService` e `PropostaService`: aprovar ok; aprovar já aprovado, versão desatualizada (409), proposta inexistente/descartada, plano de outro usuário (plano no banco inalterado); completar `PlanoPropostaIT`, `PlanoPersistenciaIT`, `PlanoGeracaoEsperaIT`, `PlannerGeracaoDiariaJob*`, `BlocoPlanoXorTest`, `ScoreV1Test`; verificar ≥7 por unidade com `mvn -Dtest='Plano*,Planner*,Proposta*' test` e subir o gate

## 6. Backend 🟡 — capacidade, habito, projeto, usuario/config

- [ ] 6.1 Testes de `CapacidadeSemanalService`, `CapacidadeOverrideService`, `CapacidadeResolvidaService`: intervalo inválido, conflito (`CapacidadeOverrideConflitoException`), lote vazio/duplicado (`DataDuplicadaNoLoteException`), restaurar não deletado, abaixo das reservas; verificar ≥7 por service
- [ ] 6.2 Testes de `HabitoService`, `HabitoDoDiaService`, `HabitoExecucaoAvulsaService`: pausar já pausado, reativar ativo, hábito deletado, `/do-dia` sem hábitos, frequência na virada do dia (`Clock`), `HabitoNaoElegivelException`; completar `HabitoExecucaoAvulsaIT`, `HabitoSlotAvulsoServiceTest`, `HabitoResumoResponseTest`; verificar ≥7 por unidade
- [ ] 6.3 Testes de `ProjetoService` e `ReservaProjetoService` (completar `ReservaProjetoServiceValidacaoTest`): reserva duplicada, capacidade insuficiente, projeto arquivado, reserva de outro usuário; verificar ≥7 por service
- [ ] 6.4 Testes de `UsuarioService` (completar `UsuarioServiceTest`, `CadastroUsuarioIT`), `SecurityConfig` (rotas públicas vs protegidas → 401, CORS permitido/negado), `DuracaoMinutosValidaValidator`, `SenhaForteValidator`; verificar ≥7 por unidade
- [ ] 6.5 Completar os demais arquivos da issue com <7 cenários cuja unidade não atinja 7 no total (`TarefaAtrasoJobTest`, `TarefaResponseTest`, `TarefaServiceProcessarAtrasosTest`, `ReordenacaoTest`, `PostgresAdvisoryLockIT`); verificar com `mvn -B test` verde e subir o gate do backend para ≥70% linhas / ≥60% branches

## 7. Frontend web — núcleo e hábito/tarefa

- [ ] 7.1 Testar `home/usePlanoDoDia.ts` com `renderHook`: carregar, gerar, aprovar; ajuste otimista com rollback em erro, 409 de versão, rede fora, resposta vazia (estado anterior preservado); verificar ≥7 cenários com `npx vitest run src/home`
- [ ] 7.2 Testar `HabitoDrawer`, `HabitoCard`, `HabitoDoDiaLista`, `HabitoCampos` e `habito/api/habitoApi.ts` (matriz de API: 2xx, 400, 401→refresh, 404, 409, 500, HTML do proxy); verificar ≥7 por unidade
- [ ] 7.3 Testar `FiltrosBarra`, `TarefaCard`, `TarefaSubtarefasSection`, `InlineAddTarefa`, `EdicoesAvancadasDialog`, `tarefa/datetime.ts`, `tarefa/cascataConfirmacao.ts`; verificar ≥7 por unidade e `npm run build` verde (mocks com `vi.mocked`)

## 8. Frontend web — demais módulos e arquivos rasos

- [ ] 8.1 Testar `pendencia/EsperaSection`, `decidir/DecidirDrawer`, `InlineAddDecidir`, `DecidirForm`, `execucao/SessaoAtivaContext`, `projeto/ProjetoDrawer`, `ProjetoCampos`, `validation/projetoForm.ts`; verificar ≥7 por unidade
- [ ] 8.2 Testar `useListaArrastavel`, `useAtalhosTeclado`, `QuickAddContext`, `MenuConta`, `MenuMais`, `PeriodoNav`, `AjustarCapacidadeHoje`, `AuthContext`, `XpProgressContext`, `TagFiltroPopover`, `feedback/Popover`, `useConfirmDialog`, `planner/blocoOrigem.ts`, `focarPrimeiroInvalido.ts`; verificar ≥7 por unidade
- [ ] 8.3 Completar os ~40 arquivos com 1–3 `it()` listados na issue até 7 (cada `*Api.test.ts` com a matriz de API do 7.2); verificar `npm test && npm run build` verdes e subir o gate do front para ≥70/60

## 9. MCP

- [ ] 9.1 Completar `config`, `login-flow`, `server`, `session-store`, `cookies`, `terminal-prompt` até ≥7 cenários cada (anomalias: sessão expirada no meio do fluxo, arquivo de sessão corrompido, cookie ausente/malformado; erros: 401/404/409 da API propagados com mensagem); verificar com `pnpm test`
- [ ] 9.2 Testar `login-cli.ts` (fluxo ok, credenciais inválidas, API fora, entrada vazia); verificar ≥7 cenários e subir o gate do MCP para ≥70/60

## 10. Mobile

- [ ] 10.1 Testar `api/RotinaApi.kt` com `MockEngine`: 2xx, 401→refresh, 404, 409, timeout, JSON inválido, corpo vazio; verificar com `./gradlew :shared:jvmTest`
- [ ] 10.2 Completar `RegrasTest`, `HabitosViewModelTest`, `RefreshCookieTest` até ≥7 e `KeystoreTokenStoreTest`/`SecureTokenStore` (instrumentado) até ≥7; verificar `:shared:jvmTest` verde e subir o gate Kover para ≥70/60

## 11. Verificação final

- [ ] 11.1 Conferir os critérios de aceite da issue #78: rodar `mvn -B test`, `npm test && npm run build` (front), `pnpm test:coverage` (MCP), `./gradlew :shared:jvmTest :shared:koverVerify` (mobile) todos verdes, thresholds ≥70% linhas / ≥60% branches nas 4 stacks e relatórios publicados no CI

## Workflow follow-up

- Fechar a issue #78 após o merge do último PR.
- Arquivar a change (`/openspec-archive-change`) para criar `openspec/specs/qualidade-testes/spec.md`.
