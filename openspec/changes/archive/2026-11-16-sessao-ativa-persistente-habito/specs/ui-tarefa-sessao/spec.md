# Spec Delta

## MODIFIED Requirements

### Requirement: Barra global de sessão ativa
Enquanto existir execução `EM_ANDAMENTO` do usuário (tarefa ou hábito, de plano aprovado ou avulsa), o AppShell MUST exibir uma barra de sessão com título, tempo restante (ou excedido), e ações Concluir e Abortar, visível em qualquer rota autenticada da app. A barra MUST ser reconstruída a partir do servidor ao carregar a app, inclusive após recarregar a página, sem depender de estado guardado só em memória.

#### Scenario: Iniciar na lista e navegar
- **GIVEN** o usuário iniciou uma sessão pela lista de tarefas
- **WHEN** navega para outra rota autenticada
- **THEN** a barra de sessão permanece visível com Concluir e Abortar

#### Scenario: Sem sessão em andamento
- **GIVEN** não há execução `EM_ANDAMENTO`
- **WHEN** o usuário navega pela app
- **THEN** a barra de sessão não é exibida

#### Scenario: Recarregar com sessão avulsa de hábito
- **GIVEN** o usuário iniciou um hábito fora do plano aprovado
- **WHEN** recarrega a página
- **THEN** a barra de sessão reaparece com o título do hábito, o tempo desde o `iniciadoEm` do servidor e as ações Concluir e Abortar

#### Scenario: Recarregar com sessão avulsa de tarefa
- **GIVEN** o usuário iniciou uma sessão de tarefa pela lista `/tarefas` (fora do plano aprovado)
- **WHEN** recarrega a página
- **THEN** a barra de sessão reaparece com o título da tarefa

#### Scenario: Sessão que atravessa a meia-noite
- **GIVEN** uma sessão iniciada às 23:50 BRT e ainda em andamento
- **WHEN** o usuário recarrega a página às 00:10 BRT
- **THEN** a barra de sessão continua visível com essa sessão
