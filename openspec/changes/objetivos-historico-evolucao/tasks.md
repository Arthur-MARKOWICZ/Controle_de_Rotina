# Tasks

Backend a partir de `backend/` e front a partir de `frontend/frontend-web/`. Cada unidade com lógica segue o 1+3+3, e os testes de tempo usam `Clock` fixo. No front, mock sempre com `vi.mocked(...)`.

Pré-requisito: `objetivos-itens-vinculados-ui` e depois `objetivos-barra-progresso` arquivadas antes do apply (ver `design.md` › Migration Plan).

## 1. Data de finalização da tarefa

- [x] 1.1 Criar `V27__tarefa_finalizada_em.sql`: adiciona a coluna e preenche a partir de `xp_evento` `TAREFA_FINALIZADA` só para tarefas `FINALIZADA` (decisão 3). Mapear `finalizadaEm` em `Tarefa` com getter. Verificar com `mvn test` no IT da migration ou do repositório, usando dados semeados:
  - tarefa FINALIZADA com evento → data do evento;
  - FINALIZADA sem evento → nula;
  - PENDENTE com evento (reaberta) → nula.
- [x] 1.2 Em `Tarefa.atualizar`, preencher ou limpar `finalizadaEm` só quando o status muda (decisão 3). Verificar em teste unitário da entidade (`TarefaTest`), com ≥ 7 cenários:
  - feliz: PENDENTE → FINALIZADA grava o `updatedAt`;
  - FINALIZADA → FINALIZADA não muda a data;
  - FINALIZADA → PENDENTE limpa;
  - FINALIZADA → CANCELADA limpa;
  - refinalizar grava a data nova;
  - `status` nulo (edição de título) não mexe na data;
  - tarefa recém-criada começa sem data.
- [x] 1.3 Cobrir os caminhos de serviço em IT: finalizar por PATCH, por execução que completa a duração, pai em cascata ao finalizar o último filho, e reabrir o pai ao criar subtarefa (a data é limpa). Verificar com `mvn test -Dtest='TarefaIT,ExecucaoIT'` (ou os ITs equivalentes que já cobrem esses fluxos), lendo `finalizada_em` do repositório.

## 2. Listagem de encerrados

- [x] 2.1 Criar `V28__objetivo_encerrado_em.sql` (coluna gerada e índice) e mapear `encerradoEm` somente leitura em `Objetivo` (decisão 1). Verificar com `mvn test -Dtest=ObjetivoIT` subindo o contexto (`ddl-auto: validate` passa).
- [x] 2.2 Criar `CombinacaoStatusInvalidaException` (400, `title` "Combinação de status inválida"). Fazer `listar` aceitar `List<StatusObjetivo>`, com `ObjetivoSpecs.comStatus(Collection)` e `ordemPadrao` por conjunto (decisão 2). Verificar em `ObjetivoServiceTest` (exceção concreta e ordem) e em `ObjetivoIT` com os cenários da spec `objetivo` › "Listagem paginada por status":
  - feliz: encerrados juntos pela data de encerramento;
  - anomalias: concluído e depois arquivado na lista só de ARQUIVADO, status repetido igual, lista vazia, outro usuário;
  - erros: ATIVO com CONCLUIDO → 400 com `$.title`, `status` PAUSADO → 400 "Parâmetro inválido", sem token → 401.
- [x] 2.3 Regenerar o contrato (`mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true`) e verificar que `status` virou array em `contract/openapi.json`.

## 3. Evolução no backend

- [x] 3.1 Extrair `RegrasContagemObjetivo` (decisão 4) e fazer `ObjetivoProgressoService` usá-la. Verificar que `ObjetivoProgressoServiceTest` e os ITs de progresso passam sem alteração.
- [x] 3.2 Adicionar `ExecucaoRepository.listarMinutosConcluidosPorTarefas` (decisão 5). Verificar em IT de repositório: devolve `concluidoEm`, `plano.data` e minutos por execução `CONCLUIDO`, ignora outras situações e outro usuário.
- [x] 3.3 Criar `ObjetivoEvolucaoService` e os DTOs `ObjetivoEvolucaoResponse` e `PontoEvolucaoResponse` (decisão 5). Verificar em `ObjetivoEvolucaoServiceTest` com `Clock` fixo, cobrindo os cenários de `specs/objetivo-evolucao`:
  - feliz: dois dias de trabalho;
  - tarefa finalizada com saldo, execuções acima da duração, subtarefa na raiz, finalizada sem saldo;
  - antes do histórico (antiga à mão e só tarefas antigas);
  - hábito dentro da janela, antes do vínculo e com excesso;
  - pausado, cancelada e desvinculada somem;
  - virada de mês em BRT (2026-12-01T02:30Z → 30/11) e finalização às 23:59 BRT;
  - **invariante:** em cada cenário, o último acumulado é igual ao `minutosConcluidos` de `ObjetivoProgressoService`.
- [x] 3.4 Expor `GET /objetivos/{id}/evolucao` no `ObjetivoController`. Verificar em `ObjetivoEvolucaoIT`:
  - feliz com pontos e `antesDoHistorico`;
  - objetivo arquivado responde;
  - objetivo sem itens → `pontos` vazio;
  - reabrir uma tarefa a tira da curva;
  - 404 com `$.title` "Objetivo não encontrado" para outro usuário e para id inexistente;
  - 401 sem token.
- [x] 3.5 Regenerar o contrato e os tipos: `mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true`, depois `npm run gerar:contrato` no front e `pnpm gerar:contrato` no `mcp`. Verificar que `cd mcp && pnpm test` e `npm run build` no front passam com os tipos novos.
- [x] 3.6 Rodar `cd backend && mvn test`. Se a cobertura medida subir, elevar `jacoco.minimo.*` no `pom.xml` para o novo piso (arredondado para baixo).

## 4. Lista com Encerrados (front)

- [x] 4.1 Em `api/objetivoApi.ts`, mudar `listarObjetivos` para `status?: StatusObjetivo[]` (um `status` por valor) e adicionar `obterEvolucaoObjetivo(id)`. Verificar em `objetivoApi.test.ts`:
  - query com dois `status`;
  - a matriz padrão para `obterEvolucaoObjetivo`: 2xx, 400, 401→refresh, 404, 409, 500/timeout, HTML do proxy/JSON inválido.
- [x] 4.2 Em `prazoObjetivo.ts`, adicionar `rotuloArquivamento`. Verificar em `prazoObjetivo.test.ts`: data no ano corrente e em outro ano, valor nulo, data inválida e virada de ano em BRT.
- [x] 4.3 Em `ObjetivosPage.tsx`, aplicar as visões Ativos | Encerrados com `?visao=encerrados`, ler os `?status=` legados com `replace` e mudar o texto vazio para "Nenhum objetivo encerrado" (decisão 6). Verificar em `ObjetivosPage.test.tsx` os cenários de "Visões por status", "Estados de carregando, vazio e erro" e "Concluir e arquivar com confirmação" (arquivar um concluído continua em Encerrados), com a chamada `listarObjetivos` recebendo `['CONCLUIDO','ARQUIVADO']`.
- [x] 4.4 Em `ObjetivoCard.tsx`, mostrar "Concluído em" e/ou "Arquivado em" no encerrado. Verificar em `ObjetivoCard.test.tsx`:
  - arquivado direto mostra só "Arquivado em";
  - concluído e depois arquivado mostra os dois;
  - concluído mostra só a conclusão;
  - sem destaque de vencido em encerrado.

## 5. Evolução no detalhe (front)

- [x] 5.1 Criar `evolucaoObjetivo.ts::montarSerie` (decisão 7). Verificar em `evolucaoObjetivo.test.ts`:
  - feliz: ativo até hoje;
  - encerrado termina no encerramento, ponto depois do encerramento estende o eixo, marcador usa o arquivamento quando não há conclusão;
  - virada de mês;
  - só `antesDoHistorico` (sem degraus) e resposta vazia.
- [x] 5.2 Criar `ObjetivoEvolucao.tsx` (SVG em degraus, legenda "antes do histórico", marcador com texto, tabela `visually-hidden`) e os estilos em `styles/objetivos.css`. Verificar em `ObjetivoEvolucao.test.tsx`:
  - total acumulado em texto;
  - tabela com uma linha por ponto;
  - "antes do histórico";
  - marcador com texto;
  - estado de carregando;
  - erro com "Tentar de novo" chamando o callback;
  - nada renderizado quando vazio.
- [x] 5.3 Em `ObjetivoDetalhePage.tsx`, buscar a evolução em paralelo com os itens, posicionar a seção entre o progresso e os itens e reler após salvar no drawer de edição. Verificar em `ObjetivoDetalhePage.test.tsx`:
  - a seção aparece na ordem certa;
  - um 500 na evolução não derruba cabeçalho, barra nem itens;
  - "Tentar de novo" recarrega;
  - concluir e reabrir no detalhe releem a evolução e o marcador de encerramento aparece;
  - HTML do proxy mostra o erro da seção.
- [x] 5.4 Rodar `npm run build` e `npm run test:coverage`. Se a cobertura medida subir, elevar `test.coverage.thresholds` em `vite.config.ts`. Conferir no navegador (`npm run dev` com o backend local), em 1280×720 e em 375px:
  - lista em Encerrados com datas;
  - link `?status=ARQUIVADO` antigo;
  - detalhe com curva, com só "antes do histórico" e de um objetivo encerrado com marcador.

## Workflow follow-up

- Fechar a issue #95 no PR, marcando os critérios de aceite. O critério "endpoint de encerrados" é atendido por `GET /objetivos?status=CONCLUIDO&status=ARQUIVADO`.
- Arquivar a change depois do merge.
