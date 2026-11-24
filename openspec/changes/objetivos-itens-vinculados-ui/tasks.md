# Tasks

Os comandos rodam a partir de `frontend/frontend-web`. Nos testes, mock sempre com `vi.mocked(...)`. Cada unidade com lógica segue o 1+3+3.

## 1. Cliente e peças compartilhadas

- [x] 1.1 Adicionar a `objetivoApi.ts`:
  - os tipos `ObjetivoItens` e `ObjetivoResumo`;
  - `listarItensObjetivo(id)`, com o fallback "Não foi possível carregar os itens vinculados.".

  Verificar em `objetivoApi.test.ts` a matriz padrão (2xx com os três grupos, 2xx vazio, 401→refresh, 404 com detail, 500/timeout, HTML do proxy). Rodar `npx vitest run src/objetivo/api`.
- [x] 1.2 Extrair de `ObjetivoCard.tsx` para `src/objetivo/acoesObjetivo.ts`:
  - `ACOES_POR_STATUS`, `ROTULO_ACAO` e os textos de confirmação e de toast;
  - o card passa a importar de lá.

  Verificar em `acoesObjetivo.test.ts` a matriz status × ações, e que `ObjetivoCard.test.tsx` continua verde.

## 2. Rotas, card e drawer de edição

- [x] 2.1 Mexer nas rotas:
  - Em `App.tsx`, tirar `:id` de baixo de `ObjetivosPage` e criar `/objetivos/:id` (`ObjetivoDetalhePage`, por enquanto um stub com `h1` e `<Outlet context={{ aoSalvar }}>`) com o filho `editar` (`ObjetivoDrawer`).
  - Em `documentTitle.ts`: "Objetivo" para `/objetivos/:id` e "Editar objetivo" para `/objetivos/:id/editar`.

  Verificar `documentTitle.test.ts`.
- [x] 2.2 Ajustar o foco no `AppShell`, conforme a decisão 4 do design: na lista, `drawer` passa a casar só com `/objetivos/nova`; detalhe ↔ editar com o mesmo id não move o foco. Verificar em `AppShell.test.tsx`:
  - lista → detalhe foca o `h1`;
  - detalhe → `editar` não move o foco;
  - `editar` → detalhe não move o foco;
  - detalhe A → detalhe B foca o `h1`;
  - `/objetivos` → `/objetivos/nova` continua sem mover o foco.
- [x] 2.3 Ajustar o `ObjetivoCard`:
  - o título vira `Link` para `/objetivos/:id`;
  - "Editar" leva a `/objetivos/:id/editar`;
  - os dois com `state: { listaSearch: location.search }`, sem `daLista`.

  Verificar em `ObjetivoCard.test.tsx` o href e o state dos dois links.
- [x] 2.4 Mudar o fechamento do `ObjetivoDrawer` na edição para `navigate('/objetivos/:id', { replace: true, state })`, preservando `listaSearch`. A criação fica igual. Atualizar `ObjetivoDrawer.test.tsx` para as rotas novas, com estes cenários:
  - editar título → toast "Objetivo atualizado" e `aoSalvar` chamado;
  - fechar sem alteração → volta para `/objetivos/:id`;
  - Esc com alteração → pede descarte;
  - 404 → sem campos;
  - fechar a criação continua voltando à visão de origem.

  Verificar também que `ObjetivosPage.test.tsx` continua verde.

## 3. Página de detalhe

- [x] 3.1 Criar `src/objetivo/ObjetivoItensSecao.tsx`:
  - grupos Tarefas, Hábitos e Projetos, com contagem, sem renderizar grupo vazio;
  - links para `/tarefas/:id`, `/habitos/:id` e `/projetos/:id`;
  - selo "via projeto {nome}" em `VIA_PROJETO`;
  - mapas de status próprios;
  - estados de carregando, vazio e erro.

  Verificar em `ObjetivoItensSecao.test.tsx`:
  - feliz: os três grupos, com o selo só na herdada;
  - anomalias: tudo vazio mostra o estado vazio, sem grupos; só hábitos esconde Tarefas e Projetos; tarefa herdada sem `projetoNome` não quebra;
  - erros: alerta "Não foi possível carregar os itens vinculados.", nenhum grupo durante o carregamento, links com o href certo.
- [x] 3.2 Implementar `src/objetivo/pages/ObjetivoDetalhePage.tsx`:
  - busca do objetivo e dos itens em paralelo, com estados independentes e descarte de resposta antiga quando o `id` muda;
  - cabeçalho com status, prazo via `prazoObjetivo` (vencido e "vence hoje"), descrição e "Voltar a Objetivos" com `listaSearch`;
  - `aoSalvar` recarrega os dois;
  - CSS em `styles/objetivos.css`.

  Verificar em `ObjetivoDetalhePage.test.tsx`:
  - feliz: abre um ativo com prazo;
  - anomalias: prazo vencido mostra "Prazo vencido"; "Voltar" usa o `listaSearch` (e `/objetivos` sem ele); a falha nos itens mantém cabeçalho e ações; a troca de `id` descarta a resposta antiga;
  - erros: 404 mostra o detail, sem ações e sem itens; 500 no objetivo mostra "Não foi possível carregar o objetivo."; HTML do proxy nos itens mostra o alerta da seção.
- [x] 3.3 Colocar as ações de ciclo de vida no detalhe, usando `acoesObjetivo`:
  - `confirm` em Concluir e Arquivar e trava `acaoRef`;
  - no sucesso, `setObjetivo` e toast, e o usuário continua na página;
  - no erro, alerta com `ApiError.message` ou o fallback, e recarga do objetivo;
  - "Editar" leva a `editar` repassando o `state`.

  Verificar em `ObjetivoDetalhePage.test.tsx`:
  - concluir confirmado: status Concluído, ações novas e toast "Objetivo concluído";
  - arquivar cancelado: nenhuma chamada;
  - clique duplo: uma chamada só;
  - 409: mostra o detail, sem toast, e recarrega;
  - 500 ao reabrir: mostra "Não foi possível reabrir o objetivo.";
  - salvar no drawer `editar`: o cabeçalho mostra o novo título.

## 4. Seletor de objetivos

- [x] 4.1 Criar `src/objetivo/ObjetivoPicker.tsx`, conforme a decisão 6 do design:
  - opções de `listarObjetivos({ status: 'ATIVO', size: 100 })`;
  - chips de `opcoes ∪ selectedObjetivos`, com o status escrito quando o objetivo não está ativo;
  - busca por título;
  - estado "Nenhum objetivo ativo" com link para `/objetivos`, e "Nenhum objetivo com esse termo" na busca sem resultado;
  - alerta de erro que mantém os chips;
  - ids com `useId()`.

  Verificar em `ObjetivoPicker.test.tsx`:
  - feliz: escolher um ativo emite ids e resumos;
  - anomalias: concluído e arquivado não aparecem como opção; chip de objetivo concluído com o status escrito e removível; remover o último chip emite `[]`; lista vazia mostra "Nenhum objetivo ativo" e o link; busca sem resultado; `disabled` bloqueia escolher;
  - erros: a falha de rede mostra "Não foi possível carregar os objetivos." e mantém os chips; HTML do proxy idem; a chamada usa `status=ATIVO`.
- [x] 4.2 Ligar o picker na tarefa:
  - `TarefaForm` recebe `objetivoIds`, `objetivosSelecionados`, `onObjetivosChange` e `mostrarObjetivos`;
  - o `TarefaDrawer` guarda o estado e o snapshot `inicial` (que entra no `alterado`);
  - envia `objetivoIds` no criar e no PATCH, e omite no PATCH de subtarefa.

  Verificar em `TarefaDrawer.test.tsx`:
  - criar com um objetivo envia `objetivoIds`;
  - editar mantém o chip de objetivo arquivado e reenvia o id;
  - subtarefa não mostra o picker e o PATCH não leva `objetivoIds`;
  - mudar só os objetivos marca o drawer como alterado;
  - 409 "Objetivo não está ativo" mostra o detail com o drawer aberto.
- [x] 4.3 Ligar o picker no hábito: `HabitoCampos` recebe as props novas e o `HabitoDrawer` guarda o estado, o snapshot e o envio no criar e no editar. Verificar em `HabitoDrawer.test.tsx`:
  - criar com objetivo;
  - desvincular (remover o chip) envia `objetivoIds: []`;
  - o chip de objetivo concluído aparece ao carregar;
  - 409 mostra o detail.
- [x] 4.4 Ligar o picker no projeto: `ProjetoCampos` recebe props opcionais, e `ProjetoDrawer` (criar) e `ProjetoFormPage` (editar) guardam o estado e enviam. Verificar nos testes do `ProjetoDrawer` e da `ProjetoFormPage`:
  - criar com objetivo;
  - editar e remover o chip envia `[]`;
  - o chip de objetivo arquivado aparece ao carregar;
  - 409 mostra o detail.

## 5. Integração

- [x] 5.1 Rodar `npm run lint`, `npm run build` e `npm run test:coverage`. A suíte tem de ficar verde, o `tsc -b` tem de passar nos testes, e a cobertura tem de ficar acima do limite. Se o piso medido subir, elevar `test.coverage.thresholds` em `vite.config.ts`.
- [ ] 5.2 Conferir no navegador (`npm run dev` com o backend local), em 1280×720 e em 375px:
  - vincular uma tarefa, um hábito e um projeto pelos forms;
  - abrir o detalhe e ver os grupos e o selo "via projeto";
  - desvincular e voltar ao detalhe;
  - concluir pelo detalhe;
  - editar pelo card e pelo detalhe, e fechar voltando ao detalhe;
  - "Voltar a Objetivos" retorna à mesma visão;
  - o objetivo arquivado não aparece no picker;
  - o foco vai para o lugar certo ao abrir e fechar o drawer.

## Workflow follow-up

- Revisar e arquivar a change (`/openspec-archive-change`) depois da implementação.
- Isso desbloqueia a #94, a barra de progresso, que entra no detalhe e no card.
