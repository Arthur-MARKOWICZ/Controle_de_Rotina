# Feature — UI v2: Tarefas com drawer e filtros instantâneos (4º slice do épico `ui-v2-epico.md`)

Escopo: `/tarefas` — criar/editar em **drawer lateral** sobre a lista (URL `/tarefas/nova` e `/tarefas/:id` preservadas), filtros aplicados na hora com chips removíveis, excluir/restaurar otimistas, cards de tarefa redesenhados, primitive `Drawer` reutilizável.  
Fora deste slice: busca por texto (API de `/tarefas` não tem parâmetro de título — não inventar endpoint); concluir tarefa direto da lista (status final vem da execução/regras de domínio); drag-and-drop; projetos (drawer de projeto vai em `ui-v2-telas-secundarias.md`).  
Dependências: `ui-v2-fundacao-visual.md`, `ui-v2-shell-nav.md` (`PageHeader`, Nova tarefa global); `ui-quick-add.md`, `ui-lista-tarefas.md` (feitos — URL de filtros, agrupamento por prazo, desfazer exclusão).

---

## Conceito

Hoje editar uma tarefa **sai da lista** (`TarefaFormPage` em rota própria, `navigate('/tarefas')` ao salvar) e a lista recarrega do zero; filtros avançados pedem “Filtros” → preencher → “Aplicar filtros”; excluir/restaurar esperam o servidor e recarregam a página. O usuário perde contexto a cada ação.

No v2 a lista é o lugar fixo: o drawer abre por cima, salva, fecha e a lista atualiza só o item afetado.

### Rotas

```text
/tarefas            → TarefasPage (lista)
/tarefas/nova       → TarefasPage + <TarefaDrawer modo="criar" />  (rota filha, Outlet)
/tarefas/:id        → TarefasPage + <TarefaDrawer modo="editar" />
```

Deep link para `/tarefas/:id` abre a lista com o drawer já aberto. Fechar o drawer volta para `/tarefas` **preservando a query string** de filtros. Voltar do navegador fecha o drawer.

---

## Front-end

1. **Primitive `feedback/Drawer.tsx`** — sobre `<dialog>` nativo (`showModal`): painel à direita com 28rem no desktop; no mobile (< 640px) vira bottom sheet de altura cheia; cabeçalho com título + botão fechar (`X`, `aria-label="Fechar"`); rodapé fixo para ações; Esc/backdrop fecham; foco inicial no primeiro campo; foco devolvido ao gatilho; animação de entrada `--dur-slow` (slide 16px + fade), saída mais curta; reduced-motion sem slide.
2. **Guarda de alterações** — fechar com formulário alterado pede confirmação (`useConfirmDialog`: “Descartar alterações?”).
3. **`TarefaForm` extraído** — campos e validação de `TarefaFormPage.tsx` (incluindo `tarefaForm.ts`, `prazoAtalhos`) viram componente usado pelo drawer; `TarefaFormPage` deixa de ser rota própria.
4. **Salvar** — editar: `atualizarTarefa` devolve `TarefaResponse` → item substituído na lista local (reagrupado por `agruparPorPrazo`), drawer fecha, toast “Tarefa salva”; em seguida leitura silenciosa da página atual (sem skeleton) para reconciliar filtro/ordem. Criar: fecha, toast com ação “Abrir” (como o quick-add), leitura silenciosa da lista.
5. **Quick-add ↔ drawer** — o `QuickAddTarefaDialog` ganha link “Mais detalhes” que abre `/tarefas/nova` com o título já digitado (via `location.state`), para quem precisa de descrição/projeto/duração fina.
6. **Excluir otimista** — item sai na hora, toast “Tarefa excluída · Desfazer” (já existe); erro restaura o item na mesma posição. **Restaurar** otimista igual (item volta sem badge “Excluída”); erro reverte.
7. **Filtros instantâneos** — barra de filtros sempre visível no topo da lista:  
   - visões (`Todas`, …) como segmented control;  
   - botões-popover **Prioridade**, **Prazo**, **Duração**, **Projeto**, **Ordenar** (`FunnelSimple`/`SortAscending`), cada um aplica ao escolher;  
   - campos de data/número aplicam com debounce de 400ms e cancelam a requisição anterior (`AbortController`) para não haver resposta fora de ordem;  
   - filtros ativos aparecem como chips removíveis (“Prioridade: Alta ✕”) + “Limpar filtros”;  
   - estado continua 100% na URL (`listaUrl.ts`), mesmo formato de hoje.  
   Some o botão “Aplicar filtros” e o painel “Filtros / Ocultar filtros”.
8. **Troca de filtro/página** — lista atual fica visível esmaecida (`aria-busy`) até a nova página chegar; skeleton só na primeira carga.
9. **Card de tarefa** — linha inteira clicável abre o drawer (link real para `/tarefas/:id`, acessível por teclado); à esquerda ponto de prioridade + título; abaixo, meta em uma linha: prazo relativo (com absoluto no `title`), duração, chip de projeto, badge de status só quando não é o default da visão; ações à direita em menu `DotsThree` (Editar, Excluir/Restaurar) + ação Excluir visível no hover/foco no desktop. Grupos por prazo com cabeçalho fixo (sticky) e contador.
10. **Paginação** — botões secundários com ícones (`CaretLeft`/`CaretRight`), “Página X de Y” e total de tarefas; foco volta ao topo da lista ao trocar de página.
11. **Empty states** — primeira vez: ícone + “Nenhuma tarefa ainda” + CTA “Nova tarefa”; com filtros: “Nada com estes filtros” + “Limpar filtros”.

---

## Decisões fechadas (revisão)

1. Drawer como rota filha de `/tarefas`; URLs atuais preservadas; fechar mantém a query string.
2. `TarefaFormPage` some como página; o formulário vive só no drawer.
3. Filtros aplicam na hora (debounce 400ms em data/número) com cancelamento da requisição anterior.
4. Salvar/criar atualiza o item localmente e reconcilia com leitura silenciosa; sem skeleton.
5. Excluir/restaurar otimistas; desfazer exclusão mantido.
6. Sem busca por texto e sem concluir tarefa pela lista neste slice.
7. Fechar drawer com alterações pede confirmação.

---

## Testes automatizados

Harness: Vitest + Testing Library (`TarefasPage.test.tsx`, `TarefaFormPage.test.tsx`, `QuickAddTarefaDialog.test.tsx`).

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `Drawer`: abre com foco no primeiro campo, Esc fecha e devolve foco ao gatilho | F1 | Primitive reutilizada no épico |
| T2 | Entrar direto em `/tarefas/:id` renderiza a lista **e** o drawer de edição com os dados da tarefa | F2 | Deep link não pode quebrar |
| T3 | Fechar o drawer volta a `/tarefas?<mesmos filtros>` | F2 | Contexto preservado |
| T4 | Fechar com alterações pede confirmação; cancelar mantém o drawer aberto | F2 | Perda de dados |
| T5 | Salvar edição: item da lista mostra o novo título antes da leitura silenciosa e sem skeleton; validações de `tarefaForm` continuam (regressão de `TarefaFormPage.test.tsx` migrada) | F3 | Fluxo central |
| T6 | Quick-add “Mais detalhes” abre `/tarefas/nova` com o título preenchido | F3 | Ponte entre criação rápida e completa |
| T7 | Excluir: item some imediatamente; erro da API restaura o item e mostra toast de erro | F4 | Otimismo + rollback |
| T8 | Escolher prioridade no popover atualiza a URL e chama `listarTarefas` sem botão “Aplicar” | F5 | Filtro instantâneo |
| T9 | Duas mudanças rápidas de filtro: só a resposta da última é aplicada | F5 | Corrida |
| T10 | Chip de filtro ativo removível atualiza URL e lista; “Limpar filtros” volta ao default | F5 | Descoberta/remoção de filtro |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Primitive `Drawer`** | Componente + CSS (desktop/mobile, motion, reduced-motion); **T1** |
| **F2** | **Rotas filhas + `TarefaDrawer`** | Extrair `TarefaForm`; rotas `/tarefas/nova` e `/tarefas/:id` como filhas; fechar preservando filtros; guarda de alterações; remover `TarefaFormPage` como página; **T2–T4** |
| **F3** | **Salvar/criar sem sair da lista** | Patch local + leitura silenciosa; toast; “Mais detalhes” no quick-add; **T5, T6** |
| **F4** | **Excluir/restaurar otimistas** | Rollback em erro; desfazer mantido; **T7** |
| **F5** | **Barra de filtros instantâneos** | Segmented de visão, popovers, debounce + `AbortController`, chips ativos, limpar; remover painel/“Aplicar”; **T8–T10** |
| **F6** | **Card, grupos, paginação e empty** | Visual do card + menu de ações; grupos sticky; paginação com ícones; empty states; lista esmaecida em recarga |

### Ordem

```text
F1 → F2 → F3 → F4
F5 (independente após shell) → F6
```
