# Spec Delta

## ADDED Requirements

### Requirement: Progresso na leitura de objetivos
`GET /objetivos` e `GET /objetivos/{id}` MUST incluir `progresso`. Com `minutosTotais` maior que zero, o valor MUST ser um objeto com `percentual` (inteiro 0 a 100), `minutosConcluidos` e `minutosTotais`. O percentual MUST ser o piso de `100 * minutosConcluidos / minutosTotais`, no máximo 100. O cálculo MUST ocorrer na leitura, sem persistir o resultado.

#### Scenario: Tarefa finalizada e tarefa pendente
- **GIVEN** um objetivo ligado a uma tarefa raiz `FINALIZADA` de 120 min e a uma pendente de 30 min sem execuções
- **WHEN** o dono busca o objetivo
- **THEN** recebe `progresso` com `minutosConcluidos` 120, `minutosTotais` 150 e `percentual` 80

#### Scenario: Listagem traz progresso de cada item
- **GIVEN** dois objetivos ativos, um com total 0 e outro com 60 de 60
- **WHEN** o dono lista objetivos
- **THEN** o primeiro vem com `progresso` nulo e o segundo com `percentual` 100

#### Scenario: Percentual arredondado para baixo
- **GIVEN** um objetivo com 10 min concluídos de 30
- **WHEN** o dono busca o objetivo
- **THEN** `percentual` é 33, não 34
- **AND** não é 100

#### Scenario: Piso de 100 só no total completo
- **GIVEN** um objetivo com 149 min concluídos de 150
- **WHEN** o dono busca o objetivo
- **THEN** `percentual` é 99

### Requirement: Progresso nulo quando o total é zero
Quando nenhum item soma tempo (`minutosTotais` zero), `progresso` MUST ser `null`. Isso inclui objetivo sem vínculos visíveis, só tarefas `CANCELADA`, só hábitos `PAUSADO` ou com soft delete, e projeto ligado sem tarefas raiz. MUST NÃO devolver 0%.

#### Scenario: Objetivo sem itens
- **GIVEN** um objetivo do usuário sem vínculos
- **WHEN** o dono busca o objetivo
- **THEN** `progresso` é `null`

#### Scenario: Só tarefa cancelada
- **GIVEN** um objetivo ligado só a uma tarefa raiz `CANCELADA` de 60 min
- **WHEN** o dono busca o objetivo
- **THEN** `progresso` é `null`

#### Scenario: Projeto sem tarefas
- **GIVEN** um objetivo ligado só a um projeto sem tarefas raiz
- **WHEN** o dono busca o objetivo
- **THEN** `progresso` é `null`

### Requirement: Contribuição da tarefa no progresso
Cada tarefa raiz do conjunto que não esteja `CANCELADA` nem com soft delete MUST somar `duracaoMinutos` ao total. Se `FINALIZADA`, MUST somar a duração ao concluído; senão MUST somar os minutos canônicos das execuções `CONCLUIDO` da raiz e dos filhos não deletados, limitados à duração da raiz.

#### Scenario: Tarefa parcial não finalizada
- **GIVEN** uma tarefa raiz de 120 min, não `FINALIZADA`, com 60 min de execuções `CONCLUIDO`
- **WHEN** o dono busca o objetivo ligado a ela
- **THEN** essa tarefa contribui 60 no concluído e 120 no total

#### Scenario: Execuções acima da duração
- **GIVEN** uma tarefa raiz de 30 min, não `FINALIZADA`, com 45 min de execuções `CONCLUIDO`
- **WHEN** o dono busca o objetivo
- **THEN** essa tarefa contribui 30 no concluído e 30 no total

#### Scenario: Soft delete some do total
- **GIVEN** duas tarefas raiz ligadas, uma de 60 min ativa e uma de 40 min com soft delete
- **WHEN** o dono busca o objetivo
- **THEN** o total é 60
- **AND** restaurar a segunda a faz voltar a somar

#### Scenario: Deduplicação por herança
- **GIVEN** uma tarefa raiz de 90 min ligada direto e cujo projeto também está ligado ao mesmo objetivo
- **WHEN** o dono busca o objetivo
- **THEN** a tarefa soma 90 uma vez, não duas

#### Scenario: Filhos alimentam a raiz
- **GIVEN** uma tarefa raiz de 60 min (soma de dois filhos de 30) não `FINALIZADA`, com 30 min `CONCLUIDO` num filho
- **WHEN** o dono busca o objetivo
- **THEN** a raiz contribui 30 no concluído e 60 no total

### Requirement: Contribuição do hábito no progresso
Cada hábito `ATIVO` sem soft delete MUST somar `duracaoMinutos` vezes os dias da semana dele na janela. O concluído MUST ser a soma dos minutos dedicados das execuções `CONCLUIDO` na janela — timer (fim − início, sem crédito de 80%) ou minutos informados — limitada ao total daquele hábito.

#### Scenario: Só hábito, um dia esperado cumprido
- **GIVEN** hoje é segunda em BRT, um hábito de 30 min só às segundas, vinculado hoje, com 30 min dedicados hoje
- **WHEN** o dono busca o objetivo
- **THEN** `minutosTotais` é 30, `minutosConcluidos` é 30 e `percentual` é 100

#### Scenario: Extra num dia cobre buraco
- **GIVEN** um hábito de 30 min com 3 dias esperados na janela e 90 min dedicados num único dia
- **WHEN** o dono busca o objetivo
- **THEN** `minutosConcluidos` é 90, `minutosTotais` é 90 e `percentual` é 100

#### Scenario: Misto tarefa e hábito
- **GIVEN** uma tarefa `FINALIZADA` de 120 min e um hábito com total 30 e 0 dedicados
- **WHEN** o dono busca o objetivo
- **THEN** `minutosConcluidos` é 120, `minutosTotais` é 150 e `percentual` é 80

#### Scenario: Soft delete do hábito
- **GIVEN** um hábito ativo ligado, com minutos na janela, depois soft-deletado
- **WHEN** o dono busca o objetivo
- **THEN** o hábito não soma
- **AND** restaurá-lo o faz voltar a somar na mesma janela

### Requirement: Janela do hábito no progresso
A janela MUST começar na data civil `America/Sao_Paulo` do `created_at` do vínculo e terminar no mais cedo entre hoje nessa zona, o `prazo` do objetivo se já passou, e a data civil de `concluidoEm` ou `arquivadoEm` (a mais cedo das que existirem). Hoje MUST entrar no total quando for dia do hábito e cair na janela.

#### Scenario: Hoje entra no total à meia-noite
- **GIVEN** um hábito diário de 30 min vinculado há 3 dias, com 90 min dedicados, e o relógio vira o quarto dia esperado em BRT sem cumprimento novo
- **WHEN** o dono busca o objetivo
- **THEN** `minutosTotais` é 120 e `minutosConcluidos` é 90

#### Scenario: Virada de semana em BRT
- **GIVEN** o instante 2026-11-16T02:30Z (ainda domingo 15/11 23:30 em BRT) e um hábito só de segunda, vinculado na segunda anterior, sem cumprimento
- **WHEN** o dono busca o objetivo
- **THEN** a segunda de hoje ainda não entrou no total
- **AND** em 2026-11-16T03:30Z (segunda 00:30 BRT) o total inclui essa segunda

#### Scenario: Congela no encerramento
- **GIVEN** um objetivo concluído ontem, com hábito diário vinculado antes, e hoje é dia do hábito
- **WHEN** o dono busca o objetivo
- **THEN** o dia de hoje não entra no total
- **AND** o dia da conclusão entra se for dia do hábito

#### Scenario: Prazo vencido corta a janela
- **GIVEN** um objetivo `ATIVO` com prazo ontem e hábito diário vinculado antes
- **WHEN** o dono busca o objetivo hoje
- **THEN** a janela termina no prazo, não em hoje

#### Scenario: Execução antes do vínculo fica de fora
- **GIVEN** um hábito com 30 min dedicados ontem, vinculado hoje
- **WHEN** o dono busca o objetivo
- **THEN** esses 30 min não entram no concluído

### Requirement: Hábito pausado fora do progresso
Hábito `PAUSADO` MUST NÃO somar total nem concluído. O vínculo MUST permanecer. Reativar MUST voltar a contar desde o `created_at` do vínculo, inclusive os dias em que esteve pausado.

#### Scenario: Pausado some do progresso
- **GIVEN** um hábito `ATIVO` que somava 30 de 30, depois pausado
- **WHEN** o dono busca o objetivo
- **THEN** esse hábito não entra no total nem no concluído
- **AND** o vínculo continua no banco

#### Scenario: Reativar recoloca a janela inteira
- **GIVEN** um hábito pausado por dois dias de semana dele e depois reativado
- **WHEN** o dono busca o objetivo
- **THEN** esses dois dias voltam ao total

### Requirement: Reenvio do vínculo de hábito preserva a data
PATCH de hábito que mantém um objetivo já ligado MUST NÃO alterar o instante em que aquele par foi criado. Par novo MUST receber o instante atual. Par ausente no array MUST ser removido.

#### Scenario: Reenviar os mesmos ids
- **GIVEN** um hábito ligado ao objetivo A desde ontem
- **WHEN** envia PATCH com `objetivoIds: [A]` e um título novo
- **THEN** a janela do hábito no progresso de A continua começando ontem

#### Scenario: Trocar o conjunto
- **GIVEN** um hábito ligado a A desde ontem
- **WHEN** envia PATCH com `objetivoIds: [B]` (B ativo)
- **THEN** A deixa de contar esse hábito
- **AND** a janela em B começa hoje
