# Spec Delta

## Purpose

Mostra como um objetivo evoluiu no tempo: os minutos concluídos acumulados por dia, na mesma base do progresso, para o usuário ver o caminho até o percentual atual.

## ADDED Requirements

### Requirement: Evolução do objetivo por dia
`GET /objetivos/{id}/evolucao` MUST devolver `antesDoHistorico` (inteiro ≥ 0) e `pontos`, uma lista em ordem crescente de `data` (data civil em `America/Sao_Paulo`) com `minutosNoDia` (> 0) e `minutosAcumulados`. O acumulado MUST partir de `antesDoHistorico` e somar `minutosNoDia` dia a dia. Dias sem minutos MUST NOT aparecer. A resposta MUST valer para objetivo em qualquer status.

#### Scenario: Evolução com dois dias de trabalho
- **GIVEN** um objetivo com uma tarefa raiz de 120 min, não `FINALIZADA`, com execuções `CONCLUIDO` de 30 min em 03/11 e 45 min em 05/11 (BRT)
- **WHEN** o dono pede a evolução
- **THEN** recebe 200 com `antesDoHistorico` 0
- **AND** `pontos` = [{03/11, 30, 30}, {05/11, 45, 75}]

#### Scenario: Objetivo encerrado continua respondendo
- **GIVEN** um objetivo `ARQUIVADO` com itens que somam minutos
- **WHEN** o dono pede a evolução
- **THEN** recebe 200 com os pontos

#### Scenario: Objetivo sem nada que some tempo
- **GIVEN** um objetivo cujo `progresso` é nulo
- **WHEN** o dono pede a evolução
- **THEN** recebe 200 com `antesDoHistorico` 0 e `pontos` vazio

### Requirement: Evolução só do próprio objetivo
Objetivo inexistente ou de outro usuário MUST responder 404 com `title` "Objetivo não encontrado", sem revelar se o objetivo existe.

#### Scenario: Objetivo de outro usuário
- **GIVEN** um objetivo de outro usuário
- **WHEN** o usuário pede a evolução dele
- **THEN** recebe 404 com `title` "Objetivo não encontrado"

#### Scenario: Objetivo inexistente
- **WHEN** o usuário pede a evolução de um id que não existe
- **THEN** recebe 404 com `title` "Objetivo não encontrado"

#### Scenario: Sem autenticação
- **WHEN** a evolução é pedida sem token válido
- **THEN** recebe 401

### Requirement: Último acumulado igual ao progresso
O último `minutosAcumulados` (ou `antesDoHistorico`, quando não há pontos) MUST ser igual a `progresso.minutosConcluidos` do mesmo objetivo no mesmo instante. A evolução MUST usar o mesmo conjunto de itens e as mesmas regras de contagem do progresso: tarefa `CANCELADA` ou com soft delete, hábito não `ATIVO` e item desvinculado MUST NOT somar em nenhum dia.

#### Scenario: Fecha com o progresso
- **GIVEN** um objetivo com tarefas parciais, tarefas finalizadas e hábitos, cujo `progresso.minutosConcluidos` é 410
- **WHEN** o dono pede a evolução
- **THEN** o último `minutosAcumulados` é 410

#### Scenario: Hábito pausado some da curva inteira
- **GIVEN** um objetivo com um hábito que somou 60 min na semana passada
- **WHEN** o hábito é pausado e o dono pede a evolução
- **THEN** nenhum ponto inclui os minutos desse hábito

#### Scenario: Tarefa desvinculada some da curva
- **GIVEN** uma tarefa com execuções em 03/11 ligada ao objetivo
- **WHEN** o vínculo é removido e o dono pede a evolução
- **THEN** não há ponto em 03/11 vindo dessa tarefa

### Requirement: Contribuição da tarefa na evolução
Cada tarefa raiz que conta no progresso MUST somar, no dia de cada execução `CONCLUIDO` dela e dos filhos não deletados, os minutos da execução. Se a tarefa está `FINALIZADA`, no dia da finalização MUST entrar o saldo entre a duração e o já somado. O acumulado da tarefa MUST nunca passar da duração; o que exceder MUST ser descartado a partir do dia em que excede.

#### Scenario: Finalizada com saldo no dia da finalização
- **GIVEN** uma tarefa raiz de 120 min com 30 min de execução em 03/11 e finalizada à mão em 06/11
- **WHEN** o dono pede a evolução
- **THEN** a tarefa soma 30 em 03/11 e 90 em 06/11

#### Scenario: Execuções acima da duração
- **GIVEN** uma tarefa raiz de 30 min, não `FINALIZADA`, com execuções de 20 min em 03/11 e 25 min em 04/11
- **WHEN** o dono pede a evolução
- **THEN** a tarefa soma 20 em 03/11 e 10 em 04/11

#### Scenario: Execução de subtarefa conta na raiz
- **GIVEN** uma raiz de 60 min com um filho que teve 30 min de execução em 04/11
- **WHEN** o dono pede a evolução
- **THEN** 30 min aparecem em 04/11

#### Scenario: Finalizada sem saldo
- **GIVEN** uma tarefa de 60 min finalizada pela execução que completou 60 min em 05/11
- **WHEN** o dono pede a evolução
- **THEN** a tarefa soma 60 em 05/11 e nada a mais

### Requirement: Data de finalização da tarefa
O sistema MUST registrar o instante em que uma tarefa passa a `FINALIZADA`, por qualquer caminho (edição de status, execução que completa a duração, finalização em cascata entre pai e filhos), e MUST apagá-lo quando a tarefa sai de `FINALIZADA`. Finalizar de novo MUST registrar o novo instante.

#### Scenario: Reabrir tira a tarefa da curva
- **GIVEN** uma tarefa de 60 min sem execuções, finalizada em 05/11, ligada ao objetivo
- **WHEN** a tarefa volta a `PENDENTE` e o dono pede a evolução
- **THEN** não há ponto em 05/11 vindo dessa tarefa
- **AND** o progresso deixa de contar os 60 min

#### Scenario: Finalizar de novo move o saldo
- **GIVEN** uma tarefa finalizada em 05/11, reaberta e finalizada de novo em 09/11
- **WHEN** o dono pede a evolução
- **THEN** o saldo da tarefa aparece em 09/11, não em 05/11

#### Scenario: Pai finalizado em cascata
- **GIVEN** uma raiz de 60 min com dois filhos e nenhuma execução
- **WHEN** o último filho é finalizado em 07/11 e a raiz é finalizada em cascata
- **THEN** a evolução soma o saldo da raiz em 07/11

### Requirement: Tarefas finalizadas antes do histórico
Tarefa `FINALIZADA` sem instante de finalização registrado MUST somar o saldo entre a duração e as execuções já posicionadas em `antesDoHistorico`, e não num dia. Tarefas finalizadas antes desta mudança MUST receber como instante de finalização o do evento de XP de tarefa finalizada, quando ele existir; sem o evento, MUST ficar sem instante.

#### Scenario: Antiga com evento de XP
- **GIVEN** uma tarefa finalizada antes da mudança, com evento de XP de finalização em 20/10
- **WHEN** o dono pede a evolução
- **THEN** o saldo da tarefa aparece em 20/10

#### Scenario: Antiga finalizada à mão
- **GIVEN** uma tarefa de 90 min finalizada à mão antes da mudança, sem evento de XP, com 30 min de execução em 18/10
- **WHEN** o dono pede a evolução
- **THEN** 30 min aparecem em 18/10 e 60 min em `antesDoHistorico`

#### Scenario: Só tarefas antigas sem data
- **GIVEN** um objetivo cujas únicas tarefas foram finalizadas antes da mudança, sem execuções nem evento de XP
- **WHEN** o dono pede a evolução
- **THEN** `pontos` vem vazio e `antesDoHistorico` é igual a `progresso.minutosConcluidos`

### Requirement: Contribuição do hábito na evolução
Cada hábito que conta no progresso MUST somar os minutos dedicados das execuções `CONCLUIDO` no dia do plano delas, só nos dias dentro da janela do hábito no progresso. O acumulado do hábito MUST nunca passar do total dele no progresso; o que exceder MUST ser descartado a partir do dia em que excede.

#### Scenario: Execuções dentro da janela
- **GIVEN** um hábito de 30 min ligado em 02/11, com execuções de 30 min nos planos de 02/11 e 04/11
- **WHEN** o dono pede a evolução
- **THEN** o hábito soma 30 em 02/11 e 30 em 04/11

#### Scenario: Execução antes do vínculo fica fora
- **GIVEN** um hábito ligado em 05/11 com uma execução no plano de 03/11
- **WHEN** o dono pede a evolução
- **THEN** não há ponto em 03/11 vindo desse hábito

#### Scenario: Excesso acima do total do hábito
- **GIVEN** um hábito cujo total no progresso é 60 min, com execuções de 40 min em 03/11 e 40 min em 05/11
- **WHEN** o dono pede a evolução
- **THEN** o hábito soma 40 em 03/11 e 20 em 05/11

### Requirement: Dias da evolução em BRT
O dia de cada contribuição de tarefa MUST ser a data civil em `America/Sao_Paulo` do instante de conclusão da execução (ou do instante de finalização, para o saldo), e não a data UTC.

#### Scenario: Virada de mês em BRT
- **GIVEN** uma execução de tarefa concluída em 2026-12-01T02:30Z (30/11 23:30 em BRT)
- **WHEN** o dono pede a evolução
- **THEN** os minutos aparecem em 30/11, não em 01/12

#### Scenario: Finalização perto da meia-noite
- **GIVEN** uma tarefa finalizada em 2026-11-01T02:59Z (31/10 23:59 em BRT)
- **WHEN** o dono pede a evolução
- **THEN** o saldo aparece em 31/10
