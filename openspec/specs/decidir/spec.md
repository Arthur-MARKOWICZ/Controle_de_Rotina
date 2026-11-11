# decidir Specification

## Purpose

Permite capturar itens que ainda não são tarefas planejáveis, decidir promovendo-os a tarefa ou descartando-os, e manter o vínculo histórico quando promovidos — sem entrar no planner enquanto estiverem só em Decidir.

## Requirements

### Requirement: Criar item para decidir
O sistema MUST permitir criar um item Decidir autenticado com `titulo` obrigatório. `descricao`, `decidirAte`, `projetoId` e `tagIds` MUST ser opcionais. Soft delete do item MUST NÃO existir neste corte.

#### Scenario: Criar só com título
- **GIVEN** o usuário autenticado
- **WHEN** cria um item com título válido
- **THEN** o item é persistido com `tarefaId` null
- **AND** fica disponível na listagem de abertos

#### Scenario: Criar com campos opcionais
- **GIVEN** o usuário autenticado com tags e um projeto próprios
- **WHEN** cria um item com título, descrição, `decidirAte`, `projetoId` e `tagIds`
- **THEN** a resposta inclui esses campos e as tags resumidas
- **AND** `tarefaId` permanece null

#### Scenario: Título obrigatório
- **GIVEN** o usuário autenticado
- **WHEN** tenta criar um item sem título (ou título em branco)
- **THEN** o sistema rejeita a operação como inválida

### Requirement: Listar abertos e promovidos
A listagem padrão MUST retornar apenas itens do usuário com `tarefaId` null (abertos). O sistema MUST permitir listar itens já promovidos (`tarefaId` não null) via filtro explícito. Itens de outro usuário MUST NÃO aparecer.

#### Scenario: Lista padrão só abertos
- **GIVEN** o usuário tem um item aberto e um promovido
- **WHEN** lista Decidir sem filtro de promovidos
- **THEN** só o aberto entra no resultado

#### Scenario: Listar promovidos
- **GIVEN** o usuário tem itens promovidos
- **WHEN** lista com filtro de promovidos
- **THEN** só itens com `tarefaId` preenchido entram no resultado

### Requirement: Atualizar item aberto
Enquanto `tarefaId` for null, o sistema MUST permitir alterar título, descrição, `decidirAte`, `projetoId` e o conjunto de tags (substituição quando `tagIds` for enviado). Item de outro usuário ou inexistente MUST resultar em 404.

#### Scenario: Editar aberto
- **GIVEN** um item aberto do usuário
- **WHEN** atualiza título e descrição
- **THEN** a resposta reflete os novos valores

#### Scenario: Substituir tags
- **GIVEN** um item aberto com tags
- **WHEN** envia `tagIds` com um novo conjunto
- **THEN** os vínculos passam a ser exatamente esse conjunto

### Requirement: Item promovido somente leitura
Quando `tarefaId` não for null, o sistema MUST rejeitar alterações de título, descrição, `decidirAte`, `projetoId` e tags nesse item. Promover de novo MUST ser rejeitado enquanto o vínculo existir.

#### Scenario: PATCH em promovido rejeitado
- **GIVEN** um item com `tarefaId` preenchido
- **WHEN** tenta atualizar o título
- **THEN** o sistema rejeita a operação com conflito ou inválido

#### Scenario: Promover de novo rejeitado
- **GIVEN** um item já promovido
- **WHEN** solicita promover novamente
- **THEN** o sistema rejeita a operação

### Requirement: Descartar com hard delete
O sistema MUST permitir descartar um item aberto com hard delete. Status `DESCARTADO` MUST NÃO existir. Descartar item inexistente ou de outro usuário MUST resultar em 404.

#### Scenario: Descartar aberto
- **GIVEN** um item aberto do usuário
- **WHEN** descarta o item
- **THEN** o item deixa de existir
- **AND** não aparece em nenhuma listagem

### Requirement: Promover cria tarefa e vincula
Promover um item aberto MUST criar uma tarefa do usuário com o título do item e a descrição copiada quando houver. Duração, prazo e prioridade MUST ser opcionais no body (mesmo contrato mínimo de criar tarefa). Tags do item MUST ser copiadas para a tarefa. Se o item tiver `projetoId`, a tarefa MUST herdar esse projeto. O item MUST permanecer com `tarefaId` apontando para a tarefa criada.

#### Scenario: Promover mínimo
- **GIVEN** um item aberto só com título
- **WHEN** promove sem campos extras de tarefa
- **THEN** nasce uma tarefa com esse título
- **AND** o item fica com `tarefaId` da tarefa

#### Scenario: Promover copia descrição tags e projeto
- **GIVEN** um item aberto com descrição, tags e `projetoId`
- **WHEN** promove
- **THEN** a tarefa tem a mesma descrição, as mesmas tags e o mesmo `projetoId`
- **AND** o item permanece somente leitura com `tarefaId` preenchido

#### Scenario: Promover com duração opcional
- **GIVEN** um item aberto
- **WHEN** promove enviando `duracaoMinutos` válido
- **THEN** a tarefa criada usa essa duração

### Requirement: Soft delete da tarefa reabre o item
Quando a tarefa linkada receber soft delete, o sistema MUST zerar `tarefaId` do item correspondente do mesmo usuário. O item MUST voltar à listagem de abertos e MUST voltar a ser editável. Restaurar a tarefa MUST NÃO religar automaticamente o item.

#### Scenario: Soft delete reabre
- **GIVEN** um item promovido ligado à tarefa T
- **WHEN** a tarefa T é soft-deletada
- **THEN** o item fica com `tarefaId` null
- **AND** aparece de novo na lista de abertos

#### Scenario: Restore não religa
- **GIVEN** um item reaberto após soft delete da tarefa T
- **WHEN** a tarefa T é restaurada
- **THEN** o item permanece com `tarefaId` null

### Requirement: Projeto removido zera projetoId
Se o projeto referenciado pelo item deixar de estar disponível para vínculo (arquivado ou removido conforme regra do domínio de projeto), o sistema MUST zerar `projetoId` no item sem apagar o item.

#### Scenario: Projeto arquivado
- **GIVEN** um item aberto com `projetoId` de um projeto do usuário
- **WHEN** esse projeto é arquivado (ou equivalente que impede vínculo)
- **THEN** o item permanece
- **AND** `projetoId` fica null

### Requirement: decidirAte sem status de atraso
`decidirAte` vencido MUST NÃO criar status de atraso no item. A listagem de abertos MUST permitir ordenar priorizando `decidirAte` (nulos por último ou conforme contrato estável documentado na API).

#### Scenario: Vencido continua aberto
- **GIVEN** um item aberto com `decidirAte` no passado
- **WHEN** lista abertos
- **THEN** o item permanece na lista sem status especial de atraso
