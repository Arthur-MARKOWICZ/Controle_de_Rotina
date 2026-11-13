# Spec Delta

## ADDED Requirements

### Requirement: Filtrar listagem por tarefas bloqueadas
`GET /tarefas` MUST aceitar o parâmetro opcional `bloqueada`. Com `bloqueada=true`, MUST devolver só tarefas do usuário bloqueadas (espera irresolvida própria, pai bloqueado ou projeto origem bloqueado); com `false`, só as não bloqueadas; ausente, o comportamento atual. O filtro MUST combinar com os demais e respeitar a paginação.

#### Scenario: Só bloqueadas
- **GIVEN** o usuário com uma tarefa A bloqueada por espera e uma tarefa B livre
- **WHEN** chama `GET /tarefas?bloqueada=true`
- **THEN** recebe 200 com A e sem B
- **AND** A vem com `bloqueada: true` e suas esperas

#### Scenario: Bloqueio herdado entra no filtro
- **GIVEN** uma tarefa sem espera própria cujo projeto espera outra tarefa não finalizada
- **WHEN** chama `GET /tarefas?bloqueada=true`
- **THEN** a tarefa aparece no resultado

#### Scenario: Espera resolvida sai do filtro
- **GIVEN** uma tarefa A que esperava B, e B foi finalizada
- **WHEN** chama `GET /tarefas?bloqueada=true`
- **THEN** A não aparece

#### Scenario: Não vaza tarefas de outro usuário
- **GIVEN** outro usuário com tarefas bloqueadas
- **WHEN** o usuário chama `GET /tarefas?bloqueada=true`
- **THEN** nenhuma tarefa do outro usuário aparece

#### Scenario: Paginação consistente
- **GIVEN** 25 tarefas bloqueadas e 10 livres
- **WHEN** chama `GET /tarefas?bloqueada=true&size=20`
- **THEN** a página tem 20 tarefas bloqueadas e o total informado é 25

#### Scenario: Valor inválido
- **WHEN** chama `GET /tarefas?bloqueada=talvez`
- **THEN** recebe 400 com ProblemDetail indicando parâmetro inválido
