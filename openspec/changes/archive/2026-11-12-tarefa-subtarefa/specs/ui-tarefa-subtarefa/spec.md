# Spec Delta

## Purpose

Mostra subtarefas na lista (árvore ou card solto filtrado) e permite criá-las só no drawer do pai, com confirmação antes de ações em cascata.

## ADDED Requirements

### Requirement: Criar subtarefa só no drawer do pai
A UI MUST oferecer criar subtarefa apenas no drawer de uma tarefa-raiz existente. A lista MUST NÃO ter campo de adicionar subtarefa no card expandido. O fluxo MUST enviar `parentId` do pai aberto.

#### Scenario: Drawer do pai mostra criação
- **GIVEN** o drawer de uma tarefa-raiz aberta
- **WHEN** o usuário vê a seção de subtarefas
- **THEN** há um controle para adicionar subtarefa
- **AND** ao confirmar, a subtarefa aparece na lista do drawer

#### Scenario: Card expandido sem criar
- **GIVEN** o card do pai expandido na lista
- **WHEN** o usuário vê os filhos
- **THEN** não há controle de adicionar subtarefa naquele card

### Requirement: Lista sem filtro é árvore recolhida
Em `/tarefas` sem filtro que “solte” o filho, a UI MUST listar o pai como card com botão de expandir, default recolhido. Filhos MUST aparecer aninhados só após expandir. Estado de expandir MUST ser só da sessão (não na URL).

#### Scenario: Pai recolhido
- **GIVEN** uma raiz com duas subtarefas e a lista sem filtro de prazo/status/prioridade/tag que isole o filho
- **WHEN** a lista renderiza
- **THEN** o pai aparece como um card
- **AND** os filhos não aparecem até expandir

#### Scenario: Expandir mostra filhos
- **GIVEN** o card do pai recolhido
- **WHEN** o usuário aciona expandir
- **THEN** as subtarefas não deletadas aparecem sob o pai
- **AND** cada filho aninhado não precisa do indicador “Subtarefa de”

### Requirement: Filtro solta o filho se o pai não está no resultado
Quando um filtro faz o filho entrar no resultado e o pai não, a UI MUST mostrar o filho como card próprio com indicador “Subtarefa de {título do pai}” que abre o drawer do pai. Se pai e filho estão no resultado, a UI MUST NÃO duplicar: filho fica aninhado no pai expandido.

#### Scenario: Só o filho bate no filtro
- **GIVEN** o filho tem prazo hoje e o prazo do pai não entra no filtro
- **WHEN** a lista filtra prazo hoje
- **THEN** o filho aparece como card solto
- **AND** o indicador aponta o título do pai

#### Scenario: Pai e filho no mesmo resultado
- **GIVEN** filtro de prioridade que ambos satisfazem
- **WHEN** a lista renderiza
- **THEN** o pai aparece com expandir
- **AND** o filho não aparece como segundo card no mesmo nível

### Requirement: Pai sem sessão; folha com sessão
O card do pai MUST NÃO oferecer Iniciar/Concluir de sessão. Folha (solta ou aninhada) MUST manter as ações de sessão já especificadas em `ui-tarefa-sessao`. Duração no drawer do pai com filhos MUST ser somente leitura.

#### Scenario: Card do pai
- **GIVEN** um pai com filhos e a lista sem filtro
- **WHEN** o usuário vê o card do pai
- **THEN** há expandir e ações de status/excluir
- **AND** não há Iniciar nem Concluir de sessão

#### Scenario: Duração bloqueada no drawer do pai
- **GIVEN** o drawer de um pai com filhos
- **WHEN** o usuário vê o formulário
- **THEN** o campo de duração não é editável
- **AND** o valor exibido é a soma dos filhos

### Requirement: Confirmar cascata no pai
Antes de finalizar, cancelar ou excluir um pai que tenha filhos não deletados, a UI MUST mostrar um diálogo com opção de voltar ou confirmar. Confirmar MUST disparar a operação em cascata da API. Sem filhos, o fluxo atual (sem esse diálogo extra) MUST permanecer.

#### Scenario: Finalizar pai com filhos
- **GIVEN** um pai com filhos abertos
- **WHEN** o usuário escolhe finalizar o pai
- **THEN** um diálogo avisa que os filhos também serão concluídos
- **AND** cancelar no diálogo não altera status
- **AND** confirmar marca pai e filhos como finalizados

#### Scenario: Excluir pai com filhos
- **GIVEN** um pai com filhos ativos
- **WHEN** o usuário escolhe excluir o pai
- **THEN** um diálogo avisa que os filhos também serão excluídos
- **AND** confirmar soft-deleta a família

#### Scenario: Pai sem filhos
- **GIVEN** uma tarefa-raiz sem filhos
- **WHEN** o usuário finaliza, cancela ou exclui
- **THEN** o fluxo não exige o diálogo de subtarefas
