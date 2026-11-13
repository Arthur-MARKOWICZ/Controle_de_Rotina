# calibracao-estimativa Specification

## Purpose

Mostra ao usuário quanto tempo ele realmente leva em relação ao que estimou, a partir das execuções de blocos já gravadas, para que ele calibre as próximas estimativas sem mudar o motor de planejamento.

## Requirements

### Requirement: Endpoint de calibração
O sistema MUST expor `GET /historico/calibracao` autenticado, devolvendo a janela usada, o mínimo de amostras e os recortes geral, por tag e por projeto. Só execuções do usuário autenticado MUST entrar no cálculo. Sem token válido MUST responder 401.

#### Scenario: Usuário com execuções suficientes
- **GIVEN** o usuário tem 6 blocos de tarefa concluídos com timer nos últimos 90 dias
- **WHEN** chama `GET /historico/calibracao`
- **THEN** recebe 200 com `geral.amostras = 6`, `geral.suficiente = true` e `geral.razao` numérica

#### Scenario: Execuções de outro usuário não entram
- **GIVEN** o usuário B tem 10 amostras válidas e o usuário A não tem nenhuma
- **WHEN** A chama `GET /historico/calibracao`
- **THEN** recebe `geral.amostras = 0` e `geral.suficiente = false`

#### Scenario: Sem autenticação
- **WHEN** a chamada é feita sem token
- **THEN** o sistema responde 401

### Requirement: Janela fixa de 90 dias em BRT
O cálculo MUST considerar só execuções cujo `concluidoEm` esteja entre o início do dia BRT de 89 dias atrás (inclusivo) e o fim do dia BRT de hoje (exclusivo do dia seguinte). A resposta MUST informar `janela.inicio` e `janela.fim` como datas locais BRT.

#### Scenario: Execução no limite da janela
- **GIVEN** hoje é 2026-11-13 BRT
- **WHEN** existe uma amostra concluída em 2026-08-16 00:00 BRT e outra em 2026-08-15 23:59 BRT
- **THEN** só a primeira entra e `janela.inicio = 2026-08-16`, `janela.fim = 2026-11-13`

### Requirement: Seleção de amostras válidas
Uma amostra MUST ser uma execução `CONCLUIDO` de bloco ligado a tarefa, com `iniciadoEm` e `concluidoEm` preenchidos e `concluidoEm` posterior a `iniciadoEm`. Execuções abortadas, em andamento ou não iniciadas, blocos de hábito e conclusões diretas sem timer MUST ser descartadas.

#### Scenario: Abortadas e em andamento não contam
- **GIVEN** 5 amostras válidas, 2 execuções `ABORTADO` e 1 `EM_ANDAMENTO`
- **WHEN** o usuário consulta a calibração
- **THEN** `geral.amostras = 5`

#### Scenario: Conclusão direta e hábito não contam
- **GIVEN** um bloco de tarefa concluído direto (início igual ao fim) e um bloco de hábito concluído com timer
- **WHEN** o usuário consulta a calibração
- **THEN** nenhum dos dois entra nas amostras

### Requirement: Descarte de outliers
Para cada amostra, razão individual = minutos reais ÷ minutos planejados do bloco. Amostras com razão individual abaixo de 0,25 ou acima de 4,0 MUST ser descartadas antes de qualquer agregação e MUST NÃO contar em `amostras`.

#### Scenario: Sessão esquecida aberta
- **GIVEN** 5 amostras válidas e um bloco de 30 min concluído 5 horas após iniciar
- **WHEN** o usuário consulta a calibração
- **THEN** `geral.amostras = 5` e a razão não é afetada pela sessão esquecida

#### Scenario: Clique acidental
- **GIVEN** um bloco de 60 min iniciado e concluído 2 minutos depois
- **WHEN** o usuário consulta a calibração
- **THEN** a amostra é descartada

#### Scenario: Limites inclusivos
- **GIVEN** um bloco de 60 min concluído com exatamente 15 min reais e outro com exatamente 240 min reais
- **WHEN** o usuário consulta a calibração
- **THEN** as duas amostras entram

### Requirement: Cálculo da razão e do desvio
Para cada recorte, `razao` MUST ser a soma dos minutos reais dividida pela soma dos minutos planejados das amostras do recorte, arredondada a 2 casas (half-up). `desvioMedioMinutos` MUST ser a média de |real − planejado| por amostra, arredondada a 1 casa.

#### Scenario: Razão ponderada
- **GIVEN** amostras (planejado → real) 30→45, 60→60, 60→90, 30→30, 120→150
- **WHEN** o usuário consulta a calibração
- **THEN** `geral.razao = 1.25` (375 ÷ 300) e `geral.desvioMedioMinutos = 15.0`

### Requirement: Mínimo de amostras
Um recorte com menos de 5 amostras MUST vir com `suficiente = false`, `razao = null` e `desvioMedioMinutos = null`, mantendo `amostras` com a contagem real. Com 5 ou mais MUST vir com `suficiente = true` e números preenchidos.

#### Scenario: Quatro amostras
- **GIVEN** 4 amostras válidas
- **WHEN** o usuário consulta a calibração
- **THEN** `geral` vem com `amostras = 4`, `suficiente = false` e `razao = null`

#### Scenario: Sem nenhuma execução
- **GIVEN** um usuário recém-criado
- **WHEN** consulta a calibração
- **THEN** recebe 200 com `geral.amostras = 0`, `porTag = []` e `porProjeto = []`

### Requirement: Recorte por tag
`porTag` MUST conter uma entrada por tag atualmente vinculada a ao menos uma tarefa com amostra, com `tagId`, `nome`, `cor` e as métricas do recorte. Uma tarefa com várias tags MUST contar a amostra em cada uma. Tags apagadas MUST NÃO aparecer.

#### Scenario: Tarefa com duas tags
- **GIVEN** 5 amostras de uma tarefa com as tags `faculdade` e `leitura`
- **WHEN** o usuário consulta a calibração
- **THEN** `porTag` tem as duas tags, cada uma com `amostras = 5`

### Requirement: Recorte por projeto
`porProjeto` MUST conter uma entrada por projeto de tarefa com amostra, com `projetoId`, `nome` e as métricas. Tarefas sem projeto MUST entrar só no geral. Projetos arquivados MUST continuar aparecendo.

#### Scenario: Tarefa sem projeto
- **GIVEN** 5 amostras de tarefas sem projeto
- **WHEN** o usuário consulta a calibração
- **THEN** `geral.amostras = 5` e `porProjeto = []`

### Requirement: Ordenação dos recortes
`porTag` e `porProjeto` MUST vir com os recortes suficientes primeiro, ordenados pela distância |razao − 1| decrescente, e depois os insuficientes por `amostras` decrescente; empate por nome.

#### Scenario: Recorte mais desviado primeiro
- **GIVEN** tag A com razão 1,10 e tag B com razão 0,60, ambas suficientes, e tag C com 3 amostras
- **WHEN** o usuário consulta a calibração
- **THEN** a ordem de `porTag` é B, A, C
