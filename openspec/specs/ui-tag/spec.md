# ui-tag Specification

## Purpose

Expõe na interface a gestão do catálogo de tags, o vínculo em tarefa/hábito/projeto e o filtro com relevância E→OU, inclusive dentro dos grupos de prazo da lista de tarefas.

## Requirements

### Requirement: Página de gestão de tags
O sistema MUST oferecer uma rota `/tags` onde o usuário autenticado lista, cria, renomeia, altera cor e apaga tags. O destino MUST estar acessível a partir do menu “Mais” (e equivalente na sidebar, se houver grupo secundário).

#### Scenario: Abrir gestão
- **GIVEN** o usuário autenticado
- **WHEN** navega para Tags via “Mais” ou `/tags`
- **THEN** vê a lista das suas tags com nome e cor
- **AND** pode criar uma tag nova com nome e cor

#### Scenario: Apagar com confirmação
- **GIVEN** uma tag existente
- **WHEN** o usuário pede para apagar
- **THEN** o sistema pede confirmação antes do hard delete
- **AND** após confirmar a tag some da lista e dos pickers

### Requirement: Picker no item com create inline
Nos fluxos de criar/editar tarefa, hábito e projeto, o sistema MUST permitir escolher tags existentes e criar uma tag nova digitando o nome no picker. Tag criada inline MUST usar uma cor default do sistema; ajuste de cor/renomear MUST estar disponível via gestão ou atalho “Gerenciar” no picker.

#### Scenario: Criar tag no picker
- **GIVEN** o formulário de uma tarefa (ou hábito/projeto) aberto
- **WHEN** o usuário digita um nome de tag inexistente e confirma a criação
- **THEN** a tag é criada no catálogo com cor default
- **AND** fica selecionada no item

#### Scenario: Atalho gerenciar no picker
- **GIVEN** o picker de tags aberto
- **WHEN** o usuário aciona “Gerenciar”
- **THEN** consegue chegar à gestão de tags (rota `/tags` ou equivalente) sem perder o contexto do produto

### Requirement: Chips visíveis no item
Cards e/ou formulários de tarefa, hábito e projeto MUST exibir as tags vinculadas (nome e cor) de forma legível, sem depender só do detalhe expandido.

#### Scenario: Chip na lista de tarefas
- **GIVEN** uma tarefa com pelo menos uma tag
- **WHEN** o usuário vê a lista de tarefas
- **THEN** a tag aparece associada ao item (chip ou equivalente)

### Requirement: Filtro por tags nas listas
As telas de listagem de tarefas, hábitos e projetos MUST permitir filtrar por uma ou mais tags do catálogo, refletindo o filtro na URL quando a lista já usa query params.

#### Scenario: Filtrar tarefas por tag
- **GIVEN** tarefas com tags distintas
- **WHEN** o usuário seleciona uma ou mais tags no filtro da lista
- **THEN** só itens com pelo menos uma dessas tags permanecem visíveis
- **AND** o estado do filtro é recuperável via URL na lista de tarefas

### Requirement: Ordenação E→OU nos grupos de prazo
Na lista de tarefas com agrupamento por prazo, quando há filtro com duas ou mais tags, o sistema MUST colocar dentro de cada grupo primeiro os itens com match E e depois os com match OU. Listas de hábito e projeto (sem esses grupos) MUST aplicar a mesma prioridade na ordenação da lista.

#### Scenario: Dentro do grupo Hoje
- **GIVEN** filtro por T1 e T2 e duas tarefas no grupo “Hoje” (uma com match E, outra só OU)
- **WHEN** a lista é renderizada
- **THEN** a de match E aparece antes da de match OU dentro de “Hoje”

#### Scenario: Lista de projetos
- **GIVEN** filtro por duas tags na lista de projetos
- **WHEN** a lista é renderizada
- **THEN** projetos com todas as tags pedidas aparecem antes dos que têm só algumas
