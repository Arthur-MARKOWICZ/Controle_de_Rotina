# Spec Delta

## Purpose

Define a aba Projetos do app Android: listar, criar, editar, arquivar e reativar projetos e ver as tarefas de cada um, usando os contratos atuais de `/projetos` e `/tarefas`.

## ADDED Requirements

### Requirement: Lista de projetos
A aba Projetos MUST listar os projetos `ATIVO` via `GET /projetos?status=ATIVO`, na ordem que a API devolve, com nome e indicação de bloqueado. Um filtro MUST permitir trocar para os `ARQUIVADO`.

#### Scenario: Abrir projetos
- **GIVEN** o usuário tem projetos ativos
- **WHEN** abre a aba Projetos
- **THEN** cada projeto ativo aparece com o nome

#### Scenario: Ver arquivados
- **WHEN** o usuário troca o filtro para Arquivados
- **THEN** a lista mostra só os projetos `ARQUIVADO`

#### Scenario: Sem projetos
- **GIVEN** a API devolve lista vazia
- **WHEN** o usuário abre a aba Projetos
- **THEN** aparece um estado vazio com a ação Novo projeto

### Requirement: Criar projeto
O app MUST permitir criar projeto com nome obrigatório (até 80 caracteres) e descrição opcional (até 500), via `POST /projetos`. Erros de Problem Details MUST aparecer junto ao campo.

#### Scenario: Criar com nome
- **WHEN** o usuário informa o nome e confirma
- **THEN** o projeto é criado e aparece na lista

#### Scenario: Nome vazio
- **WHEN** o usuário confirma sem nome
- **THEN** o app não envia a requisição e marca o campo nome

### Requirement: Editar projeto
Tocar num projeto MUST abrir o detalhe, onde nome e descrição podem ser editados via `PATCH /projetos/{id}`, enviando só os campos alterados.

#### Scenario: Renomear projeto
- **GIVEN** o detalhe de um projeto ativo aberto
- **WHEN** o usuário muda o nome e salva
- **THEN** o novo nome aparece no detalhe e na lista

### Requirement: Arquivar e reativar
O detalhe de um projeto `ATIVO` MUST oferecer Arquivar (`POST /projetos/{id}/arquivar`) com confirmação. O detalhe de um `ARQUIVADO` MUST oferecer Reativar (`POST /projetos/{id}/reativar`). Recusas da API MUST aparecer como mensagem do Problem Details.

#### Scenario: Arquivar
- **GIVEN** um projeto ativo
- **WHEN** o usuário toca em Arquivar e confirma
- **THEN** o projeto sai da lista de ativos e aparece em Arquivados

#### Scenario: Reativar
- **GIVEN** um projeto arquivado
- **WHEN** o usuário toca em Reativar
- **THEN** o projeto volta para a lista de ativos

### Requirement: Tarefas do projeto
O detalhe do projeto MUST listar as tarefas abertas dele via `GET /tarefas?projetoId=`, com as mesmas ações de sessão e edição da aba Tarefas.

#### Scenario: Ver tarefas do projeto
- **GIVEN** um projeto com duas tarefas abertas
- **WHEN** o usuário abre o detalhe
- **THEN** as duas tarefas aparecem abaixo dos dados do projeto

### Requirement: Escolher projeto na tarefa
Criar e editar tarefa MUST oferecer um seletor de projeto com os projetos `ATIVO` e a opção Sem projeto. Na edição, escolher Sem projeto MUST enviar `projetoId: null`.

#### Scenario: Vincular tarefa a projeto
- **GIVEN** o formulário de tarefa aberto
- **WHEN** o usuário escolhe o projeto "Casa" e salva
- **THEN** o card da tarefa mostra "Casa"

#### Scenario: Remover projeto da tarefa
- **GIVEN** uma tarefa vinculada a um projeto
- **WHEN** o usuário escolhe Sem projeto e salva
- **THEN** o app envia `projetoId: null` e o card deixa de mostrar o projeto
