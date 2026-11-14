# habito-historico Specification

## Purpose

Registrar quanto tempo o usuário dedicou a cada cumprimento de hábito e expor, por hábito e período, o histórico diário com o tempo dedicado, a meta do dia e se a meta foi atingida.

## Requirements

### Requirement: Minutos informados no concluir direto de hábito
`POST /habitos/{id}/concluir-direto` MUST aceitar body JSON opcional com `minutos` (inteiro). Sem body ou sem `minutos`, o sistema MUST registrar a duração do bloco como minutos informados. O valor MUST ficar gravado na execução concluída.

#### Scenario: Concluir direto com minutos
- **GIVEN** um hábito ativo de 30 min agendado para hoje
- **WHEN** o usuário chama `concluir-direto` com `{ "minutos": 20 }`
- **THEN** a resposta é 200 com a execução `CONCLUIDO`
- **AND** o histórico do dia mostra 20 minutos dedicados

#### Scenario: Concluir direto sem body
- **GIVEN** um hábito ativo de 30 min agendado para hoje
- **WHEN** o usuário chama `concluir-direto` sem body
- **THEN** a resposta é 200 e o histórico do dia mostra 30 minutos dedicados

#### Scenario: Minutos fora do limite
- **WHEN** o usuário chama `concluir-direto` com `minutos` igual a 0, negativo ou maior que 1440
- **THEN** a resposta é 400 com `title` "Minutos informados inválidos" e propriedade `minutosMaximo` = 1440
- **AND** nenhuma execução é criada ou alterada e nenhum XP é concedido

#### Scenario: Execução em andamento ignora minutos informados
- **GIVEN** o bloco do hábito já está `EM_ANDAMENTO` pelo timer
- **WHEN** o usuário chama `concluir-direto` com `minutos`
- **THEN** a execução é concluída com o tempo do timer e `minutos` não é gravado

### Requirement: XP do concluir direto de hábito pelos minutos informados
Ao concluir direto um hábito, o XP do bloco MUST ser calculado a partir dos minutos informados com a mesma regra de crédito do timer: crédito cheio quando os minutos chegam a 80% da duração do bloco, senão os próprios minutos, nunca acima da duração do bloco.

#### Scenario: Minutos abaixo de 80%
- **GIVEN** um hábito de 30 min
- **WHEN** o usuário conclui direto com 10 minutos
- **THEN** o XP concedido corresponde a 10 minutos efetivos

#### Scenario: Minutos acima da duração
- **GIVEN** um hábito de 30 min
- **WHEN** o usuário conclui direto com 60 minutos
- **THEN** o XP concedido corresponde a 30 minutos efetivos

#### Scenario: Concluir direto repetido não duplica XP
- **GIVEN** o bloco do hábito já está `CONCLUIDO`
- **WHEN** o usuário chama `concluir-direto` de novo para o mesmo bloco
- **THEN** a resposta mantém o comportamento atual (novo bloco avulso ou 409) e o XP da execução anterior não é concedido de novo

### Requirement: Sequência independe do tempo dedicado
O cálculo de `streakAtual` e `melhorStreak` MUST NOT considerar os minutos dedicados; qualquer execução `CONCLUIDO` no dia agendado continua contando como cumprimento.

#### Scenario: Tempo insuficiente mantém a sequência
- **GIVEN** um hábito de 30 min com sequência 3
- **WHEN** o usuário conclui direto o próximo dia agendado com 5 minutos
- **THEN** a sequência passa a 4

### Requirement: Consulta do histórico do hábito
O sistema MUST expor `GET /habitos/{id}/historico` com `dataDe` e `dataAte` opcionais (padrão: últimos 84 dias terminando hoje em BRT) e retornar um item por data civil com ao menos uma execução `CONCLUIDO` do hábito, em ordem decrescente de data. Dias sem cumprimento MUST NOT aparecer.

#### Scenario: Histórico com cumprimentos
- **GIVEN** um hábito concluído na segunda e na quarta da semana passada
- **WHEN** o usuário consulta o histórico do período
- **THEN** a resposta é 200 com dois itens, quarta antes de segunda

#### Scenario: Hábito sem cumprimentos
- **WHEN** o usuário consulta o histórico de um hábito nunca concluído
- **THEN** a resposta é 200 com lista vazia

#### Scenario: Execuções não concluídas são ignoradas
- **GIVEN** uma execução do hábito `ABORTADO` e outra `EM_ANDAMENTO` no período
- **WHEN** o usuário consulta o histórico
- **THEN** essas datas não aparecem

#### Scenario: Hábito excluído
- **GIVEN** um hábito excluído (soft-delete) com cumprimentos anteriores
- **WHEN** o usuário consulta o histórico
- **THEN** a resposta é 200 com os cumprimentos

#### Scenario: Hábito de outro usuário ou inexistente
- **WHEN** o usuário consulta o histórico de um hábito que não é dele ou não existe
- **THEN** a resposta é 404 com `title` "Hábito não encontrado"

#### Scenario: Intervalo inválido
- **WHEN** o usuário informa `dataAte` antes de `dataDe`, só uma das datas, ou um intervalo maior que 366 dias
- **THEN** a resposta é 400 com `title` "Intervalo inválido"

### Requirement: Tempo dedicado e meta do dia
Cada item do histórico MUST conter `data`, `minutosDedicados`, `metaMinutos`, `suficiente` e `origem`. `minutosDedicados` MUST ser a soma, nas execuções `CONCLUIDO` da data, do tempo do timer (fim − início, sem o crédito de 80%) ou dos minutos informados. `metaMinutos` MUST ser a maior duração de bloco do hábito naquela data, não a duração atual do hábito.

#### Scenario: Tempo pelo timer
- **GIVEN** uma execução iniciada às 10:00 e concluída às 10:25 em um bloco de 30 min
- **WHEN** o usuário consulta o histórico
- **THEN** o item tem `minutosDedicados` 25, `metaMinutos` 30 e `origem` `TIMER`

#### Scenario: Duração do hábito editada depois
- **GIVEN** um cumprimento feito quando o hábito durava 30 min e o hábito editado depois para 45 min
- **WHEN** o usuário consulta o histórico
- **THEN** o item antigo mantém `metaMinutos` 30

#### Scenario: Duas execuções no mesmo dia
- **GIVEN** duas execuções concluídas do hábito na mesma data, uma pelo timer com 15 min e outra direta com 20 min informados
- **WHEN** o usuário consulta o histórico
- **THEN** há um único item com `minutosDedicados` 35 e `origem` `MISTO`

### Requirement: Tempo suficiente
`suficiente` MUST ser verdadeiro somente quando `minutosDedicados` for maior ou igual a `metaMinutos` (100% da meta).

#### Scenario: Faixa entre 80% e 99%
- **GIVEN** um bloco de 30 min concluído pelo timer em 25 min
- **WHEN** o usuário consulta o histórico
- **THEN** o item tem `suficiente` falso, mesmo com XP de crédito cheio

#### Scenario: Meta exata
- **GIVEN** um bloco de 30 min concluído direto com 30 minutos informados
- **WHEN** o usuário consulta o histórico
- **THEN** o item tem `suficiente` verdadeiro
