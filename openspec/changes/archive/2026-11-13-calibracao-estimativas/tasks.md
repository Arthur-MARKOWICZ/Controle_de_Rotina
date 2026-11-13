# Tasks

Regra de testes: cada unidade com lógica fecha com ≥ 7 cenários (1 feliz + 3 anomalias + 3 erros), `Clock` fixo nos limites BRT. Nomes no padrão `deve…Quando…`.

## 1. Backend — cálculo puro

- [x] 1.1 Adicionar `PeriodoBRT.intervaloUltimosDias(hoje, dias)` e cobrir em `PeriodoBRTTest` (caso normal, virada de mês/ano, meia-noite BRT, `dias <= 0` → `IllegalArgumentException`); verificar com `mvn test -Dtest=PeriodoBRTTest`
- [x] 1.2 Criar records de entrada/saída do cálculo e `CalibracaoCalculadora` (constantes 5 / 0,25 / 4,0; razão ponderada com `BigDecimal` HALF_UP; desvio médio; recortes geral/tag/projeto; ordenação da spec) em `historico/`
- [x] 1.3 Criar `CalibracaoCalculadoraTest` cobrindo: exemplo 1,25 / 15,0 da spec; lista vazia; 4 vs 5 amostras; outliers 0,24 / 0,25 / 4,0 / 4,01; `concluidoEm <= iniciadoEm`; tarefa com 2 tags; tarefa sem projeto; ordenação suficiente/insuficiente/empate por nome; entradas nulas → exceção do tipo concreto. Verificar com `mvn test -Dtest=CalibracaoCalculadoraTest`

## 2. Backend — consulta, service e endpoint

- [x] 2.1 Adicionar consulta de amostras em `ExecucaoRepository` (join `Execucao`/`BlocoPlano`/`Tarefa`, filtro por usuário, `CONCLUIDO`, janela, `iniciadoEm` não nulo) conforme design §2
- [x] 2.2 Criar DTOs `CalibracaoResponse` (com `Janela` aninhada), `MetricasCalibracaoResponse`, `RecorteTagCalibracaoResponse` e `RecorteProjetoCalibracaoResponse` em `historico/dto`
- [x] 2.3 Implementar o método de calibração no service (`@Transactional(readOnly = true)`, `Clock`, 4 queries fixas: amostras, `tarefa_tag`, tags, projetos) e expor `GET /historico/calibracao` em `HistoricoController`
- [x] 2.4 Criar `HistoricoCalibracaoIT` (Testcontainers, padrão de `HistoricoSemanaIT`, `Clock` fixo): 200 feliz com recortes; usuário B não vaza para A; abortada/em andamento/hábito/concluir-direto ignorados; limite da janela de 90 dias em BRT; sessão esquecida descartada; usuário novo → `amostras = 0` e listas vazias; tag apagada some e projeto arquivado aparece; 401 sem token, com token expirado no meio do fluxo e com token malformado (todos com `$.title = "Não autorizado"`). Verificar com `mvn test -Dtest=HistoricoCalibracaoIT`
- [x] 2.5 Rodar `cd backend && mvn test` e confirmar que o gate JaCoCo passa; se o piso medido subir, elevar `jacoco.minimo.*` no `pom.xml` (arredondado para baixo)

## 3. Proxy

- [x] 3.1 Incluir `'/historico/calibracao'` na lista de subpaths de `frontend/frontend-web/vite.config.ts` e nas linhas de `/historico/*` de `frontend/frontend-web/API_PROXY.md`; atualizar o comentário de prefixos do `nginx.conf` se ele listar subpaths. Verificar com `npm run dev` + `curl -i localhost:<porta>/historico/calibracao` devolvendo JSON/401, não `index.html`

## 4. Frontend — API e formatação

- [x] 4.1 Adicionar tipos `CalibracaoResponse`/recortes e `consultarCalibracao()` em `src/historico/api/historicoApi.ts` (fallback "Não foi possível carregar sua calibração.")
- [x] 4.2 Cobrir em `historicoApi.test.ts` a matriz padrão: 2xx, 400, 401→refresh, 404, 409, 500/timeout, HTML do proxy/JSON inválido (usar `vi.mocked(...)`, nunca `.mock` no export)
- [x] 4.3 Adicionar `formatarRazao` e `fraseCalibracao` em `src/historico/formatacao.ts` com testes em `formatacao.test.ts`: 1,4×; faixa 0,95–1,05 inclusiva; abaixo de 0,95; `null` → sem número; arredondamento 1,25 → "1,3×"

## 5. Frontend — cartão no Histórico

- [x] 5.1 Criar `src/historico/CartaoCalibracao.tsx` (skeleton, frase geral + "N blocos", até 3 recortes suficientes de tag com cor e de projeto, estado "Ainda sem dados suficientes" com "faltam X blocos concluídos com timer", `InlineAlert` no erro) e estilos em `src/styles/historico.css` reaproveitando os tokens existentes
- [x] 5.2 Criar `CartaoCalibracao.test.tsx`: feliz com recortes; em dia; sem dados (2 de 5, nenhum "×"); nenhum recorte suficiente → sem lista; mais de 3 recortes → só 3; erro 500 com mensagem; HTML do proxy; resposta com listas vazias
- [x] 5.3 Renderizar o cartão em `HistoricoPage.tsx` fora do efeito de período e estender `HistoricoPage.test.tsx`: cartão aparece em Dia e Semana; navegar de período não chama `consultarCalibracao` de novo; erro do cartão não esconde os indicadores
- [x] 5.4 Rodar `cd frontend/frontend-web && npm run build && npm run test:coverage`; confirmar typecheck dos testes e o gate; elevar `test.coverage.thresholds` se o piso medido subir

## 6. Verificação integrada

- [x] 6.1 Subir backend + front, concluir ≥ 5 blocos com timer em uma tarefa com tag/projeto e conferir no `/historico` o cartão com a razão geral e o recorte; anotar o `desvioMedioMinutos` geral como baseline da métrica de sucesso no PR
- [x] 6.2 `openspec validate calibracao-estimativas --strict` sem erros

## Workflow follow-up

- Abrir PR referenciando a issue #71 e registrar a baseline do desvio médio.
- Criar a issue da fase 2 (aplicar o fator no motor, opt-in) se ainda não existir.
- Arquivar a change após o merge (`/openspec-archive-change`).
