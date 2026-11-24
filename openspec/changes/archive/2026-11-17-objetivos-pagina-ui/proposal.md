# Proposal

## Why

A API de objetivos (#89, change `objetivos-cadastro-api`) já existe, mas o usuário não tem como ver nem gerenciar seus objetivos — e "manter o objetivo visível" é o ponto central da #65. Esta change entrega a página `/objetivos` (issue #90), destino próprio na navegação, onde o usuário cria, edita, conclui, arquiva e reabre objetivos.

## What Changes

- Novo item **Objetivos** na navegação: sidebar no grupo primário logo após Projetos; no mobile, dentro do menu "Mais" (a bottom nav continua com 5 itens). Título da aba e atalho de teclado `G O`.
- Nova página `/objetivos` com três visões em chips — **Ativos**, **Concluídos**, **Arquivados** — refletidas na URL (`?status=&page=`), paginação e os mesmos estados de carregando, vazio e erro das demais listas.
- Card de objetivo com título, prazo em formato local (com destaque de **prazo vencido** para ativos), data de conclusão para concluídos, descrição curta e um **espaço reservado para a barra de progresso** (sem percentual, preenchido pela issue de progresso).
- Ações por visão: Ativos → Editar, Concluir, Arquivar; Concluídos → Editar, Arquivar, Reabrir; Arquivados → Editar, Reabrir. Concluir e Arquivar pedem confirmação; todas mostram toast de sucesso; erro de API aparece como alerta e a lista é recarregada.
- **Drawer** para criar (`/objetivos/nova`) e editar (`/objetivos/:id`), no padrão de Hábitos: campos título, descrição e prazo; prazo não aceita data anterior a hoje (BRT) ao criar ou ao trocar o prazo; fechar com alterações pede confirmação de descarte.
- Cliente `objetivoApi.ts` tipado a partir do `schema.d.ts` gerado.

Fora do escopo: vínculo com tarefas/hábitos/projetos (#91), cálculo e exibição real do progresso, tela de histórico dedicada, XP ao concluir objetivo, filtro por tags e reordenação manual, ferramentas novas no MCP, alterações no backend.

## Capabilities

### New Capabilities
- `ui-objetivo`: página e drawer de objetivos na interface web — navegação, visões por status, card, ciclo de vida (concluir, arquivar, reabrir) com confirmação e feedback, criação e edição.

### Modified Capabilities
<!-- Nenhuma: a nav não tem spec própria; o item Objetivos fica descrito em `ui-objetivo`. -->

## Impact

- **Frontend web:** novo módulo `src/objetivo/` (api, página, drawer, campos, validação, formatação de prazo e testes); novo `src/styles/objetivos.css`; rotas em `App.tsx`; `layout/AppShell.tsx` (item de nav e `PAGINAS_COM_DRAWER`), `layout/MenuMais.tsx`, `layout/documentTitle.ts`, `layout/atalhos.ts` e seus testes; `lista/truncarDescricao.ts` (extraído de `projeto/pages/ProjetosPage.tsx`, que passa a usá-lo).
- **Backend, contrato, proxy, MCP:** sem mudança — já entregues pela `objetivos-cadastro-api` (`/objetivos` no `vite.config.ts`, `nginx.conf`, `API_PROXY.md` e `schema.d.ts`).
- **Cobertura:** novos testes contam para o gate do frontend (`vite.config.ts`); limite só sobe.
