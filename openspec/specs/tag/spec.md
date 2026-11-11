# tag Specification

## Purpose

Permite ao usuário manter um catálogo de tags (nome e cor) e vinculá-las de forma independente a tarefas, hábitos, projetos e itens Decidir, com filtro e ordenação por relevância de match nas listagens.

## Requirements

### Requirement: Catálogo de tags do usuário
O sistema MUST expor CRUD autenticado de tags pertencentes ao usuário. Cada tag MUST ter `nome` (obrigatório, único por usuário em comparação case-insensitive) e `cor` (obrigatório, hex `#RRGGBB`). Hierarquia de tags MUST NÃO existir neste corte.

#### Scenario: Criar tag
- **GIVEN** o usuário autenticado
- **WHEN** cria uma tag com nome válido e cor hex
- **THEN** a tag é persistida e devolvida com id
- **AND** fica disponível para vínculo e filtro

#### Scenario: Nome duplicado rejeitado
- **GIVEN** já existe tag do usuário com nome `estudo_conceito`
- **WHEN** tenta criar outra com nome `Estudo_Conceito`
- **THEN** o sistema rejeita a operação por conflito de unicidade

#### Scenario: Listar só as próprias tags
- **GIVEN** tags de dois usuários distintos
- **WHEN** o usuário A lista tags
- **THEN** só recebe as tags de A

### Requirement: Atualizar nome e cor
O sistema MUST permitir alterar `nome` e/ou `cor` de uma tag do usuário, mantendo a unicidade de nome.

#### Scenario: Renomear tag
- **GIVEN** uma tag do usuário
- **WHEN** atualiza o nome para um valor ainda livre
- **THEN** a tag passa a expor o novo nome
- **AND** vínculos existentes continuam apontando para o mesmo id

### Requirement: Hard delete remove vínculos
Ao apagar uma tag, o sistema MUST removê-la definitivamente e MUST remover todos os vínculos dessa tag com tarefas, hábitos, projetos e itens Decidir do usuário. Soft delete de tag MUST NÃO ser usado.

#### Scenario: Apagar tag com vínculos
- **GIVEN** uma tag vinculada a pelo menos uma tarefa, um hábito, um projeto ou um item Decidir
- **WHEN** o usuário apaga a tag
- **THEN** a tag deixa de existir
- **AND** nenhum item permanece vinculado a esse id

### Requirement: Vínculo N:N independente
O sistema MUST permitir associar zero ou mais tags a cada tarefa, hábito, projeto e item Decidir do usuário. Tags de projeto MUST NÃO ser herdadas por tarefas, hábitos ou itens Decidir desse projeto.

#### Scenario: Tarefa com várias tags
- **GIVEN** duas tags do usuário
- **WHEN** associa ambas a uma tarefa
- **THEN** a resposta da tarefa inclui as duas tags
- **AND** um projeto do usuário com as mesmas tags não altera o conjunto da tarefa

#### Scenario: Projeto tagueado não propaga
- **GIVEN** um projeto com a tag `controle_rotina` e uma tarefa desse projeto sem tags
- **WHEN** filtra tarefas por `controle_rotina`
- **THEN** essa tarefa NÃO entra no resultado só por causa do projeto

#### Scenario: Item Decidir com tags
- **GIVEN** duas tags do usuário
- **WHEN** associa ambas a um item Decidir
- **THEN** a resposta do item inclui as duas tags
- **AND** tags do projeto do item (se houver) NÃO alteram o conjunto do item

### Requirement: Respostas enriquecidas com tags
Listagens e detalhe de tarefa, hábito, projeto e item Decidir MUST incluir as tags vinculadas do item (id, nome, cor), no mínimo no create/update/get/list relevantes.

#### Scenario: Listagem de tarefas traz tags
- **GIVEN** tarefas com e sem tags
- **WHEN** o usuário lista tarefas
- **THEN** cada item traz a coleção de tags vinculadas (vazia se nenhuma)

#### Scenario: Listagem Decidir traz tags
- **GIVEN** itens Decidir com e sem tags
- **WHEN** o usuário lista Decidir
- **THEN** cada item traz a coleção de tags vinculadas (vazia se nenhuma)

### Requirement: Filtro por tags nas listagens
As listagens de tarefa, hábito, projeto e Decidir MUST aceitar filtro por um ou mais ids de tag do usuário. O resultado MUST ser a união dos itens que possuem pelo menos uma das tags pedidas. Itens sem nenhuma das tags MUST ficar de fora.

#### Scenario: Filtro com uma tag
- **GIVEN** itens com tags distintas
- **WHEN** filtra por um único id de tag
- **THEN** só entram itens que possuem essa tag

#### Scenario: Filtro com várias tags (união)
- **GIVEN** item A com tags [T1, T2], item B só com T1, item C só com T3
- **WHEN** filtra por T1 e T2
- **THEN** A e B entram no resultado
- **AND** C fica de fora

#### Scenario: Filtro Decidir por tag
- **GIVEN** itens Decidir com tags distintas
- **WHEN** filtra a listagem Decidir por um id de tag
- **THEN** só entram itens Decidir que possuem essa tag

### Requirement: Ordenação E antes de OU
Quando o filtro de tags tem dois ou mais ids, o sistema MUST ordenar os resultados de modo que itens com **todas** as tags pedidas (match E) apareçam antes dos que têm **apenas algumas** (match OU). Com uma única tag no filtro, essa distinção NÃO se aplica. Demais critérios de ordenação/paginação do recurso permanecem aplicáveis depois dessa prioridade de match. Isso MUST valer também para a listagem Decidir.

#### Scenario: Match total antes de parcial
- **GIVEN** filtro por T1 e T2, item A com [T1, T2], item B só com T1
- **WHEN** lista o recurso filtrado
- **THEN** A aparece antes de B na ordenação por relevância de tag

#### Scenario: Uma tag no filtro
- **GIVEN** filtro só por T1
- **WHEN** lista o recurso
- **THEN** todos os matches são tratados com a mesma prioridade de match de tag

#### Scenario: Match E→OU em Decidir
- **GIVEN** filtro por T1 e T2 na listagem Decidir, item A com [T1, T2], item B só com T1
- **WHEN** lista Decidir filtrado
- **THEN** A aparece antes de B
