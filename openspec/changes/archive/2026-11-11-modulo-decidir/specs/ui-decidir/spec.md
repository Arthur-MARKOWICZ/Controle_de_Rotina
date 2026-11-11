# Spec Delta

## Purpose

Expõe na interface a fila Decidir: captura rápida, lista de abertos, promover a tarefa, descartar, tags/projeto e destaque visual de prazo de decisão vencido, com destino próprio na navegação principal.

## ADDED Requirements

### Requirement: Destino Decidir na nav principal
O sistema MUST oferecer a rota `/decidir` acessível pela navegação principal (sidebar e bottom nav) com o rótulo **Decidir**, sem depender só do menu “Mais”.

#### Scenario: Abrir pela nav
- **GIVEN** o usuário autenticado
- **WHEN** aciona o link Decidir na nav principal
- **THEN** navega para `/decidir`
- **AND** vê a lista de itens abertos (ou empty state)

### Requirement: Captura e lista de abertos
A página `/decidir` MUST permitir criar um item com título (demais campos opcionais no fluxo mínimo ou no detalhe) e listar os itens abertos do usuário. Empty state MUST orientar a captura quando não houver abertos.

#### Scenario: Criar pela página
- **GIVEN** o usuário em `/decidir`
- **WHEN** informa um título e confirma a captura
- **THEN** o item aparece na lista de abertos

#### Scenario: Empty state
- **GIVEN** o usuário sem itens abertos
- **WHEN** abre `/decidir`
- **THEN** vê empty state com orientação para capturar

### Requirement: Promover e descartar na UI
Para cada item aberto, a UI MUST permitir promover (abrindo fluxo que pode completar campos opcionais da tarefa) e descartar com confirmação. Após promover, o item MUST sair da lista de abertos. Após descartar, MUST sumir da lista.

#### Scenario: Promover remove da fila
- **GIVEN** um item aberto na lista
- **WHEN** o usuário promove com sucesso
- **THEN** o item deixa de aparecer na lista de abertos
- **AND** a tarefa criada fica disponível no fluxo de tarefas do produto

#### Scenario: Descartar com confirmação
- **GIVEN** um item aberto na lista
- **WHEN** o usuário pede para descartar e confirma
- **THEN** o item some da lista

### Requirement: Tags e projeto no item aberto
Na criação/edição de item aberto, a UI MUST permitir escolher tags (reutilizando o picker existente, inclusive create inline) e projeto opcional. Chips de tags MUST aparecer na lista ou no detalhe do item aberto.

#### Scenario: Tag no item
- **GIVEN** o formulário/detalhe de um item aberto
- **WHEN** o usuário seleciona uma tag
- **THEN** a tag fica associada ao item e visível após salvar

### Requirement: Destaque de decidirAte vencido
Itens abertos com `decidirAte` no passado MUST ser destacados visualmente na lista (estilo de atraso/aviso), sem inventar um status de domínio na UI.

#### Scenario: Vencido destacado
- **GIVEN** um item aberto com `decidirAte` ontem
- **WHEN** a lista de abertos é renderizada
- **THEN** o item é visualmente distinguível dos demais abertos

### Requirement: Acesso a já decididos
A UI MUST permitir ver itens já promovidos (histórico com link/indicação da tarefa), fora da fila padrão de abertos.

#### Scenario: Ver promovidos
- **GIVEN** o usuário tem pelo menos um item promovido
- **WHEN** aciona a visão de já decididos
- **THEN** vê esses itens em modo somente leitura
- **AND** não consegue editá-los nem promover de novo pela UI

### Requirement: Filtro por tags em Decidir
A lista de Decidir MUST permitir filtrar por uma ou mais tags do catálogo, refletindo o filtro na URL quando a página usar query params.

#### Scenario: Filtrar abertos por tag
- **GIVEN** itens abertos com tags distintas
- **WHEN** o usuário seleciona uma tag no filtro
- **THEN** só itens abertos com essa tag permanecem visíveis
