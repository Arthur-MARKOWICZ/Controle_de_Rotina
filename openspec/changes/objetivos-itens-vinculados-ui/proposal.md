# Proposal

## Why

A API já liga objetivos a tarefas, hábitos e projetos (#91, change `objetivos-vinculos`), mas a interface não usa isso. O usuário não escolhe objetivos ao criar ou editar um item e não vê o que contribui para cada objetivo. Esta change é a issue #92: torna o vínculo usável na tela. A #94 (barra de progresso) e a #95 (histórico) dependem de existir uma tela de detalhe do objetivo.

## What Changes

- **Seletor de objetivos** (`ObjetivoPicker`) nos formulários de tarefa (drawer, dentro de "Edições avançadas", onde ficam as tags), hábito (drawer) e projeto (drawer de criação e hub `/projetos/:id`):
  - escolhe vários objetivos;
  - as opções são só os **ativos**;
  - um objetivo já vinculado que foi concluído ou arquivado continua como chip selecionado, com o status escrito, mas não aparece nas opções;
  - não aparece em **subtarefa**, que não pode ter objetivo.
  - O envio usa `objetivoIds` no create e no PATCH, igual a `tagIds`.
- **Página de detalhe do objetivo** em `/objetivos/:id`, no modelo do hub de projeto. **BREAKING (rota):** até agora essa URL abria o drawer de edição.
  - Cabeçalho com título, status, prazo, descrição e as ações por status (Editar, Concluir, Arquivar, Reabrir).
  - As ações ficam na própria página, atualizam o objetivo e mostram toast.
  - Itens vinculados agrupados em **Tarefas / Hábitos / Projetos**, com o selo "via projeto X" nas tarefas herdadas. Cada item leva à sua tela.
  - Estados de carregando, vazio e erro de rede.
  - A página relê os itens sempre que abre, então vincular ou desvincular em outro formulário aparece na hora.
- **Edição** passa para `/objetivos/:id/editar`: é o mesmo `ObjetivoDrawer`, aberto sobre o detalhe. Fechar ou salvar volta ao detalhe.
- **Card na lista:** o título passa a ser link para o detalhe, e "Editar" leva a `/objetivos/:id/editar`.
- Cliente `listarItensObjetivo` em `objetivoApi.ts`. Título da aba "Objetivo" no detalhe e "Editar objetivo" no drawer de edição.

**Fora do escopo:**
- seletor no QuickAdd e no InlineAdd (criação rápida de tarefa);
- desvincular direto no detalhe;
- aviso de objetivo herdado no seletor da tarefa;
- barra de progresso (#94);
- histórico (#95);
- mudanças no backend, no contrato ou no MCP.

## Capabilities

### New Capabilities
<!-- Nenhuma: seletor e detalhe são comportamento de objetivo na UI e ficam em `ui-objetivo`. -->

### Modified Capabilities
- `ui-objetivo`:
  - Os títulos de aba passam a incluir o detalhe e "Editar objetivo".
  - O card ganha link para o detalhe.
  - A edição sai de `/objetivos/:id` para `/objetivos/:id/editar` e passa a voltar ao detalhe.
  - Entram requisitos novos: página de detalhe, itens vinculados, ações no detalhe e seletor de objetivos nos formulários.

## Impact

- **Frontend web:**
  - `src/objetivo/`: `ObjetivoPicker`, `pages/ObjetivoDetalhePage`, a seção de itens, `listarItensObjetivo` em `api/objetivoApi.ts`, ajustes em `ObjetivoDrawer` e `ObjetivoCard`, e os testes de cada um.
  - Rotas em `App.tsx`.
  - `layout/AppShell.tsx` (`PAGINAS_COM_DRAWER`) e `layout/documentTitle.ts`, com os testes.
  - Formulários: `tarefa/TarefaForm.tsx` e `TarefaDrawer.tsx`, `habito/HabitoCampos.tsx` e `HabitoDrawer.tsx`, `projeto/ProjetoCampos.tsx`, `ProjetoDrawer.tsx` e `pages/ProjetoFormPage.tsx`.
  - CSS em `styles/objetivos.css`.
- **Backend, contrato, proxy e MCP:** nada muda. O `/objetivos/{id}/itens` e o `objetivoIds` nos requests já existem, e o prefixo `/objetivos` já está no proxy. A rota SPA `/objetivos/:id/editar` cai no fallback do SPA, como as outras.
- **Cobertura:** os testes novos contam para o gate do frontend, e o limite só sobe.
