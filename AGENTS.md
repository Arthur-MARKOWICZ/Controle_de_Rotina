## O que é este projeto

Assistente de **planejamento automático de rotina** com **gamificação leve por XP**.

O usuário informa o que precisa fazer, quanto tempo estima e até quando precisa estar pronto. O sistema gera um plano executável; o usuário aprova ou ajusta.

**Diferencial:** motor de planejamento — não um Todoist com XP, nem um RPG de produtividade.

Documento de visão: `assistente_planejamento_rotina_gamificado.md`  
Decisões de arquitetura: `Decisoes_arqutetura.md`  
Planos de features: `plans/negocio/` (domínio/API), `plans/ui/` (UI/UX)  
Ordem dos slices: `ordem_execucao/docs.md`  
Skills de plano: `.agents/criar_plano` (escrever/reescrever), `.agents/revisao_plano` (criticar antes de fechar)  
Skill de UI/UX: `.cursor/skills/ui-ux-pro-max` (design system / recomendações visuais; CLI: `uipro`)  
OpenSpec (spec-driven): `.cursor/skills/openspec-*` — propor/aplicar/verificar/arquivar mudanças; specs em `openspec/` (CLI: `npx @fission-ai/openspec`)

## Proxy front → API (não esquecer)

Todo **prefixo novo** de controller (`@RequestMapping("/…")`) precisa entrar no proxy do front:

1. `frontend/frontend-web/vite.config.ts` (`server.proxy`) — `npm run dev`
2. `frontend/frontend-web/nginx.conf` — **rebuild** da imagem Docker do frontend
3. Atualizar a lista em `frontend/frontend-web/API_PROXY.md`

Sem isso, o front recebe `index.html` e aparece erro `Unexpected token '<', "<!doctype…"`.  
Detalhes e checklist: `frontend/frontend-web/API_PROXY.md`.

## Typecheck do front (Docker `npm run build`)

`npm run build` no frontend é `tsc -b && vite build`. O `tsc -b` typechecka **testes** (`src/**/*.test.ts(x)`).

Em mock do Vitest, **nunca** acesse `.mock` no export tipado (`tarefaApi.listarTarefas.mock`). Use `vi.mocked(...)`:

```ts
vi.mocked(tarefaApi.listarTarefas).mock.calls
vi.mocked(tarefaApi.listarTarefas).mockResolvedValue(...)
```

Sem isso o Docker quebra com `Property 'mock' does not exist on type`.  
Detalhes: `frontend/frontend-web/README.md`.

## Testes: regra mínima "1 + 3 + 3" (Definition of Done)

Toda unidade **com lógica** (service, motor, job, validator, filtro, endpoint, hook, componente com comportamento, utilitário, ViewModel) precisa de **≥ 7 cenários**, somando os arquivos que a testam diretamente (unitário + IT contam juntos). DTO/record sem regra, tipos e config declarativa ficam fora.

| Tipo | Qtd. | O teste verifica |
|---|---|---|
| ✅ Caminho feliz | ≥ 1 | uso normal → resultado esperado |
| ⚠️ Anomalia | ≥ 3 | recurso de outro usuário, chamada duplicada/concorrente, soft-deleted/arquivado, lista vazia, limites (0, máx., meia-noite BRT, virada de semana), token expirado no meio do fluxo, resposta vazia/malformada — **e que o estado (banco / tela) não foi alterado** |
| ❌ Erro esperado | ≥ 3 | **status e identificador do erro**: no backend, `status` + `$.title` do ProblemDetail (e a propriedade específica, ex. `$.nome`); em unitário, o **tipo concreto** da exceção; no cliente, a mensagem/estado de erro exibido. "Deu erro" não basta |

- Nome do teste diz condição e resultado: `deveRetornar409QuandoPlanoJaAprovado`, `naoDeveConcederXpDuasVezesParaMesmoEvento`.
- Tempo: use `Clock` fixo nos limites (meia-noite, virada de semana/mês/ano em BRT), nunca o relógio real.
- Matriz padrão para clientes de API (`*Api.test.ts`, `RotinaApi`): 2xx, 400, 401→refresh, 404, 409, 500/timeout, HTML do proxy / JSON inválido.

### Cobertura e gate (catraca)

| Stack | Comando | Relatório | Limite em |
|---|---|---|---|
| Backend | `cd backend && mvn test` | `target/site/jacoco/` | `pom.xml` (`jacoco.minimo.*`) |
| Frontend | `cd frontend/frontend-web && npm run test:coverage` | `coverage/` | `vite.config.ts` (`test.coverage.thresholds`) |
| MCP | `cd mcp && pnpm test:coverage` | `coverage/` | `vitest.config.ts` |
| Mobile | `cd mobile && ./gradlew :shared:jvmTest :shared:koverHtmlReportJvm :shared:koverVerifyJvm` | `shared/build/reports/kover/` | `shared/build.gradle.kts` (`kover {}`) |

O CI (`deploy.yml`, `mobile.yml`) falha abaixo do limite e publica os relatórios como artefato. **Limite só sobe, nunca desce:** ao fechar um PR de testes, eleve o limite da stack para o novo piso medido (arredondado para baixo). Alvo: **70% linhas / 60% branches** em todas as stacks.
