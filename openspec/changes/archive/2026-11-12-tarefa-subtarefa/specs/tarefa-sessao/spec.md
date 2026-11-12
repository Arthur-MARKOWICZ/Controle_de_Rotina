# Spec Delta

## ADDED Requirements

### Requirement: Recusar sessão em pai com filhos
O sistema MUST recusar `POST /tarefas/{id}/iniciar` e `POST /tarefas/{id}/concluir-direto` quando a tarefa tiver ao menos um filho não deletado. Folhas MUST continuar o fluxo de sessão existente.

#### Scenario: Iniciar no pai rejeitado
- **GIVEN** uma tarefa com filhos não deletados
- **WHEN** solicita iniciar sessão nessa tarefa
- **THEN** o sistema rejeita a operação como inválida ou em conflito
- **AND** nenhum bloco avulso é criado para o pai

#### Scenario: Concluir direto no pai rejeitado
- **GIVEN** uma tarefa com filhos não deletados
- **WHEN** solicita concluir-direto nessa tarefa
- **THEN** o sistema rejeita a operação
- **AND** os filhos não mudam de status por essa chamada

#### Scenario: Folha segue o fluxo atual
- **GIVEN** uma subtarefa sem filhos e com restante maior que zero
- **WHEN** inicia ou conclui direto a sessão
- **THEN** a sessão segue as regras já especificadas de bloco, restante e XP
