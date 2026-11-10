# tarefa-sessao Specification

## Purpose

Permite ao usuário executar sessões de trabalho em tarefas — no plano aprovado ou fora dele — com timer ou conclusão direta, creditando tempo e progresso de forma previsível.

## Requirements

### Requirement: Concluir direto bloco de tarefa no plano aprovado
O sistema MUST permitir concluir um bloco de tarefa do plano `APROVADO` corrente sem passar por `EM_ANDAMENTO`, criando ou atualizando a execução para `CONCLUIDO` e creditando tempo efetivo igual à duração planejada do bloco.

#### Scenario: Concluir direto a partir de não iniciado
- **GIVEN** um bloco de tarefa no plano `APROVADO` corrente sem execução ou com status efetivo `NAO_INICIADO` ou `ABORTADO`
- **WHEN** o usuário solicita concluir direto esse bloco
- **THEN** a execução fica `CONCLUIDO`
- **AND** o tempo efetivo da sessão é a `duracaoMinutos` do bloco
- **AND** o progresso da tarefa soma essa duração entre os blocos concluídos

#### Scenario: Concluir direto rejeitado se já concluído
- **GIVEN** a execução do bloco já está `CONCLUIDO`
- **WHEN** o usuário solicita concluir direto
- **THEN** o sistema rejeita a operação com conflito

### Requirement: Tempo efetivo no caminho com timer
Quando a sessão passa por `EM_ANDAMENTO` e depois `CONCLUIDO`, o sistema MUST calcular tempo efetivo com a regra existente de minutos efetivos entre `iniciadoEm` e `concluidoEm`, limitada pela duração do bloco.

#### Scenario: Concluir após iniciar
- **GIVEN** uma execução de bloco de tarefa `EM_ANDAMENTO`
- **WHEN** o usuário conclui a execução
- **THEN** o tempo efetivo é derivado do intervalo decorrido e da duração do bloco
- **AND** o progresso da tarefa continua somando a duração planejada do bloco concluído

### Requirement: Plano implícito TAREFA_AVULSA
O sistema MUST materializar, sob demanda, no máximo um plano `TAREFA_AVULSA` por usuário e data civil BRT, fora da corrente do planner (`RASCUNHO`/`APROVADO`), contendo blocos somente de origem tarefa.

#### Scenario: Materialização na primeira sessão avulsa do dia
- **GIVEN** não existe plano `TAREFA_AVULSA` para o usuário na data
- **WHEN** o usuário inicia ou conclui direto uma sessão avulsa de tarefa nessa data
- **THEN** o sistema cria o plano `TAREFA_AVULSA` e um bloco da tarefa com a duração da sessão
- **AND** esse plano não se torna a corrente do Home nem entra na geração/aprovação do planner

#### Scenario: Reuso do plano avulso do dia
- **GIVEN** já existe plano `TAREFA_AVULSA` do usuário na data
- **WHEN** o usuário abre outra sessão avulsa de outra tarefa no mesmo dia
- **THEN** o sistema reutiliza o mesmo plano e adiciona um novo bloco para essa tarefa

### Requirement: Preferir bloco aberto do plano aprovado
Ao iniciar ou concluir direto uma tarefa a partir da lista, se existir bloco dessa tarefa ainda não `CONCLUIDO` no plano `APROVADO` corrente de hoje, o sistema MUST usar esse bloco e NÃO criar bloco avulso nem exigir duração de sessão.

#### Scenario: Lista reusa bloco do plano
- **GIVEN** a tarefa tem um bloco não concluído no plano `APROVADO` de hoje
- **WHEN** o usuário inicia a tarefa pela lista
- **THEN** a execução age sobre esse bloco do plano
- **AND** nenhum bloco `TAREFA_AVULSA` é criado para essa ação

### Requirement: Duração da sessão avulsa
Para sessão avulsa (sem bloco aberto no aprovado), o cliente MUST enviar `duracaoMinutos` da sessão; o servidor MUST aceitar apenas valores inteiros com piso 1 e teto igual ao restante da tarefa (`tarefa.duracaoMinutos` − soma das durações de blocos `CONCLUIDO` da tarefa).

#### Scenario: Sessão dentro do restante
- **GIVEN** uma tarefa com restante 90 minutos
- **WHEN** o usuário inicia sessão avulsa com 30 minutos
- **THEN** o bloco materializado tem `duracaoMinutos` 30

#### Scenario: Sessão acima do restante
- **GIVEN** uma tarefa com restante 20 minutos
- **WHEN** o usuário solicita sessão avulsa com 60 minutos
- **THEN** o sistema rejeita a operação como inválida

#### Scenario: Sem restante
- **GIVEN** a soma de blocos concluídos já cobre a duração da tarefa (ou a tarefa está `FINALIZADA`)
- **WHEN** o usuário solicita iniciar ou concluir direto avulso
- **THEN** o sistema rejeita a operação

### Requirement: Endpoints de sessão de tarefa
O sistema MUST expor iniciar e concluir-direto autenticados por `tarefaId`, com `data` opcional (default hoje BRT) e `duracaoMinutos` obrigatório quando a ação materializa bloco avulso.

#### Scenario: Iniciar avulso
- **WHEN** `POST /tarefas/{id}/iniciar` com duração válida e sem bloco aberto no aprovado
- **THEN** a resposta indica execução `EM_ANDAMENTO` do bloco avulso criado ou reutilizado conforme as regras de materialização

#### Scenario: Concluir direto avulso
- **WHEN** `POST /tarefas/{id}/concluir-direto` com duração válida e sem bloco aberto no aprovado
- **THEN** a resposta indica execução `CONCLUIDO` com tempo efetivo igual à duração do bloco da sessão

### Requirement: Progresso e finalização da tarefa
Após cada bloco de tarefa `CONCLUIDO`, o sistema MUST manter a regra de progresso por soma das durações planejadas dos blocos concluídos e marcar a tarefa `FINALIZADA` quando a soma for maior ou igual a `tarefa.duracaoMinutos`.

#### Scenario: Sessão parcial não finaliza
- **GIVEN** tarefa de 120 minutos sem blocos concluídos
- **WHEN** o usuário conclui direto uma sessão de 30 minutos
- **THEN** a tarefa fica `EM_PROGRESSO` (ou permanece se já estava)
- **AND** o restante efetivo para novas sessões passa a 90 minutos

#### Scenario: Sessão que completa a duração
- **GIVEN** tarefa de 120 minutos com 90 minutos já concluídos em blocos
- **WHEN** o usuário conclui uma sessão de 30 minutos
- **THEN** a tarefa fica `FINALIZADA`
