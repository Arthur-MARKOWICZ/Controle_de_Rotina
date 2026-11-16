# Spec Delta

## Purpose

Permite que qualquer cliente descubra, a qualquer momento, qual sessão (execução em andamento) o usuário tem aberta, independentemente do dia e do tipo de plano, e que iniciar de novo um hábito já em andamento retome a sessão em vez de falhar.

## ADDED Requirements

### Requirement: Consulta da sessão ativa
O sistema MUST expor `GET /execucoes/ativa` que devolve a execução `EM_ANDAMENTO` do usuário autenticado, em plano `APROVADO`, `HABITO_AVULSO` ou `TAREFA_AVULSA`, sem filtro de data, no mesmo formato de item de `GET /execucoes` (`bloco`, `tarefaResumo`, `habitoResumo`, `execucao`). Sem sessão, MUST responder `204` sem corpo.

#### Scenario: Hábito avulso em andamento
- **GIVEN** o usuário iniciou um hábito fora do plano aprovado (plano `HABITO_AVULSO`)
- **WHEN** chama `GET /execucoes/ativa`
- **THEN** recebe `200` com `execucao.status = EM_ANDAMENTO`, `execucao.iniciadoEm` preenchido e `habitoResumo.id` igual ao hábito
- **AND** `tarefaResumo` é nulo

#### Scenario: Bloco do plano aprovado em andamento
- **GIVEN** um bloco de tarefa do plano `APROVADO` de hoje está `EM_ANDAMENTO`
- **WHEN** o usuário chama `GET /execucoes/ativa`
- **THEN** recebe `200` com o bloco e `tarefaResumo` preenchidos

#### Scenario: Tarefa avulsa em andamento
- **GIVEN** o usuário iniciou uma sessão de tarefa avulsa (plano `TAREFA_AVULSA`)
- **WHEN** chama `GET /execucoes/ativa`
- **THEN** recebe `200` com `tarefaResumo.id` igual à tarefa

#### Scenario: Nenhuma sessão em andamento
- **GIVEN** todas as execuções do usuário estão `CONCLUIDO`, `ABORTADO` ou não existem
- **WHEN** chama `GET /execucoes/ativa`
- **THEN** recebe `204` sem corpo

#### Scenario: Sessão iniciada no dia anterior
- **GIVEN** o usuário iniciou um hábito às 23:50 BRT de ontem e não concluiu
- **WHEN** chama `GET /execucoes/ativa` às 00:10 BRT de hoje
- **THEN** recebe `200` com essa execução

#### Scenario: Sessão de outro usuário não aparece
- **GIVEN** apenas outro usuário tem execução `EM_ANDAMENTO`
- **WHEN** o usuário autenticado chama `GET /execucoes/ativa`
- **THEN** recebe `204`

#### Scenario: Sem autenticação
- **WHEN** a chamada é feita sem token válido
- **THEN** recebe `401`

### Requirement: Só sessões que ainda podem ser concluídas ou abortadas
`GET /execucoes/ativa` MUST considerar apenas execuções cujo plano ainda aceita concluir/abortar: planos avulsos (`HABITO_AVULSO`, `TAREFA_AVULSA`) ou o plano `APROVADO` que ainda é o corrente do seu dia. Execuções `EM_ANDAMENTO` em plano substituído MUST ser ignoradas, sem alterar seu estado.

#### Scenario: Plano aprovado substituído por nova versão
- **GIVEN** um bloco do plano aprovado v1 está `EM_ANDAMENTO` e o usuário gerou o rascunho v2 do mesmo dia
- **WHEN** chama `GET /execucoes/ativa`
- **THEN** recebe `204`
- **AND** a execução do v1 continua `EM_ANDAMENTO` no banco

#### Scenario: Sessão avulsa convive com plano substituído
- **GIVEN** uma execução `EM_ANDAMENTO` em plano substituído e outra em plano `HABITO_AVULSO`
- **WHEN** o usuário chama `GET /execucoes/ativa`
- **THEN** recebe a execução do plano `HABITO_AVULSO`

### Requirement: Escolha entre várias sessões em andamento
Quando o usuário tiver mais de uma execução `EM_ANDAMENTO` elegível, `GET /execucoes/ativa` MUST devolver a de `iniciadoEm` mais recente, sem alterar as demais.

#### Scenario: Duas sessões abertas
- **GIVEN** uma execução `EM_ANDAMENTO` iniciada às 09:00 e outra às 10:00
- **WHEN** o usuário chama `GET /execucoes/ativa`
- **THEN** recebe a iniciada às 10:00
- **AND** a execução das 09:00 continua `EM_ANDAMENTO`

### Requirement: Iniciar hábito já em andamento retoma a sessão
`POST /habitos/{id}/iniciar` MUST responder `200` com a execução existente, sem alterar `iniciadoEm`, quando o bloco que seria iniciado (bloco aberto do plano aprovado do dia ou bloco avulso do hábito) já estiver `EM_ANDAMENTO`. `POST /execucoes/iniciar` MUST manter `409` nesse caso.

#### Scenario: Reiniciar hábito avulso em andamento
- **GIVEN** o hábito foi iniciado avulso às 08:00 e está `EM_ANDAMENTO`
- **WHEN** o usuário chama `POST /habitos/{id}/iniciar` às 08:20 para o mesmo dia
- **THEN** recebe `200` com o mesmo `id` de execução e `iniciadoEm` 08:00
- **AND** nenhuma nova execução, bloco ou plano é criado

#### Scenario: Reiniciar hábito em andamento no plano aprovado
- **GIVEN** o bloco do hábito no plano aprovado de hoje está `EM_ANDAMENTO`
- **WHEN** o usuário chama `POST /habitos/{id}/iniciar`
- **THEN** recebe `200` com a execução desse bloco

#### Scenario: Endpoint genérico continua recusando
- **GIVEN** um bloco com execução `EM_ANDAMENTO`
- **WHEN** o usuário chama `POST /execucoes/iniciar` com esse `blocoId`
- **THEN** recebe `409` com o ProblemDetail de execução já em andamento

#### Scenario: Hábito já concluído no dia continua recusando
- **GIVEN** o bloco avulso do hábito hoje está `CONCLUIDO`
- **WHEN** o usuário chama `POST /habitos/{id}/iniciar`
- **THEN** recebe `409` com o ProblemDetail de execução já concluída
