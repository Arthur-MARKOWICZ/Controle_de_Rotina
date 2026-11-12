# Proposal

## Why

No drawer de criar/editar tarefa, a descrição é um `textarea` curto no meio dos metadados (prioridade, prazo, projeto, status). Isso atrapalha escrever notas longas e mistura “configurar a tarefa” com “escrever o conteúdo”. Queremos o padrão TickTick light: metadados num painel; descrição numa superfície content-first, separada.

## What Changes

- Remover o `textarea` inline de descrição do formulário meta do drawer.
- No form meta, uma linha clicável “Descrição” com **preview truncado** (ou “Adicionar descrição” se vazia) abre uma **view interna** no mesmo `Drawer`.
- Na view da nota: chrome dedicado (voltar + título “Descrição”), corpo com textarea full, rodapé só “Pronto”; Esc e o X do header **voltam ao meta** (não fecham o drawer).
- Edição da nota é **só draft local** (`values.descricao`); persistência continua no **Salvar** / **Criar** do rodapé do meta.
- Mesmo fluxo em criar (`/tarefas/nova`) e editar (`/tarefas/:id`).
- Guarda “Descartar alterações?” continua considerando mudanças na descrição.

Fora de escopo: markdown/rich text; auto-save da descrição; rota dedicada `/tarefas/:id/descricao`; split-pane desktop; aumentar o limite de 2000 caracteres na API; mudar o campo `descricao` no backend.

## Capabilities

### New Capabilities

- `ui-tarefa-descricao`: edição da descrição da tarefa em view aninhada no drawer (entrada com preview, chrome da nota, navegação Esc/X/voltar, draft local até salvar o formulário meta).

### Modified Capabilities

- (nenhuma — inventário OpenSpec de specs principais ainda vazio)

## Impact

- Frontend: `TarefaForm`, `TarefaDrawer`, possivelmente extensão leve de `feedback/Drawer` (slot de voltar / header leading); CSS em `tarefas.css` / drawer; testes `TarefaDrawer.test.tsx` / form.
- Backend / proxy: sem mudança de API ou prefixo novo.
- Acessibilidade: foco ao entrar na nota (textarea) e ao voltar (linha Descrição); stack de Esc aninhado.
