# ui-pendencia Specification

## Purpose

Expõe espera e bloqueio na ficha da tarefa e no hub do projeto, com indicação na lista, para o usuário criar, ver e remover a relação sem confundir com o status PENDENTE.

## Requirements

### Requirement: Seção de espera no drawer da tarefa
O drawer de editar tarefa MUST oferecer uma seção para listar esperas daquela origem, adicionar alvo (tarefa ou projeto do usuário) e remover espera. Criar tarefa nova MUST NÃO exigir espera. Copy MUST usar Espera / Bloqueada por, nunca o rótulo do status `PENDENTE`.

#### Scenario: Adicionar espera no drawer
- **GIVEN** o drawer de uma tarefa existente
- **WHEN** o usuário escolhe um alvo e confirma
- **THEN** a espera aparece na seção
- **AND** a ficha indica que a tarefa está bloqueada se o alvo ainda não resolveu

#### Scenario: Remover espera no drawer
- **GIVEN** o drawer com uma espera listada
- **WHEN** o usuário remove essa espera
- **THEN** o vínculo some da seção
- **AND** a indicação de bloqueio some se não restar outra espera irresolvida

#### Scenario: Criar sem espera
- **GIVEN** o drawer de nova tarefa
- **WHEN** o usuário salva só com título
- **THEN** a tarefa nasce sem esperas

### Requirement: Seção de espera no hub do projeto
O hub `/projetos/:id` MUST oferecer a mesma operação de espera com o projeto como origem: listar, adicionar alvo (tarefa ou projeto) e remover. Projeto arquivado MUST NÃO permitir criar espera.

#### Scenario: Projeto espera tarefa pelo hub
- **GIVEN** o usuário no hub de um projeto `ATIVO`
- **WHEN** adiciona uma tarefa como alvo
- **THEN** a espera aparece no hub
- **AND** o hub indica que o projeto está bloqueado enquanto o alvo não resolver

#### Scenario: Hub arquivado só leitura
- **GIVEN** um projeto `ARQUIVADO`
- **WHEN** o usuário abre o hub
- **THEN** não há controle para criar espera

### Requirement: Badge de espera na lista de tarefas
Cards em `/tarefas` MUST indicar quando a tarefa está bloqueada (espera própria, pai bloqueado ou projeto origem bloqueado). A indicação MUST mostrar o alvo de forma resumida (título) ou, se houver vários, que há mais de uma espera.

#### Scenario: Card bloqueado
- **GIVEN** a tarefa A espera B chamada "Revisar API"
- **WHEN** a lista renderiza o card de A
- **THEN** o card mostra que A espera "Revisar API"

#### Scenario: Várias esperas no card
- **GIVEN** a tarefa espera dois alvos
- **WHEN** a lista renderiza o card
- **THEN** o card indica bloqueio com mais de um alvo, sem listar um grafo completo

### Requirement: Sessão ocultada quando bloqueada
A UI MUST NÃO oferecer Iniciar nem Concluir de sessão em tarefa bloqueada, na lista e no card de bloco do Home se esse bloco ainda existir no plano. A ficha MUST permanecer aberta para editar ou remover a espera.

#### Scenario: Lista sem sessão
- **GIVEN** uma folha com restante e espera irresolvida
- **WHEN** o usuário vê o card na lista
- **THEN** Iniciar e Concluir de sessão não são oferecidos
- **AND** o card continua clicável para abrir o drawer

#### Scenario: Home com bloco de tarefa bloqueada
- **GIVEN** o plano aprovado ainda tem um bloco dessa tarefa e a espera passou a irresolvida
- **WHEN** o usuário vê o card do bloco
- **THEN** Iniciar e Concluir desse bloco não são oferecidos
- **AND** o card indica a espera
