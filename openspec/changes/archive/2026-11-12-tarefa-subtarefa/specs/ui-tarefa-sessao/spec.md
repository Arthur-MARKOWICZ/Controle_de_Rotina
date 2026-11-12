# Spec Delta

## MODIFIED Requirements

### Requirement: CTAs na lista de tarefas
Em `/tarefas`, cards de tarefas elegíveis (não deletadas, com restante > 0, status que permita progresso, **sem filhos não deletados**) MUST oferecer Iniciar e Concluir. Card de pai com filhos MUST NÃO oferecer essas ações de sessão.

#### Scenario: Tarefa com restante
- **GIVEN** uma tarefa pendente ou em progresso com restante maior que zero e sem filhos não deletados
- **WHEN** o usuário vê o card na lista
- **THEN** há ações Iniciar e Concluir

#### Scenario: Tarefa sem restante ou finalizada
- **GIVEN** uma tarefa finalizada ou com restante zero
- **WHEN** o usuário vê o card na lista
- **THEN** Iniciar e Concluir de sessão não são oferecidos

#### Scenario: Pai com filhos na lista
- **GIVEN** uma tarefa com filhos não deletados
- **WHEN** o usuário vê o card do pai na lista
- **THEN** Iniciar e Concluir de sessão não são oferecidos
