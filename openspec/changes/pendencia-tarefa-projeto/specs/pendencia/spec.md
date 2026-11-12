# Spec Delta

## Purpose

Permite que uma tarefa ou um projeto espere outro terminar antes de entrar no planner, na sessão e na finalização — com espera explícita entre os dois tipos, sem inventar status novo de projeto.

## ADDED Requirements

### Requirement: Criar espera entre tarefa e projeto
O sistema MUST permitir que o usuário autenticado crie uma espera com origem (tarefa ou projeto próprio) e alvo (tarefa ou projeto próprio). As quatro combinações MUST ser aceitas. Origem e alvo MUST ser do mesmo usuário, existentes e não deletados; projeto envolvido MUST estar `ATIVO`.

#### Scenario: Tarefa espera tarefa
- **GIVEN** duas tarefas do usuário, A e B
- **WHEN** cria espera com origem A e alvo B
- **THEN** a espera é persistida
- **AND** A fica bloqueada enquanto B não estiver `FINALIZADA`

#### Scenario: Tarefa espera projeto
- **GIVEN** uma tarefa T e um projeto P `ATIVO` do usuário
- **WHEN** cria espera com origem T e alvo P
- **THEN** a espera é persistida
- **AND** T fica bloqueada enquanto P não estiver resolvido

#### Scenario: Projeto espera tarefa
- **GIVEN** um projeto P `ATIVO` e uma tarefa T do usuário
- **WHEN** cria espera com origem P e alvo T
- **THEN** a espera é persistida
- **AND** as tarefas não deletadas de P ficam bloqueadas enquanto T não estiver `FINALIZADA`

#### Scenario: Projeto espera projeto
- **GIVEN** dois projetos ativos P e Q do usuário
- **WHEN** cria espera com origem P e alvo Q
- **THEN** a espera é persistida
- **AND** as tarefas não deletadas de P ficam bloqueadas enquanto Q não estiver resolvido

#### Scenario: Alvo de outro usuário
- **GIVEN** uma tarefa do usuário e um alvo que não é dele
- **WHEN** tenta criar a espera
- **THEN** o sistema responde 404

### Requirement: Recusar auto-espera, duplicata e ciclo
O sistema MUST recusar espera em que origem e alvo são o mesmo recurso, espera já existente no mesmo par, ou espera que feche um ciclo no grafo de esperas do usuário.

#### Scenario: Auto-espera
- **GIVEN** a tarefa A
- **WHEN** tenta criar espera com origem A e alvo A
- **THEN** o sistema rejeita a operação como inválida

#### Scenario: Par duplicado
- **GIVEN** já existe espera de A para B
- **WHEN** tenta criar de novo espera de A para B
- **THEN** o sistema rejeita a operação com conflito

#### Scenario: Ciclo de duas pontas
- **GIVEN** espera de A para B
- **WHEN** tenta criar espera de B para A
- **THEN** o sistema rejeita a operação como inválida

#### Scenario: Ciclo transitivo
- **GIVEN** espera de A para B e de B para C
- **WHEN** tenta criar espera de C para A
- **THEN** o sistema rejeita a operação como inválida

### Requirement: Várias esperas resolvem em E
Uma origem MUST permanecer bloqueada enquanto ao menos uma espera dela estiver irresolvida. Só MUST ficar desbloqueada quando todos os alvos estiverem resolvidos (ou as esperas tiverem sido removidas).

#### Scenario: Duas esperas, uma resolvida
- **GIVEN** tarefa A espera B e C, e só B está `FINALIZADA`
- **WHEN** consulta o estado de A
- **THEN** A permanece bloqueada

#### Scenario: Todas resolvidas
- **GIVEN** tarefa A espera B e C
- **WHEN** B e C ficam `FINALIZADA`
- **THEN** A deixa de estar bloqueada

### Requirement: Alvo tarefa só resolve com FINALIZADA
Espera cujo alvo é tarefa MUST ficar resolvida somente quando essa tarefa estiver `FINALIZADA` e não deletada. `CANCELADA`, `PENDENTE`, `EM_PROGRESSO` e `ATRASADA` MUST NÃO resolver.

#### Scenario: Alvo finalizado
- **GIVEN** A espera B e B está `PENDENTE`
- **WHEN** B passa a `FINALIZADA`
- **THEN** essa espera fica resolvida

#### Scenario: Alvo cancelado
- **GIVEN** A espera B
- **WHEN** B passa a `CANCELADA`
- **THEN** a espera permanece irresolvida
- **AND** A continua bloqueada

### Requirement: Alvo projeto resolve sem tarefas abertas
Espera cujo alvo é projeto MUST ficar resolvida somente quando o projeto tiver ao menos uma tarefa não deletada e nenhuma tarefa não deletada em `PENDENTE`, `EM_PROGRESSO` ou `ATRASADA`. Projeto sem tarefas MUST continuar bloqueando.

#### Scenario: Projeto vazio
- **GIVEN** A espera o projeto P e P não tem tarefas
- **WHEN** consulta a espera
- **THEN** a espera permanece irresolvida

#### Scenario: Todas as tarefas do alvo finalizadas
- **GIVEN** A espera P e P tem duas tarefas não deletadas
- **WHEN** ambas ficam `FINALIZADA`
- **THEN** a espera fica resolvida

#### Scenario: Ainda há tarefa atrasada no alvo
- **GIVEN** A espera P, uma tarefa de P está `FINALIZADA` e outra `ATRASADA`
- **WHEN** consulta a espera
- **THEN** a espera permanece irresolvida

### Requirement: Remover espera e limpeza de vínculo
O dono MUST poder remover uma espera explicitamente. Soft delete da tarefa alvo ou origem MUST apagar as esperas daquele recurso. Arquivar um projeto envolvido (origem ou alvo) MUST apagar as esperas daquele projeto. Restaurar tarefa MUST NÃO recriar a espera.

#### Scenario: Remover espera
- **GIVEN** A espera B
- **WHEN** o usuário remove essa espera
- **THEN** o vínculo deixa de existir
- **AND** A desbloqueia se não restar outra espera irresolvida

#### Scenario: Soft delete do alvo
- **GIVEN** A espera B
- **WHEN** B é soft-deletada
- **THEN** a espera é apagada
- **AND** A desbloqueia se não restar outra espera irresolvida

#### Scenario: Arquivar projeto alvo
- **GIVEN** A espera o projeto P
- **WHEN** P é arquivado
- **THEN** a espera é apagada

#### Scenario: Restore não recria
- **GIVEN** a espera sumiu no soft delete de B
- **WHEN** B é restaurada
- **THEN** a espera não volta

### Requirement: Tarefa bloqueada fora do planner e do ajuste
Tarefa com espera irresolvida própria, cujo pai estiver bloqueado, ou cujo projeto estiver bloqueado como origem, MUST NÃO receber bloco na geração do plano. Adicionar essa tarefa a um rascunho MUST ser rejeitado. Reserva de projeto origem bloqueado MUST continuar devolvendo minutos não usados ao pool livre.

#### Scenario: Geração omite tarefa que espera
- **GIVEN** tarefa A espera B ainda não `FINALIZADA` e A seria elegível por status e saldo
- **WHEN** o plano é gerado
- **THEN** A não recebe bloco

#### Scenario: Projeto origem bloqueado omite as tarefas
- **GIVEN** projeto P espera Q irresolvido e T pertence a P
- **WHEN** o plano é gerado
- **THEN** T não recebe bloco

#### Scenario: Filho herda bloqueio do pai
- **GIVEN** o pai espera X irresolvido e tem uma subtarefa folha
- **WHEN** o plano é gerado
- **THEN** a subtarefa não recebe bloco

#### Scenario: Adicionar bloco rejeitado
- **GIVEN** um rascunho e uma tarefa bloqueada
- **WHEN** tenta adicionar a tarefa ao plano
- **THEN** o sistema rejeita a operação

### Requirement: Recusar finalizar tarefa bloqueada
PATCH que coloque a tarefa em `FINALIZADA` MUST ser rejeitado enquanto ela estiver bloqueada. Folha não bloqueada MUST continuar o fluxo atual.

#### Scenario: Finalizar bloqueada
- **GIVEN** tarefa A bloqueada
- **WHEN** envia PATCH com status `FINALIZADA`
- **THEN** o sistema rejeita a operação
- **AND** o status de A não muda

### Requirement: Resposta expõe esperas e bloqueio
Respostas de tarefa e de projeto MUST incluir a lista de esperas daquele recurso como origem (alvo tipo/id/título, se está resolvida) e um indicador `bloqueada`. Listagem MUST NÃO disparar N+1 para montar esse enrich.

#### Scenario: Get da tarefa com espera
- **GIVEN** A espera B cujo título é "Estudar"
- **WHEN** busca A
- **THEN** a resposta inclui a espera com alvo B e título "Estudar"
- **AND** `bloqueada` é verdadeiro se a espera estiver irresolvida

#### Scenario: Get do projeto sem espera
- **GIVEN** um projeto sem esperas como origem
- **WHEN** busca o projeto
- **THEN** `esperas` vem vazio
- **AND** `bloqueada` é falso
