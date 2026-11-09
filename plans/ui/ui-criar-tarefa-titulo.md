# Feature — Criar tarefa só com título (UI TickTick-like) (3º slice do épico `ui-tarefas-primeiro-epico.md`)

Escopo: na lista `/tarefas`, **campo inline** no topo para criar com Enter (só título); simplificar o quick-add global para o mesmo mínimo; ponte “Mais detalhes” → drawer.  
Fora deste slice: nav/landing (já em `ui-ia-nav-tarefas-primeiro.md`); CTAs do planner; smart lists novas; concluir pela lista; backend (já em `tarefa-criar-so-titulo.md`).  
Dependências: `tarefa-criar-so-titulo.md` (B1), `ui-v2-tarefas-drawer.md`, `ui-ia-nav-tarefas-primeiro.md` (lista como casa).

---

## Conceito

Hoje o quick-add exige título + prioridade + duração (+ prazo opcional). O fluxo TickTick deste épico é: **um campo, Enter, item na lista**; detalhes no drawer depois.

Defaults vêm da API (`MEDIA`, 30 min, sem prazo). O front **não** inventa defaults locais divergentes: envia só `titulo` (e `projetoId` se o filtro atual da lista for um projeto).

---

## Front-end

1. **`InlineAddTarefa`** (nome livre desde que fique em `tarefa/`) — input de uma linha no topo da lista (abaixo do `PageHeader` / filtros, acima dos grupos):  
   - placeholder do tipo “Adicionar tarefa…”;  
   - Enter ou botão discreto “Adicionar” cria;  
   - Escape limpa/foca fora;  
   - durante envio: disabled + estado ocupado;  
   - sucesso: limpa o campo, insere/atualiza a lista (leitura silenciosa ou prepend otimista do `TarefaResponse` + reagrupar); toast curto opcional (ou só o item aparecendo — **decisão:** toast “Tarefa criada” com ação “Abrir” → drawer, alinhado ao quick-add atual);  
   - erro API → toast/inline sob o campo.  
2. **Filtro por projeto** — se a URL tem `projetoId`, o create inline envia esse `projetoId`.  
3. **Quick-add do shell** (`QuickAddTarefaDialog`) — modo mínimo: campo título + submit; link “Mais detalhes” abre `/tarefas/nova` (ou drawer) com o título; remover obrigatoriedade de prioridade/duração/prazo no fluxo rápido (podem ficar recolhidos como “Opções” **ou** sumir do dialog — **decisão:** sumir do dialog mínimo; detalhe só no drawer).  
4. **Atalho `N`** — continua abrindo o quick-add mínimo **ou** foca o `InlineAddTarefa` quando já estiver em `/tarefas` (**decisão:** em `/tarefas`, `N` foca o inline; fora de `/tarefas`, abre o dialog mínimo).  
5. **Drawer create** — inalterado para quem quer preencher tudo; pode pré-preencher título vindo do state.  
6. **Empty state** — CTA principal pode focar o inline em vez de só abrir dialog.  
7. Acessível: `label`/`aria-label` no input; não depender só do placeholder.

---

## Decisões fechadas (revisão)

1. Create mínimo = só `titulo` (+ `projetoId` se filtro ativo); defaults só no servidor.
2. Inline na lista + dialog mínimo no shell; drawer para o resto.
3. Em `/tarefas`, `N` foca o inline; senão abre dialog mínimo.
4. Toast de sucesso com “Abrir” mantido.
5. Sem endpoint novo além do contrato do plano de negócio.

---

## Testes automatizados

Harness: Vitest + Testing Library (`TarefasPage`, `QuickAddTarefaDialog`, shell atalho se couber).

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Enter no inline com título chama `criarTarefa` só com `titulo` (sem prioridade/duração no body) e limpa o campo | F1 | Fluxo TickTick |
| T2 | Com `?projetoId=` na URL, o body inclui esse `projetoId` | F1 | Contexto de projeto |
| T3 | Título vazio: não chama API; feedback acessível | F1 | Validação |
| T4 | Dialog mínimo do shell: submit só com título; “Mais detalhes” leva ao drawer/nova com título | F2 | Quick-add alinhado |
| T5 | Em `/tarefas`, atalho `N` foca o inline (não abre dialog) | F3 | Atalho |
| T6 | Fora de `/tarefas`, `N` abre dialog mínimo | F3 | Regressão shell |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Inline add na lista** | Componente + wire em `TarefasPage`; **T1–T3** |
| **F2** | **Quick-add mínimo + Mais detalhes** | Simplificar dialog; **T4** |
| **F3** | **Atalho `N` contextual + empty CTA** | Foco inline vs dialog; **T5–T6** |

### Ordem

```text
F1 → F2 → F3
```
