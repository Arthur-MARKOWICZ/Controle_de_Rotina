# Tasks

## 1. Primitive Drawer

- [x] 1.1 Estender `feedback/Drawer` com slot opcional de header leading (`headerStart`) sem quebrar usos atuais (tarefa/hábito/projeto); verificar teste existente do Drawer e novo caso com `headerStart` renderizado
- [x] 1.2 Garantir que Esc/X/backdrop continuam chamando só `onClose` (o pai decide voltar vs fechar); verificar com teste de interação do Drawer

## 2. Form meta e view da nota

- [x] 2.1 Em `TarefaForm`, remover o textarea inline de descrição e adicionar linha acionável com “Adicionar descrição” / preview truncado + callback `onAbrirDescricao`; verificar teste de componente (rótulo vazio vs preview)
- [x] 2.2 Em `TarefaDrawer`, introduzir `view: 'meta' | 'nota'`, renderizar corpo/rodapé/título/voltar conforme a view, draft em `values.descricao`, e `pedirFechar` que na nota volta ao meta; verificar testes: abrir nota sem mudar URL, Pronto volta ao meta sem `atualizarTarefa`/`criarTarefa`, Esc/X na nota voltam ao meta
- [x] 2.3 Estilos content-first da nota + linha de preview (`tarefas.css` / drawer); verificar visualmente em 375px e desktop e que `prefers-reduced-motion` não quebra o fluxo
- [x] 2.4 Foco: ao abrir a nota foca o textarea; ao voltar foca a linha de descrição; verificar com teste de foco (Testing Library)

## 3. Persistência e guarda

- [x] 3.1 Confirmar que Salvar/Criar no meta envia a descrição editada na nota e que fechar o meta com só a descrição alterada mostra “Descartar alterações?”; verificar cenários em `TarefaDrawer.test.tsx`
- [x] 3.2 Com tarefa deletada, linha de descrição disabled e nota não abre; verificar teste de regressão

## 4. Integração

- [x] 4.1 Rodar suíte frontend relevante (`TarefaDrawer`, Drawer, form) e corrigir regressões introduzidas por esta change

## Workflow follow-up

- Archive a change após review/implementação (`/openspec-archive-change`).
