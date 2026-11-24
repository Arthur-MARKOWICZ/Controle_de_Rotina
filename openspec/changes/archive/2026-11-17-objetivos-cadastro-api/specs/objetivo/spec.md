# Spec Delta

## Purpose

Permite ao usuário registrar o que quer alcançar (objetivos com título, descrição e prazo opcionais) e controlar explicitamente seu ciclo de vida: ativo, concluído, arquivado e reaberto.

## ADDED Requirements

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
