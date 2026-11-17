# Design

## Context

Motivação em `proposal.md`; comportamento em `specs/ui-objetivo/spec.md`.

A API `/objetivos` está pronta (change `objetivos-cadastro-api`): proxy em `vite.config.ts`/`nginx.conf`, `ObjetivoResponse` e `PagedModelObjetivoResponse` já em `src/api/schema.d.ts`. `prazo` é data sem horário (`yyyy-MM-dd`); `concluidoEm`/`arquivadoEm` são instantes. A ordem padrão de cada visão já vem do backend.

O front já tem todas as peças:

- **Lista:** `ProjetosPage` — chips de visão (`visao-chips`), estado na URL com `replace`, `Skeleton`, `InlineAlert`, estado vazio, paginação, `acaoId` por card, `useConfirmDialog`, `useToast`, recarga por contador `recarregarEm`.
- **Drawer criar + editar:** `HabitoDrawer` — `modo` derivado de `useParams`, `daLista` em `location.state` para fechar com `navigate(-1)`, `alterado` comparando com o valor inicial, confirmação de descarte com `confirmandoRef`, comunicação com a lista por `useOutletContext`.
- **Rotas com drawer:** `App.tsx` (`/habitos` com filhos `nova` e `:id`) e `PAGINAS_COM_DRAWER` no `AppShell` (foco não pula para o `h1` ao abrir o drawer).
- **Datas:** `dataHojeIso` (`planner/data`) dá hoje em BRT; `parseDataIsoLocal` e `adicionarDiasIso` (`capacidade/semana`) tratam datas `yyyy-MM-dd`.
- **Cliente de API:** `tarefaApi`/`tagApi` tipam pelo `schema.d.ts`; `apiFetch` + `lerErroApi` padronizam erro (`ApiError` com o `detail` da ProblemDetail, ou o `title` na falta dele).

## Goals / Non-Goals

**Goals:**
- Montar a feature só com padrões e componentes existentes; nenhum componente compartilhado novo.
- Deixar o espaço de progresso estável no card para a issue de progresso trocar só o conteúdo.
- Toda regra de data (vencido, "vence hoje", mínimo do campo) num módulo puro testável com data de referência injetada.

**Non-Goals:**
- Atualização otimista da lista; filtro por tags; reordenação por arraste; página "hub" do objetivo.

## Decisions

### Estrutura de arquivos

```
src/objetivo/
  api/objetivoApi.ts (+ .test.ts)        listar, criar, buscar, atualizar, concluir, arquivar, reabrir
  pages/ObjetivosPage.tsx (+ .test.tsx)  lista, visões, ações, <Outlet context>
  ObjetivoCard.tsx (+ .test.tsx)         card + ações por status
  ObjetivoDrawer.tsx (+ .test.tsx)       criar/editar
  ObjetivoCampos.tsx                     título, descrição, prazo
  validation/objetivoForm.ts (+ .test.ts)
  prazoObjetivo.ts (+ .test.ts)          situação do prazo e rótulos
src/styles/objetivos.css
```

`ObjetivoCard` sai da página (diferente de `ProjetosLista`) porque concentra a matriz status × ações e o rótulo de prazo, e fica testável isoladamente; a página fica com URL, carga e handlers.

### Cliente tipado pelo contrato
`objetivoApi.ts` segue `tarefaApi`: `type Schemas = components['schemas']`, `ObjetivoResponse = Schemas['ObjetivoResponse']`, `StatusObjetivo = ObjetivoResponse['status']`, request types de `CriarObjetivoRequest`/`AtualizarObjetivoRequest`, página como `Schemas['PagedModelObjetivoResponse']` (domínios tipados pelo contrato usam o `PagedModel*` gerado; `Pagina<T>` é para os tipados à mão). Cada função usa `apiFetch` e `lerErroApi(response, fallback)`. Alternativa descartada: tipos à mão como em `projetoApi` — o AGENTS.md pede tipos gerados e o CI já valida o `schema.d.ts`.

`atualizarObjetivo` envia só os campos do formulário; `prazo: null` quando o campo foi limpo (o PATCH trata `null` explícito como remoção). Para não disparar a regra de "prazo no passado" ao reenviar um prazo vencido inalterado, o drawer envia o `prazo` original — o backend já aceita prazo igual ao atual.

### Lista: recarregar em vez de mutar
Após concluir/arquivar/reabrir (sucesso ou erro) e após salvar no drawer, a página incrementa `recarregarEm` e busca de novo, como `ProjetosPage`. Como toda ação de ciclo de vida tira o item da visão, o resultado é sempre correto e cobre o 409 vindo de outra aba. O drawer chama `aoSalvar()` via `useOutletContext<ObjetivosOutletContext>()`.

Alternativa descartada: atualizar a lista localmente (como `HabitosPage` com `aoAtualizar`/`aoCriar`) — exigiria reaplicar a ordenação do backend (prazo, sem prazo no fim, datas de encerramento) no cliente.

### URL
`?status=CONCLUIDO|ARQUIVADO` (ausente = `ATIVO`, valor desconhecido = `ATIVO`) e `?page=N` (ausente = 0), escritos com `replace: true` como em projetos. Trocar visão zera a página. O link "Novo objetivo" e os links de editar passam `{ pathname, search: location.search }` e `state: { daLista: true }`, então fechar o drawer volta à mesma visão/página. Exceção: depois de criar, o drawer fecha com `navigate('/objetivos', { replace: true })`, porque o objetivo novo é ativo e não apareceria em Concluídos/Arquivados.

Enquanto uma ação de ciclo de vida está em andamento, a página passa `bloqueado` para todos os cards (botões desabilitados); só o card da ação troca o rótulo ("Concluindo…"). Uma trava em `ref` impede a segunda chamada antes do re-render.

`truncarDescricao` fica em `src/lista/truncarDescricao.ts`, compartilhado com `ProjetosPage`.

### Rotas e foco
`App.tsx`: `<Route path="/objetivos" element={<ObjetivosPage />}>` com filhos `nova` e `:id` → `<ObjetivoDrawer />`. `PAGINAS_COM_DRAWER` ganha `{ base: '/objetivos', drawer: /^\/objetivos\/[^/]+$/ }`. `documentTitle.ts` ganha `/objetivos` → "Objetivos", `/objetivos/nova` → "Novo objetivo" e `/objetivos/:id` → "Objetivo" (a regra de `nova` antes da de `:id`).

### Navegação
`AppShell`: item `OBJETIVOS = { to: '/objetivos', label: 'Objetivos', Icon: FlagIcon }`, inserido em `NAV_SIDEBAR_PRIMARIO` após `PROJETOS`; `NAV_BOTTOM` não muda. `MenuMais`: `ITENS` ganha Objetivos como primeiro item (é o destino mais "primário" entre os que estão lá). `atalhos.ts`: `o: '/objetivos'` em `ATALHOS_GO` e `{ teclas: 'G O', descricao: 'Ir para Objetivos' }` na ajuda. Ícone Phosphor `Flag` (sugestão da skill ui-ux-pro-max; família e peso iguais aos demais itens — `fill` quando ativo).

### Prazo: módulo puro
`prazoObjetivo.ts` recebe `hojeIso` (default `dataHojeIso()`), sem ler relógio internamente:

```
situacaoPrazo(prazo, hojeIso) -> 'sem-prazo' | 'vencido' | 'hoje' | 'futuro'
rotuloPrazo(prazo, hojeIso)   -> "Até 20 nov · faltam 3 dias" | "Vence hoje" | "Prazo vencido · 16 nov" | null
rotuloConclusao(concluidoEm)  -> "Concluído em 12 nov"
```

Comparação por string `yyyy-MM-dd` (ordem lexicográfica = cronológica), sem `Date` para o prazo, o que elimina erro de fuso. Dias restantes via `adicionarDiasIso`/diferença em UTC. `concluidoEm` (instante) é formatado em `America/Sao_Paulo` com `Intl.DateTimeFormat`. "Vencido" só se aplica a `ATIVO` — a decisão fica no card, não no módulo.

### Validação do formulário
`validarObjetivoForm(values, { hojeIso, prazoOriginal })` no padrão de `validarHabitoForm` (erros por campo + `primeiroErroObjetivo` para o `InlineAlert`; o campo com erro recebe `aria-invalid`): título obrigatório após trim e ≤ 80; descrição ≤ 500; prazo `< hojeIso` é erro só se `prazo !== prazoOriginal`. O `<input type="date">` recebe `min={hojeIso}` apenas quando não há prazo original vencido (senão o navegador marcaria inválido um valor que o backend aceita). `maxLength` nos campos de texto como em `ProjetoCampos`.

### Card e espaço de progresso

```
+------------------------------------------------------------+
| [titulo-link -> drawer]                      [acoes...]    |
| Até 20 nov · faltam 3 dias   (ou aviso "Prazo vencido")    |
| descrição truncada em 120                                  |
| +- - - - - - - - - - - - - - - - - - - - - - - - - - - - + |
| : Progresso aparece quando houver itens vinculados       : |
| +- - - - - - - - - - - - - - - - - - - - - - - - - - - - + |
+------------------------------------------------------------+
```

Classes `objetivo-card`, `objetivo-card--vencido`, `objetivo-card--arquivado`, `objetivo-card__progresso` em `objetivos.css`, reaproveitando tokens (`--line`, `--panel`, `--muted`, `--space-*`, `--radius-md`) e o visual de aviso de `decidir-card--vencido`. O placeholder é um `div` com borda tracejada e texto `--muted`, sem `role="progressbar"`; altura fixa para a issue de progresso trocar o conteúdo sem mexer no layout. O vencido usa texto + cor (nunca só cor).

### Confirmações e textos
`useConfirmDialog`:
- Concluir: título "Concluir objetivo?", mensagem "“{titulo}” sai dos ativos. Você pode reabrir depois.", botão "Concluir".
- Arquivar: "Arquivar objetivo?", "“{titulo}” fica arquivado. Você pode reabrir depois.", botão "Arquivar".

Toasts: "Objetivo criado", "Objetivo atualizado", "Objetivo concluído", "Objetivo arquivado", "Objetivo reaberto". Erro de ação: `error instanceof ApiError ? error.message : 'Não foi possível {concluir|arquivar|reabrir} o objetivo.'`.

### Drawer em modo edição com 404
Se `buscarObjetivo` falha, o drawer mostra `InlineAlert` com a mensagem e oculta o formulário e o botão Salvar (só "Fechar"). Mesmo comportamento para 404 e 500; a mensagem vem do `ApiError`.

### Testes
Matriz 1 + 3 + 3 por unidade, `vi.mocked(...)` nos mocks:

- `objetivoApi.test.ts` — matriz padrão de cliente: 2xx, 400, 401 → refresh, 404, 409, 500/timeout, HTML do proxy / JSON inválido; mais a query de listagem (status, page, size) e o corpo do PATCH com `prazo: null`.
- `prazoObjetivo.test.ts` — `hojeIso` fixo: futuro, hoje, ontem, sem prazo, virada de mês/ano, `concluidoEm` às 02:30Z ainda no dia anterior em BRT.
- `objetivoForm.test.ts` — título vazio/81, descrição 501, prazo ontem (novo) × prazo vencido original mantido.
- `ObjetivosPage.test.tsx` — visões e URL, status inválido na URL, estados carregando/vazio/erro, concluir confirmado, arquivar cancelado (sem chamada), clique duplo, 409/404/500 com alerta e recarga.
- `ObjetivoCard.test.tsx` — ações por status, vencido só em ativo, placeholder sem `progressbar`.
- `ObjetivoDrawer.test.tsx` — criar, editar, remover prazo, manter prazo vencido, 404 no carregamento, erro 400 da API mantendo os dados, descarte com e sem alteração.
- `AppShell.test.tsx`, `atalhos.test.ts`, `documentTitle.test.ts` — atualizar ordem da sidebar (T1), "Mais" (T2), `G O` e títulos.

## Risks / Trade-offs

- [Recarregar a lista a cada ação faz uma requisição extra] → Lista paginada de 20; custo baixo e elimina divergência de ordenação com o backend.
- [Relógio do dispositivo errado muda "vencido" na UI] → Só afeta o destaque; o backend continua sendo a regra para salvar.
- [Testes T1/T2 do `AppShell` fixam a ordem da nav e vão quebrar] → Atualizá-los na mesma tarefa da navegação.
- [Item novo na sidebar primária aumenta a altura em telas baixas] → São 4 itens primários; sem rolagem nas larguras testadas. Verificar visualmente em 1280×720.
- [`min` do campo de data bloquearia salvar um prazo vencido inalterado] → `min` omitido quando o prazo original já está vencido; validação própria cobre o resto.

## Migration Plan

Só frontend; sem migração de dados. Deploy pelo pipeline normal (rebuild da imagem do frontend). Rollback: reverter o build — a API continua funcionando sem a tela.
