# Spec Delta

## ADDED Requirements

### Requirement: Vínculo N:N a partir do item
O sistema MUST permitir associar zero ou mais objetivos a cada tarefa raiz, hábito e projeto do usuário pelo campo `objetivoIds` no create e no PATCH. Presença do campo MUST substituir o conjunto; lista vazia MUST limpar; ausência MUST manter. Ids repetidos no array MUST contar uma vez.

#### Scenario: Criar tarefa com dois objetivos
- **GIVEN** dois objetivos ativos do usuário
- **WHEN** cria uma tarefa raiz com os dois ids em `objetivoIds`
- **THEN** recebe 201 e `objetivos` contém exatamente esses dois (id, titulo, status)

#### Scenario: PATCH substitui o conjunto
- **GIVEN** uma tarefa ligada aos objetivos A e B
- **WHEN** envia PATCH com `objetivoIds: [B]`
- **THEN** a tarefa fica ligada só a B
- **AND** A deixa de listar essa tarefa como direta

#### Scenario: PATCH sem o campo mantém vínculos
- **GIVEN** uma tarefa ligada a um objetivo
- **WHEN** envia PATCH só com `titulo`
- **THEN** o vínculo permanece

#### Scenario: Lista vazia limpa
- **GIVEN** um hábito ligado a um objetivo
- **WHEN** envia PATCH com `objetivoIds: []`
- **THEN** `objetivos` volta vazio

#### Scenario: Ids repetidos são idempotentes
- **GIVEN** um objetivo ativo
- **WHEN** cria um projeto com `objetivoIds` contendo o mesmo id duas vezes
- **THEN** persiste um único vínculo
- **AND** enviar de novo o mesmo conjunto não cria outra linha

### Requirement: Respostas trazem só vínculos diretos
Create, detalhe, listagem e update de tarefa, hábito e projeto MUST incluir `objetivos` com `id`, `titulo` e `status` de cada vínculo **direto**, lista vazia quando não houver. Objetivos herdados via projeto MUST NÃO aparecer nesse campo.

#### Scenario: Listagem de tarefas traz objetivos diretos
- **GIVEN** uma tarefa ligada direto ao objetivo A e cujo projeto está ligado ao objetivo B
- **WHEN** o usuário lista tarefas
- **THEN** essa tarefa traz só A em `objetivos`

#### Scenario: Item sem vínculo
- **GIVEN** um hábito sem objetivos
- **WHEN** o usuário busca o hábito
- **THEN** `objetivos` é uma lista vazia

### Requirement: Vínculo novo só em objetivo ativo
Incluir no conjunto um objetivo que ainda não estava ligado ao item MUST ser aceito somente se o objetivo estiver `ATIVO`. Objetivo `CONCLUIDO` ou `ARQUIVADO` ainda não ligado MUST responder 409 com `title` "Objetivo não está ativo", sem alterar vínculos. Objetivo já ligado MUST ser aceito de novo no conjunto mesmo depois de concluído ou arquivado. Remover um vínculo MUST ser permitido em qualquer status do objetivo.

#### Scenario: Ligar a objetivo ativo
- **GIVEN** um objetivo `ATIVO` do usuário
- **WHEN** cria uma tarefa raiz com esse id em `objetivoIds`
- **THEN** recebe 201 com o objetivo em `objetivos`

#### Scenario: Ligar a objetivo concluído rejeitado
- **GIVEN** um objetivo `CONCLUIDO` e uma tarefa sem vínculos
- **WHEN** envia PATCH da tarefa com esse id em `objetivoIds`
- **THEN** recebe 409 com `title` "Objetivo não está ativo"
- **AND** a tarefa permanece sem vínculos

#### Scenario: Reenviar objetivo já ligado depois de concluído
- **GIVEN** uma tarefa ligada a um objetivo que foi concluído depois do vínculo
- **WHEN** envia PATCH com o mesmo `objetivoIds` e um `titulo` novo
- **THEN** recebe 200, o título muda e o vínculo permanece

#### Scenario: Desvincular de objetivo arquivado
- **GIVEN** um projeto ligado a um objetivo `ARQUIVADO`
- **WHEN** envia PATCH com `objetivoIds: []`
- **THEN** recebe 200 e `objetivos` vazio

### Requirement: Objetivo de outro usuário no vínculo
Id de objetivo inexistente ou de outro usuário em `objetivoIds` MUST responder 404 com `title` "Objetivo não encontrado", sem criar nem alterar vínculos.

#### Scenario: Objetivo de outro usuário
- **GIVEN** um objetivo do usuário B
- **WHEN** o usuário A cria uma tarefa com esse id em `objetivoIds`
- **THEN** recebe 404 com `title` "Objetivo não encontrado"
- **AND** nenhuma tarefa nem vínculo é persistido

### Requirement: Subtarefa não recebe vínculo direto
Create ou PATCH de subtarefa com `objetivoIds` contendo algum id MUST ser rejeitado com 400 e `title` "Subtarefa não pode ser vinculada a objetivo", sem criar vínculo. Ausência do campo ou lista vazia MUST ser aceita. Herança via projeto MUST NÃO incluir subtarefas.

#### Scenario: Criar subtarefa com objetivoIds
- **GIVEN** uma tarefa raiz e um objetivo ativo
- **WHEN** cria uma subtarefa com esse objetivo em `objetivoIds`
- **THEN** recebe 400 com `title` "Subtarefa não pode ser vinculada a objetivo"
- **AND** nenhum vínculo é persistido

#### Scenario: Subtarefa sem o campo
- **WHEN** cria uma subtarefa sem `objetivoIds`
- **THEN** a subtarefa é criada
- **AND** `objetivos` é lista vazia

### Requirement: Herança via projeto na leitura
Uma tarefa raiz MUST contar para os objetivos ligados ao seu projeto, além dos ligados direto a ela. Se a mesma tarefa chegar ao mesmo objetivo pelos dois caminhos, MUST aparecer uma vez com origem `DIRETO`. Hábitos MUST NÃO herdar objetivos de projeto.

#### Scenario: Tarefa do projeto entra como VIA_PROJETO
- **GIVEN** o projeto P ligado ao objetivo O e uma tarefa raiz em P sem vínculo direto
- **WHEN** lista os itens de O
- **THEN** a tarefa aparece em `tarefas` com origem `VIA_PROJETO`

#### Scenario: Direto ganha da herança
- **GIVEN** a mesma tarefa raiz ligada direto a O e pertencente a um projeto também ligado a O
- **WHEN** lista os itens de O
- **THEN** a tarefa aparece uma vez com origem `DIRETO`

#### Scenario: Hábito não herda do projeto
- **GIVEN** um projeto ligado a O e um hábito sem vínculo direto
- **WHEN** lista os itens de O
- **THEN** o hábito não aparece

### Requirement: Listagem de itens do objetivo
`GET /objetivos/{id}/itens` MUST devolver `{ tarefas, habitos, projetos }` com os itens diretos e as tarefas raiz herdadas, cada um com origem `DIRETO` ou `VIA_PROJETO`. A resposta MUST NÃO ser paginada. Grupo sem itens MUST ser lista vazia. Objetivo inexistente ou de outro usuário MUST responder 404 com `title` "Objetivo não encontrado". Item com soft delete MUST NÃO aparecer; o vínculo MUST permanecer no banco.

#### Scenario: Objetivo com os três tipos
- **GIVEN** um objetivo ligado a uma tarefa raiz, um hábito e um projeto
- **WHEN** o dono lista os itens
- **THEN** recebe 200 com a tarefa em `tarefas` (DIRETO), o hábito em `habitos` (DIRETO) e o projeto em `projetos` (DIRETO)

#### Scenario: Objetivo sem itens
- **GIVEN** um objetivo do usuário sem vínculos
- **WHEN** lista os itens
- **THEN** recebe 200 com `tarefas`, `habitos` e `projetos` vazios

#### Scenario: Soft delete some da lista
- **GIVEN** uma tarefa raiz ligada a um objetivo e depois soft-deletada
- **WHEN** lista os itens do objetivo
- **THEN** a tarefa não aparece
- **AND** restaurar a tarefa a faz reaparecer como DIRETO

#### Scenario: Objetivo de outro usuário
- **GIVEN** um objetivo do usuário B
- **WHEN** o usuário A lista os itens
- **THEN** recebe 404 com `title` "Objetivo não encontrado"

### Requirement: Encerrar objetivo não remove vínculos
Concluir ou arquivar um objetivo MUST NÃO apagar as linhas de vínculo. Reabrir MUST NÃO alterar o conjunto. Listar itens de objetivo concluído ou arquivado MUST continuar devolvendo os itens.

#### Scenario: Arquivar mantém os itens
- **GIVEN** um objetivo ativo ligado a uma tarefa raiz
- **WHEN** o usuário arquiva o objetivo e lista os itens
- **THEN** a tarefa continua em `tarefas` com origem `DIRETO`
