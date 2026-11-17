# objetivo Specification

## Purpose

Permite ao usuário registrar o que quer alcançar (objetivos com título, descrição e prazo opcionais) e controlar explicitamente seu ciclo de vida: ativo, concluído, arquivado e reaberto.

## Requirements

### Requirement: Cadastro de objetivo
O sistema MUST permitir ao usuário autenticado criar um objetivo com `titulo` (obrigatório, 1 a 80 caracteres após trim), `descricao` opcional (até 500) e `prazo` opcional (data sem horário). Todo objetivo criado MUST nascer com `status` `ATIVO` e com `concluidoEm` e `arquivadoEm` nulos.

#### Scenario: Criar objetivo só com título
- **GIVEN** o usuário autenticado
- **WHEN** cria um objetivo com `titulo` "Perder 5 kg"
- **THEN** recebe 201 com o objetivo, `status` `ATIVO`, `prazo`, `descricao`, `concluidoEm` e `arquivadoEm` nulos

#### Scenario: Título ausente ou em branco
- **WHEN** cria um objetivo sem `titulo` ou com `titulo` só de espaços
- **THEN** recebe 400 com `title` "Dados inválidos" e erro na propriedade `titulo`
- **AND** nenhum objetivo é persistido

#### Scenario: Título ou descrição acima do limite
- **WHEN** cria um objetivo com `titulo` de 81 caracteres ou `descricao` de 501 caracteres
- **THEN** recebe 400 com `title` "Dados inválidos" e erro na propriedade excedida
- **AND** nenhum objetivo é persistido

#### Scenario: Prazo em formato inválido
- **WHEN** cria um objetivo com `prazo` "31/12/2026"
- **THEN** recebe 400 com `title` "Requisição inválida"
- **AND** nenhum objetivo é persistido

### Requirement: Objetivo pertence ao usuário
Toda operação sobre um objetivo específico MUST considerar apenas objetivos do usuário autenticado. Objetivo inexistente ou de outro usuário MUST responder 404 com `title` "Objetivo não encontrado", sem alterar nenhum dado.

#### Scenario: Acessar objetivo de outro usuário
- **GIVEN** um objetivo do usuário B
- **WHEN** o usuário A tenta detalhar, editar, concluir, arquivar ou reabrir esse objetivo
- **THEN** recebe 404 com `title` "Objetivo não encontrado"
- **AND** o objetivo de B permanece inalterado

#### Scenario: Listagem não mostra objetivos de outros usuários
- **GIVEN** objetivos ativos dos usuários A e B
- **WHEN** o usuário A lista objetivos
- **THEN** recebe apenas os objetivos de A

### Requirement: Prazo não pode estar no passado
Um `prazo` anterior à data de hoje no fuso `America/Sao_Paulo` MUST ser rejeitado com 400 e `title` "Prazo inválido" na criação. Na edição, a rejeição MUST ocorrer apenas quando o `prazo` enviado é diferente do prazo atual. Prazo igual a hoje MUST ser aceito.

#### Scenario: Prazo de hoje aceito
- **GIVEN** hoje é 17/11/2026 em BRT
- **WHEN** cria um objetivo com `prazo` 2026-11-17
- **THEN** recebe 201 com `prazo` 2026-11-17

#### Scenario: Prazo de ontem rejeitado na criação
- **GIVEN** hoje é 17/11/2026 em BRT
- **WHEN** cria um objetivo com `prazo` 2026-11-16
- **THEN** recebe 400 com `title` "Prazo inválido"
- **AND** nenhum objetivo é persistido

#### Scenario: Virada do dia em BRT
- **GIVEN** o instante atual é 2026-11-18T02:30Z (ainda 17/11 23:30 em BRT)
- **WHEN** cria um objetivo com `prazo` 2026-11-17
- **THEN** recebe 201

#### Scenario: Reenviar prazo vencido sem alterá-lo
- **GIVEN** um objetivo com `prazo` 2026-11-30 e hoje é 05/12/2026 em BRT
- **WHEN** edita o objetivo enviando `titulo` novo e `prazo` 2026-11-30
- **THEN** recebe 200 com o novo título e o mesmo prazo

#### Scenario: Trocar para outro prazo no passado
- **GIVEN** um objetivo com `prazo` 2026-11-30 e hoje é 05/12/2026 em BRT
- **WHEN** edita o objetivo enviando `prazo` 2026-12-01
- **THEN** recebe 400 com `title` "Prazo inválido"
- **AND** o objetivo mantém `prazo` 2026-11-30

### Requirement: Edição parcial em qualquer status
O sistema MUST permitir editar `titulo`, `descricao` e `prazo` de objetivo do usuário em qualquer `status`, sem alterar `status`, `concluidoEm` ou `arquivadoEm`. Campo ausente no corpo MUST manter o valor atual. `prazo` com `null` explícito MUST remover o prazo. `descricao` em branco MUST remover a descrição.

#### Scenario: Editar só o título
- **GIVEN** um objetivo com `descricao` e `prazo` definidos
- **WHEN** edita enviando apenas `titulo`
- **THEN** recebe 200 com o novo título e `descricao` e `prazo` inalterados

#### Scenario: Remover prazo com null explícito
- **GIVEN** um objetivo com `prazo` definido
- **WHEN** edita enviando `prazo: null`
- **THEN** recebe 200 com `prazo` nulo

#### Scenario: Editar objetivo concluído
- **GIVEN** um objetivo `CONCLUIDO` com `concluidoEm` definido
- **WHEN** edita o `titulo`
- **THEN** recebe 200 com o novo título
- **AND** `status` e `concluidoEm` permanecem iguais

#### Scenario: Título em branco na edição
- **WHEN** edita enviando `titulo` só de espaços
- **THEN** recebe 400 com `title` "Dados inválidos" e erro na propriedade `titulo`
- **AND** o objetivo permanece inalterado

### Requirement: Concluir objetivo
O sistema MUST concluir um objetivo somente por ação explícita do usuário e somente a partir de `ATIVO`, mudando para `CONCLUIDO` e preenchendo `concluidoEm` com o instante atual. O objetivo MUST NÃO ser concluído automaticamente.

#### Scenario: Concluir objetivo ativo
- **GIVEN** um objetivo `ATIVO`
- **WHEN** o usuário conclui o objetivo
- **THEN** recebe 200 com `status` `CONCLUIDO` e `concluidoEm` igual ao instante atual

#### Scenario: Concluir objetivo já concluído
- **GIVEN** um objetivo `CONCLUIDO`
- **WHEN** o usuário conclui novamente
- **THEN** recebe 409 com `title` "Objetivo já concluído"
- **AND** `concluidoEm` permanece o original

#### Scenario: Concluir objetivo arquivado
- **GIVEN** um objetivo `ARQUIVADO`
- **WHEN** o usuário tenta concluir
- **THEN** recebe 409 com `title` "Objetivo arquivado"
- **AND** o objetivo permanece `ARQUIVADO`, com as datas inalteradas

### Requirement: Arquivar objetivo
O sistema MUST arquivar um objetivo `ATIVO` ou `CONCLUIDO`, mudando para `ARQUIVADO` e preenchendo `arquivadoEm` com o instante atual. Ao arquivar um objetivo `CONCLUIDO`, `concluidoEm` MUST ser mantido.

#### Scenario: Arquivar objetivo ativo
- **GIVEN** um objetivo `ATIVO`
- **WHEN** o usuário arquiva
- **THEN** recebe 200 com `status` `ARQUIVADO`, `arquivadoEm` preenchido e `concluidoEm` nulo

#### Scenario: Arquivar objetivo concluído mantém data de conclusão
- **GIVEN** um objetivo `CONCLUIDO` com `concluidoEm` definido
- **WHEN** o usuário arquiva
- **THEN** recebe 200 com `status` `ARQUIVADO`, `arquivadoEm` preenchido e o mesmo `concluidoEm`

#### Scenario: Arquivar objetivo já arquivado
- **GIVEN** um objetivo `ARQUIVADO`
- **WHEN** o usuário arquiva novamente
- **THEN** recebe 409 com `title` "Objetivo já arquivado"
- **AND** `arquivadoEm` permanece o original

### Requirement: Reabrir objetivo
O sistema MUST reabrir um objetivo `CONCLUIDO` ou `ARQUIVADO`, mudando sempre para `ATIVO` e limpando `concluidoEm` e `arquivadoEm`. Reabrir um objetivo `ATIVO` MUST responder 409 com `title` "Objetivo já ativo".

#### Scenario: Reabrir objetivo concluído
- **GIVEN** um objetivo `CONCLUIDO`
- **WHEN** o usuário reabre
- **THEN** recebe 200 com `status` `ATIVO` e `concluidoEm` nulo

#### Scenario: Reabrir objetivo concluído e depois arquivado
- **GIVEN** um objetivo `ARQUIVADO` com `concluidoEm` e `arquivadoEm` definidos
- **WHEN** o usuário reabre
- **THEN** recebe 200 com `status` `ATIVO`, `concluidoEm` e `arquivadoEm` nulos

#### Scenario: Reabrir objetivo ativo
- **GIVEN** um objetivo `ATIVO`
- **WHEN** o usuário reabre
- **THEN** recebe 409 com `title` "Objetivo já ativo"

### Requirement: Listagem paginada por status
O sistema MUST listar objetivos do usuário em formato paginado (`content` + `page`), filtrando por um `status` informado e usando `ATIVO` quando ausente. Sem ordenação informada pelo cliente, a ordem MUST ser: `ATIVO` por `prazo` crescente com objetivos sem prazo no fim e, em empate, criação mais antiga primeiro; `CONCLUIDO` por `concluidoEm` decrescente; `ARQUIVADO` por `arquivadoEm` decrescente.

#### Scenario: Padrão lista só ativos
- **GIVEN** objetivos do usuário em `ATIVO`, `CONCLUIDO` e `ARQUIVADO`
- **WHEN** lista sem informar `status`
- **THEN** recebe apenas os objetivos `ATIVO`

#### Scenario: Ativos ordenados por prazo com sem prazo no fim
- **GIVEN** objetivos ativos com prazos 31/12, 20/11 e um sem prazo
- **WHEN** lista sem ordenação
- **THEN** recebe na ordem: 20/11, 31/12, sem prazo

#### Scenario: Concluídos mais recentes primeiro
- **GIVEN** dois objetivos concluídos em dias diferentes
- **WHEN** lista com `status` `CONCLUIDO`
- **THEN** o concluído mais recentemente vem primeiro

#### Scenario: Lista vazia
- **GIVEN** o usuário não tem objetivos arquivados
- **WHEN** lista com `status` `ARQUIVADO`
- **THEN** recebe 200 com `content` vazio e `page.totalElements` 0

#### Scenario: Status inválido
- **WHEN** lista com `status` `PAUSADO`
- **THEN** recebe 400 com `title` "Parâmetro inválido"

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
