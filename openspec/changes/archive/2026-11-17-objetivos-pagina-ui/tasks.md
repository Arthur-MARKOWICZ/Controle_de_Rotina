# Tasks

Comandos a partir de `frontend/frontend-web`. Nos testes, mocks sempre via `vi.mocked(...)`.

## 1. Cliente de API e regras puras

- [x] 1.1 Criar `src/objetivo/api/objetivoApi.ts` (listar com `status`/`page`/`size`, criar, buscar, atualizar, concluir, arquivar, reabrir) tipado por `components['schemas']` e `Pagina<ObjetivoResponse>`, com `apiFetch` + `lerErroApi`; verificar `objetivoApi.test.ts` com a matriz padrão (2xx, 400, 401 → refresh, 404, 409, 500/timeout, HTML do proxy / JSON inválido), a query da listagem e o PATCH com `prazo: null` — `npx vitest run src/objetivo/api`
- [x] 1.2 Criar `src/objetivo/prazoObjetivo.ts` (`situacaoPrazo`, `rotuloPrazo`, `rotuloConclusao`, `hojeIso` injetável); verificar `prazoObjetivo.test.ts` com ≥ 1 feliz (futuro "faltam N dias"), ≥ 3 anomalias (hoje → "Vence hoje", virada de mês/ano, `concluidoEm` 02:30Z formatado no dia anterior em BRT, sem prazo → `null`) e ≥ 3 erros de borda (ontem → vencido, prazo vazio, data malformada não lança)
- [x] 1.3 Criar `src/objetivo/validation/objetivoForm.ts` (`OBJETIVO_FORM_VAZIO`, `validarObjetivoForm(values, { hojeIso, prazoOriginal })`); verificar `objetivoForm.test.ts`: título válido; título só espaços, 81 caracteres, descrição 501, prazo ontem num objetivo novo → mensagem específica; prazo vencido igual ao original → aceito; prazo trocado para outro dia passado → erro

## 2. Navegação e rotas

- [x] 2.1 Adicionar Objetivos (`FlagIcon`) em `NAV_SIDEBAR_PRIMARIO` após Projetos e como primeiro item de `ITENS` do `MenuMais`, sem mexer em `NAV_BOTTOM`; verificar `AppShell.test.tsx` atualizado (T1 com Objetivos após Projetos, T2 com Objetivos no "Mais" e barra inferior inalterada)
- [x] 2.2 Adicionar `o: '/objetivos'` em `ATALHOS_GO`, "G O — Ir para Objetivos" na ajuda e os títulos `/objetivos`, `/objetivos/nova`, `/objetivos/:id` em `documentTitle.ts`; verificar `atalhos.test.ts` e `documentTitle.test.ts`
- [x] 2.3 Registrar em `App.tsx` a rota `/objetivos` (`ObjetivosPage`) com filhos `nova` e `:id` (`ObjetivoDrawer`) e incluir `/objetivos` em `PAGINAS_COM_DRAWER`; verificar no `AppShell.test.tsx` que abrir `/objetivos/nova` a partir de `/objetivos` não move o foco para o `h1`

## 3. Lista `/objetivos`

- [x] 3.1 Criar `src/styles/objetivos.css` (header, cards, `--vencido`, `--arquivado`, placeholder de progresso com altura fixa e borda tracejada, vazio, paginação) com tokens existentes e importá-lo em `src/index.css` ao lado de `projetos.css`; verificar `npm run build` sem erro
- [x] 3.2 Criar `src/objetivo/ObjetivoCard.tsx` (título como link ao drawer, linha de prazo/conclusão via `prazoObjetivo`, descrição truncada, placeholder de progresso, ações por status, botões desabilitados com `acaoEmAndamento`); verificar `ObjetivoCard.test.tsx`: ações de ativo/concluído/arquivado, vencido só em ativo com texto "Prazo vencido", concluído com prazo passado sem destaque, placeholder sem `role="progressbar"`, botões desabilitados durante a ação
- [x] 3.3 Criar `src/objetivo/pages/ObjetivosPage.tsx` (chips Ativos/Concluídos/Arquivados, `?status=&page=` com `replace`, contador, `Skeleton`, `InlineAlert`, vazios por visão, paginação, botão "Novo objetivo", `<Outlet context={{ aoSalvar }}>`); verificar `ObjetivosPage.test.tsx`: padrão Ativos, troca de visão zera a página na URL, status desconhecido → Ativos, carregando, vazio de Ativos com "Criar primeiro objetivo", vazio de Arquivados com "Ver ativos", falha → "Não foi possível carregar os objetivos." sem cards
- [x] 3.4 Implementar concluir/arquivar com `useConfirmDialog` e reabrir direto, com toast e recarga (`recarregarEm`) no sucesso e no erro; verificar em `ObjetivosPage.test.tsx`: concluir confirmado → toast "Objetivo concluído" e item fora da visão; arquivar cancelado → nenhuma chamada; clique duplo → uma chamada; 409 "Objetivo já concluído", 404 "Objetivo não encontrado" e 500 → mensagem no alerta, sem toast, lista recarregada

## 4. Drawer criar/editar

- [x] 4.1 Criar `src/objetivo/ObjetivoCampos.tsx` (título com `maxLength` 80, descrição 500, `input type="date"` com `min` = hoje BRT exceto quando o prazo original já venceu, botão para limpar prazo); verificar via testes do drawer
- [x] 4.2 Criar `src/objetivo/ObjetivoDrawer.tsx` no molde de `HabitoDrawer` (modo por `useParams`, `daLista`, `alterado`, descarte confirmado, 404 → alerta sem formulário, envia `prazo` original quando inalterado e `null` quando limpo, chama `aoSalvar` e mostra toast); verificar `ObjetivoDrawer.test.tsx`: criar só com título → toast "Objetivo criado"; editar título → "Objetivo atualizado"; remover prazo envia `prazo: null`; manter prazo vencido não bloqueia; título em branco e prazo de ontem → erro sem chamada; API 400 "Prazo inválido" mantém dados; 404 no carregamento → "Objetivo não encontrado" sem campos; Esc com alteração pede descarte e "continuar editando" mantém os dados; fechar sem alteração volta à mesma visão

## 5. Integração

- [x] 5.1 Rodar `npm run lint`, `npm run build` e `npm run test:coverage`; verificar suíte verde, `tsc -b` passando nos testes e cobertura acima do limite em `vite.config.ts` (subir `test.coverage.thresholds` para o novo piso medido, arredondado para baixo)
- [x] 5.2 Verificar no navegador (`npm run dev`, backend local) em 1280×720 e 375px: nav (sidebar e "Mais"), `G O`, as três visões, criar/editar/concluir/arquivar/reabrir com toasts, prazo vencido destacado e foco ao abrir/fechar o drawer

## Workflow follow-up

- Revisar e arquivar a change (`/openspec-archive-change`) após implementação.
- Desbloqueia a issue de vínculos (#91) e a de progresso, que substitui o placeholder do card.
