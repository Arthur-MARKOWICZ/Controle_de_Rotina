# Spec Delta

## Purpose

Define a aba Decidir do app Android: capturar coisas ainda não decididas, editá-las, descartá-las ou promovê-las a tarefa, usando o contrato atual de `/decidir`.

## ADDED Requirements

### Requirement: Lista de abertos
A aba Decidir MUST listar os itens abertos via `GET /decidir` (sem `promovidos`), com título e `decidirAte`. Um item com `decidirAte` no passado MUST ter destaque visual de vencido, sem virar status de atraso.

#### Scenario: Abrir Decidir
- **GIVEN** o usuário tem itens abertos
- **WHEN** abre a aba Decidir
- **THEN** cada item aparece com título e data limite, quando houver

#### Scenario: Data limite vencida
- **GIVEN** um item com `decidirAte` ontem
- **WHEN** a lista é exibida
- **THEN** a data do item aparece destacada como vencida

#### Scenario: Nada para decidir
- **GIVEN** a API devolve lista vazia
- **WHEN** o usuário abre a aba Decidir
- **THEN** aparece um estado vazio com a ação Novo item

### Requirement: Capturar item
O app MUST permitir criar item com título obrigatório (até 120), descrição opcional (até 2000) e `decidirAte` opcional, via `POST /decidir`.

#### Scenario: Capturar só com título
- **WHEN** o usuário informa o título e confirma
- **THEN** o item é criado e aparece na lista

#### Scenario: Título vazio
- **WHEN** o usuário confirma sem título
- **THEN** o app não envia a requisição e marca o campo título

### Requirement: Editar item aberto
Tocar num item aberto MUST abrir o detalhe, onde título, descrição e `decidirAte` podem ser editados via `PATCH /decidir/{id}`. Limpar a data MUST enviar `decidirAte: null`.

#### Scenario: Mudar a data limite
- **GIVEN** o detalhe de um item aberto
- **WHEN** o usuário troca a data e salva
- **THEN** a nova data aparece na lista

### Requirement: Descartar item
O detalhe de um item aberto MUST oferecer Descartar, com confirmação avisando que não dá para desfazer, chamando `DELETE /decidir/{id}`.

#### Scenario: Descartar
- **GIVEN** um item aberto
- **WHEN** o usuário toca em Descartar e confirma
- **THEN** o item some da lista

### Requirement: Promover a tarefa
O detalhe de um item aberto MUST oferecer Virar tarefa, que pede duração, prazo e prioridade opcionais e chama `POST /decidir/{id}/promover`. Em sucesso, o item sai dos abertos e a tarefa criada aparece na aba Tarefas.

#### Scenario: Promover sem campos extras
- **GIVEN** um item aberto
- **WHEN** o usuário toca em Virar tarefa e confirma sem preencher nada
- **THEN** o item sai da lista de abertos
- **AND** a tarefa com o mesmo título aparece na aba Tarefas

### Requirement: Ver decididos
A aba Decidir MUST permitir ver os itens já promovidos (`GET /decidir?promovidos=true`) em modo só leitura, sem editar, descartar nem promover.

#### Scenario: Abrir decididos
- **WHEN** o usuário troca o filtro para Decididos
- **THEN** a lista mostra os itens promovidos sem ações de edição
