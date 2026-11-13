# Spec Delta

## Purpose

Define a aba Plano do app Android: ver, gerar e aprovar o plano do dia e executar os blocos (iniciar, concluir, abortar) com feedback de XP, reusando os contratos atuais de `/planos` e `/execucoes`.

## ADDED Requirements

### Requirement: Plano do dia
A aba Plano MUST buscar o plano da data local do aparelho (`GET /planos?data=`) e mostrar status, mensagem do planner e blocos em ordem de horário, com título, duração e estado de execução. Itens não alocados MUST aparecer em uma seção separada.

#### Scenario: Plano aprovado existente
- **GIVEN** existe plano `APROVADO` para hoje
- **WHEN** o usuário abre a aba Plano
- **THEN** os blocos aparecem em ordem com título, horário/duração e estado

#### Scenario: Itens não alocados
- **GIVEN** o plano retorna `itensNaoAlocados` não vazio
- **WHEN** a aba Plano é exibida
- **THEN** há uma seção "Não coube hoje" listando esses itens

### Requirement: Gerar plano quando não existe
Se não houver plano para hoje (404), a aba Plano MUST mostrar um estado vazio com a ação Gerar plano, que chama `POST /planos/gerar?data=` e mostra o rascunho resultante.

#### Scenario: Dia sem plano
- **GIVEN** `GET /planos?data=hoje` responde 404
- **WHEN** o usuário toca em Gerar plano
- **THEN** o app chama `POST /planos/gerar` e mostra o plano `RASCUNHO` gerado

### Requirement: Aprovar rascunho
Com plano `RASCUNHO`, a aba Plano MUST oferecer Aprovar, que chama `POST /planos/{id}/aprovar`. Enquanto o plano não estiver aprovado, os blocos MUST NÃO oferecer ações de execução. O ajuste fino do rascunho MUST NÃO ser oferecido no app neste corte.

#### Scenario: Aprovar o rascunho
- **GIVEN** o plano de hoje está `RASCUNHO`
- **WHEN** o usuário toca em Aprovar
- **THEN** o plano passa a `APROVADO` na tela e os blocos ganham as ações de execução

#### Scenario: Conflito no rascunho
- **GIVEN** o rascunho tem `temConflito` verdadeiro
- **WHEN** a aba Plano é exibida
- **THEN** a mensagem de conflito do planner aparece acima dos blocos

### Requirement: Executar bloco do plano aprovado
Em plano `APROVADO`, um bloco `NAO_INICIADO` ou `ABORTADO` MUST oferecer Iniciar (`POST /execucoes/iniciar`). Um bloco `EM_ANDAMENTO` MUST oferecer Concluir (`POST /execucoes/{id}/concluir`) e Abortar (`POST /execucoes/{id}/abortar`). Um bloco `CONCLUIDO` MUST NÃO oferecer ações.

#### Scenario: Iniciar bloco
- **GIVEN** um bloco `NAO_INICIADO` no plano aprovado
- **WHEN** o usuário toca em Iniciar
- **THEN** o bloco passa a `EM_ANDAMENTO` e a barra de sessão ativa aparece

#### Scenario: Abortar bloco
- **GIVEN** um bloco `EM_ANDAMENTO`
- **WHEN** o usuário toca em Abortar e confirma
- **THEN** o bloco volta a oferecer Iniciar

### Requirement: Barra de sessão ativa
Enquanto existir execução `EM_ANDAMENTO` do usuário no dia (tarefa ou hábito), o app MUST mostrar em todas as telas autenticadas uma barra com título, tempo restante ou excedido, e as ações Concluir e Abortar. Ao abrir o app e ao voltar ao primeiro plano, MUST recuperar essa execução da API (`GET /execucoes` e `GET /habitos/do-dia`). A sessão avulsa de tarefa, que nenhuma listagem expõe, MUST ser recuperada de um resumo local do mesmo dia.

#### Scenario: Navegar com sessão em andamento
- **GIVEN** uma execução está `EM_ANDAMENTO`
- **WHEN** o usuário vai de Plano para Tarefas
- **THEN** a barra de sessão continua visível com Concluir e Abortar

#### Scenario: Reabrir o app no meio de uma sessão
- **GIVEN** o app foi fechado com uma execução `EM_ANDAMENTO`
- **WHEN** o usuário abre o app de novo
- **THEN** a barra de sessão aparece com o tempo calculado a partir do início registrado na API

### Requirement: Feedback de XP na conclusão
Ao concluir uma execução, o app MUST mostrar confirmação e, quando `xp.ganhoNesteRequest` for maior que zero, o XP ganho.

#### Scenario: Concluir com XP
- **WHEN** a conclusão retorna `ganhoNesteRequest` igual a 20
- **THEN** o usuário vê feedback de conclusão com "+20 XP"

### Requirement: Atualizar a aba Plano
A aba Plano MUST permitir puxar para atualizar e MUST recarregar o plano e as execuções ao voltar ao primeiro plano depois de ficar em segundo plano.

#### Scenario: Puxar para atualizar
- **WHEN** o usuário puxa a lista da aba Plano para baixo
- **THEN** o plano e as execuções do dia são buscados de novo
