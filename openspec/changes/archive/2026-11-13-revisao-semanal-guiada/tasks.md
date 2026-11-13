# Tasks

Regra de testes: cada unidade com lógica fecha com ≥ 7 cenários (1 feliz + 3 anomalias + 3 erros), `Clock` fixo nos limites BRT, nomes `deve…Quando…`. No front, mocks com `vi.mocked(...)`, nunca `.mock` no export tipado.

## 1. Backend — filtro `bloqueada` em `GET /tarefas`

- [x] 1.1 Adicionar `Boolean bloqueada` em `TarefaFiltro` e `@RequestParam(required = false) Boolean bloqueada` em `TarefaController.listar`; criar `TarefaSpecs.comIds`/`semIds`. Verificar com `mvn compile`
- [x] 1.2 Em `TarefaService.listar`, quando `bloqueada != null`: buscar sem paginação as tarefas do filtro base, obter `esperaService.idsTarefasBloqueadas`, somar `comIds`/`semIds` ao `Specification` e paginar (design §1); `bloqueada == null` mantém o caminho atual. Verificar com os testes de 1.3 e 1.4
- [x] 1.3 Criar teste unitário do filtro no service (Mockito): `true` só bloqueadas; `false` só livres; `null` não chama `idsTarefasBloqueadas`; filtro base vazio → página vazia sem chamar o enrich; combinação com `status=ATRASADA`. Verificar com `mvn test -Dtest=TarefaServiceListarBloqueadaTest`
- [x] 1.4 Criar `TarefaListarBloqueadaIT` (padrão de `EsperaBloqueioIT`): 200 com espera própria; bloqueio herdado de projeto e de pai entra; espera resolvida (alvo `FINALIZADA`) sai; outro usuário não vaza; 25 bloqueadas com `size=20` → 20 itens e `totalElements=25`; `bloqueada=talvez` → 400 com `$.title` de parâmetro inválido; 401 sem token e com token malformado. Verificar com `mvn test -Dtest=TarefaListarBloqueadaIT`
- [x] 1.5 Rodar `cd backend && mvn test` e confirmar o gate JaCoCo; elevar `jacoco.minimo.*` no `pom.xml` se o piso medido subir

## 2. Frontend — clientes de API

- [x] 2.1 Em `src/tarefa/api/tarefaApi.ts`, levar `bloqueada?: boolean` de `ListarTarefasParams` para a query (`true` e `false` explícitos; ausente não envia) e cobrir em `tarefaApi.test.ts` (query com `true`, com `false`, sem o parâmetro). Verificar com `npx vitest run src/tarefa/api`
- [x] 2.2 Criar `proximaSegunda(hojeIso)` em `src/revisao/datas.ts` (sobre `inicioSemana`/`adicionarDiasIso`) com `datas.test.ts`: dia de semana normal; domingo 23:30 BRT → amanhã; segunda 00:05 BRT → segunda seguinte; virada de mês e de ano; entrada inválida → erro. Verificar com `npx vitest run src/revisao/datas.test.ts`

## 3. Frontend — extração do formulário de promoção

- [x] 3.1 Extrair os campos e o submit de promoção de `DecidirPage.tsx` para `src/decidir/PromoverDecidirForm.tsx` (props: item, `onPromovido`, `onCancelar`) e usar o componente no `DecidirPage`; `DecidirPage.test.tsx` continua verde sem alteração de asserção
- [x] 3.2 Criar `PromoverDecidirForm.test.tsx`: promove só com título; envia duração/prioridade/prazo preenchidos; cancelar não chama a API; clique duplo envia uma vez; 409 (já promovido) mostra a mensagem do servidor; 400 de prazo no passado; 500 com fallback "Não foi possível promover o item.". Verificar com `npx vitest run src/decidir`

## 4. Frontend — página e passos da revisão

- [x] 4.1 Criar `src/revisao/RevisaoPage.tsx` com a máquina de passos (design §2): indicador "Passo N de 4", Continuar/Pular/Voltar, contador da sessão e `RevisaoConclusao`; estilos em `src/styles/revisao.css` com os tokens existentes
- [x] 4.2 Criar `PassoSemana.tsx` + teste: números da semana; semana vazia com Continuar ativo; erro com "Tentar de novo" e Pular; resposta HTML do proxy; sem `semanaInicio` na chamada (semana corrente do backend)
- [x] 4.3 Criar `PassoPendencias.tsx` + teste: grupos Atrasadas/Em espera com título do alvo; estado vazio; reagendar remove do grupo e incrementa o contador; descartar com confirmação cancelada não chama a API; prazo no passado (400) mantém o item com a mensagem; 404 (deletada em outra aba) mostra "não encontrada"; remover espera única tira do grupo; bloqueio herdado de projeto sem ação de remover espera
- [x] 4.4 Criar `PassoDecidir.tsx` (usa `PromoverDecidirForm`) + teste: promover tira da lista e incrementa o contador; fila vazia; destaque de `decidirAte` vencido; descartar exige confirmação; 409 de item já promovido sem criar tarefa; erro 500 ao listar
- [x] 4.5 Criar `PassoPlano.tsx` + teste: gerar a partir de `proximaSegunda` e mostrar os dias da proposta; aprovar chama `aceitarProposta` uma vez (botão desabilitado e `aria-busy`); não aprovar chama `descartarProposta`; 409 de semana já aprovada mostra o link para `/plano` sem aceitar nada; sem capacidade mostra a mensagem do planner e os itens não alocados; proposta já resolvida (erro de proposta inválida) volta a "Gerar plano"
- [x] 4.6 Criar `RevisaoPage.test.tsx`: fluxo feliz até "plano da semana aprovado"; pular os 4 passos → conclusão "plano não gerado" sem nenhuma chamada de escrita; Voltar do passo 3 recarrega o passo 2; resumo com 2 tarefas e 1 item tratados; 401 no meio do passo 3 → refresh e uma única promoção. Verificar com `npx vitest run src/revisao`

## 5. Frontend — navegação

- [x] 5.1 Registrar `/revisao` em `App.tsx` (bloco autenticado), título "Revisão da semana" em `documentTitle.ts`, item em `ITENS` de `MenuMais.tsx` e atalho em `HomePage.tsx`; estender `MenuMais.test.tsx`, `documentTitle.test.ts` e `HomePage.test.tsx` (link presente e navega para `/revisao`)
- [x] 5.2 Rodar `cd frontend/frontend-web && npm run build && npm run test:coverage`; confirmar o typecheck dos testes e o gate; elevar `test.coverage.thresholds` se o piso medido subir

## 6. Verificação integrada

- [x] 6.1 Subir backend + front com dados reais (uma tarefa atrasada, uma em espera, dois itens no Decidir) e percorrer `/revisao` até aprovar o plano, cronometrando; conferir em `/plano` os dias aprovados a partir da próxima segunda e registrar o tempo no PR (critério de ~5 minutos)
- [x] 6.2 `openspec validate revisao-semanal-guiada --strict` sem erros

## Workflow follow-up

- Abrir PR referenciando a issue #72.
- Quando a issue de lembretes existir, ligar o lembrete do ritual a `/revisao`.
- Arquivar a change após o merge (`/openspec-archive-change`).
