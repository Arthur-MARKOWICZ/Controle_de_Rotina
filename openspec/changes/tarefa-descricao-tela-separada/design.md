# Design

## Context

See proposal.md — Why. Hoje `TarefaDrawer` monta um único `Drawer` com `TarefaForm` (título + textarea descrição + metadados) e rodapé Cancelar/Salvar/Excluir. O `Drawer` (`feedback/Drawer.tsx`) trata Esc/X/backdrop via `onClose` único, foca o primeiro campo focável do corpo, e não tem slot de “voltar”. Validação e persistência de `descricao` (máx. 2000, trim no submit) já existem em `tarefaForm.ts` / `atualizarTarefa` / `criarTarefa`.

## Goals / Non-Goals

**Goals:**

- Separar meta e nota com view state local no drawer, sem nova rota.
- Reusar o estado `values.descricao` e a guarda `alterado` já existentes.
- Estender o `Drawer` o mínimo necessário para chrome da nota (voltar + título + rodapé).
- Manter acessibilidade: foco na entrada/saída da nota; Esc aninhado.

**Non-Goals:**

- Extrair um “stack navigator” genérico reutilizável além do necessário para esta tela.
- Rich text / markdown / checklist.
- Mudanças de API ou proxy.
- Redesign do drawer de hábito/projeto (podem continuar com textarea inline).

## Decisions

### 1. View state no `TarefaDrawer`, não rota filha

- Estado `view: 'meta' | 'nota'` (ou equivalente) em `TarefaDrawer`.
- URL permanece `/tarefas/nova` ou `/tarefas/:id`.
- Alternativa rejeitada: `/tarefas/:id/descricao` — deep link útil, mas complica histórico do browser e o contrato atual “fechar drawer = voltar à lista”.

### 2. Extensão leve do `Drawer` para header leading

- Adicionar props opcionais, por exemplo `headerStart?: ReactNode` (botão voltar) e/ou `closeAriaLabel` / `onClose` já redirecionado pelo pai.
- Na view nota, `TarefaDrawer` passa:
  - `title="Descrição"`
  - `headerStart` = botão voltar (`CaretLeft`, `aria-label="Voltar"`)
  - `footer` = só “Pronto”
  - `onClose` = `voltarAoMeta` (Esc, X, backdrop)
- Na view meta, comportamento atual (fechar com guarda).
- Alternativa rejeitada: chrome customizado só no children sem mexer no `Drawer` — duplica header e quebra o `aria-labelledby` do dialog.

### 3. Form meta sem textarea; linha de entrada

- `TarefaForm` troca o campo descrição por um botão/linha (role button) com preview truncado (~2 linhas / ellipsis) ou “Adicionar descrição”.
- Callback `onAbrirDescricao` para o drawer setar `view = 'nota'`.
- Preview usa o valor atual de `values.descricao` (inclui draft após voltar da nota).

### 4. Corpo da nota

- Textarea borderless/full no corpo do drawer (CSS em `tarefas.css`), `maxLength={2000}`, contador discreto `n/2000` opcional no rodapé da área de texto.
- Edição escreve em `setValues` / `descricao` imediatamente (draft); “Pronto” só muda `view` para `meta`.
- Foco: ao entrar na nota, focar o textarea (pode exigir `key`/`useEffect` quando `view` muda, porque o `Drawer` hoje só foca no `busy`).
- Ao voltar ao meta, focar a linha de descrição.

### 5. Esc / X / backdrop na nota

- `pedirFechar` no drawer vira: se `view === 'nota'` → `voltarAoMeta`; senão → fluxo atual de fechar/confirmação.
- Backdrop na nota também volta ao meta (consistente com X/Esc), evitando fechar o drawer “por acidente” no meio da escrita.

### 6. Dirty state e submit

- Continuar comparando `values` vs `inicial`; editar na nota marca `alterado`.
- Submit inalterado: `descricao: values.descricao.trim()` no corpo create/update.
- Tarefa deletada: linha de descrição disabled como os demais campos; não abrir a nota.

## Risks / Trade-offs

- [Foco do Drawer só reage a `busy`] → Mitigar: efeito explícito em `TarefaDrawer` ao trocar `view`, ou estender `Drawer` com `focusKey`.
- [Backdrop na nota = voltar, não fechar] → Usuário pode achar que clicou fora para sair; mitiga com X/voltar claros e comportamento igual ao Esc.
- [Habito/Projeto ainda com textarea] → Inconsistência aceitável neste slice; não expandir escopo.
- [Preview truncado vs conteúdo longo] → Truncar no CSS (`line-clamp`); texto completo só na nota.

## Migration Plan

- Só frontend; deploy sem migration DB.
- Rollback: reverter o PR / restaurar textarea inline.

## Open Questions

- Nenhum que mude spec/abordagem/tarefas; detalhes de clamp (1 vs 2 linhas) e copy exata do contador ficam na implementação.
