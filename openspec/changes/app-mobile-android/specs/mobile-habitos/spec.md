# Spec Delta

## Purpose

Define a tela Hábitos do app Android: ver os hábitos do dia com streak e cumprimento, e iniciar ou concluir direto uma sessão avulsa de hábito.

## ADDED Requirements

### Requirement: Hábitos do dia
A tela Hábitos MUST listar os hábitos do dia via `GET /habitos/do-dia` com título, duração, horário preferencial, streak atual e indicação de cumprido.

#### Scenario: Abrir hábitos do dia
- **GIVEN** o usuário tem hábitos ativos para hoje
- **WHEN** abre a tela Hábitos
- **THEN** cada hábito aparece com título, duração, streak e se já foi cumprido

#### Scenario: Nenhum hábito hoje
- **GIVEN** a API devolve lista vazia
- **WHEN** o usuário abre a tela Hábitos
- **THEN** aparece um estado vazio dizendo que não há hábitos para hoje

### Requirement: Iniciar e concluir hábito
Um hábito não cumprido e sem execução `EM_ANDAMENTO` MUST oferecer Iniciar (`POST /habitos/{id}/iniciar`) e Concluir direto (`POST /habitos/{id}/concluir-direto`). Um hábito cumprido MUST NÃO oferecer essas ações. Um hábito em andamento MUST ser concluído ou abortado pela barra de sessão ativa.

#### Scenario: Iniciar hábito
- **GIVEN** um hábito não cumprido e sem sessão em andamento
- **WHEN** o usuário toca em Iniciar
- **THEN** a barra de sessão ativa aparece com o título do hábito

#### Scenario: Concluir hábito direto
- **GIVEN** um hábito não cumprido
- **WHEN** o usuário toca em Concluir
- **THEN** o hábito passa a cumprido, o streak é atualizado e o XP ganho aparece

#### Scenario: Hábito já cumprido
- **GIVEN** um hábito com `cumprido` verdadeiro
- **WHEN** o usuário vê o card
- **THEN** não há ações Iniciar nem Concluir
