# Spec Delta

## ADDED Requirements

### Requirement: Recusar sessão em tarefa bloqueada
O sistema MUST recusar `POST /tarefas/{id}/iniciar` e `POST /tarefas/{id}/concluir-direto` quando a tarefa estiver bloqueada por espera própria, pai bloqueado ou projeto origem bloqueado. Folha não bloqueada MUST continuar o fluxo de sessão existente.

#### Scenario: Iniciar em tarefa que espera
- **GIVEN** uma folha com restante e espera irresolvida
- **WHEN** solicita iniciar sessão nessa tarefa
- **THEN** o sistema rejeita a operação como inválida ou em conflito
- **AND** nenhum bloco avulso é criado

#### Scenario: Concluir direto em tarefa de projeto bloqueado
- **GIVEN** uma folha cujo projeto está bloqueado como origem
- **WHEN** solicita concluir-direto
- **THEN** o sistema rejeita a operação
- **AND** o status da tarefa não muda por essa chamada

#### Scenario: Folha desbloqueada segue o fluxo atual
- **GIVEN** uma folha sem espera irresolvida, pai não bloqueado e projeto não bloqueado
- **WHEN** inicia ou conclui direto a sessão
- **THEN** a sessão segue as regras já especificadas de bloco, restante e XP
