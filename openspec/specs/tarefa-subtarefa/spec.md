# tarefa-subtarefa Specification

## Purpose

Permite subtarefas de um nível como tarefas de verdade: o pai soma a duração dos filhos e só as folhas entram no planner, no atraso e na sessão.

## Requirements

### Requirement: Criar subtarefa de um nível
O sistema MUST permitir criar uma tarefa com `parentId` opcional apontando para uma tarefa-raiz do mesmo usuário, não deletada. Criar com `parentId` de um filho, de outro usuário, inexistente ou deletado MUST ser rejeitado.

#### Scenario: Criar filho de raiz
- **GIVEN** uma tarefa-raiz do usuário sem filhos
- **WHEN** cria uma tarefa com `parentId` dessa raiz, título e duração válidos
- **THEN** a tarefa é persistida como subtarefa
- **AND** a resposta inclui `parentId`

#### Scenario: Neto rejeitado
- **GIVEN** uma subtarefa existente
- **WHEN** tenta criar outra tarefa com `parentId` igual ao id da subtarefa
- **THEN** o sistema rejeita a operação como inválida

#### Scenario: Pai de outro usuário
- **GIVEN** uma tarefa-raiz de outro usuário
- **WHEN** tenta criar subtarefa com esse `parentId`
- **THEN** o sistema responde 404

### Requirement: Recusar filho se o pai já tem bloco concluído
O sistema MUST recusar criar subtarefa quando o pai tiver ao menos um bloco com execução `CONCLUIDO`.

#### Scenario: Pai com progresso recusado
- **GIVEN** uma tarefa-raiz com um bloco `CONCLUIDO`
- **WHEN** tenta criar uma subtarefa dela
- **THEN** o sistema rejeita a operação com conflito ou inválido

### Requirement: Duração do pai é a soma dos filhos
Enquanto houver filhos não deletados, `duracaoMinutos` do pai MUST ser a soma das durações desses filhos. PATCH de duração no pai nesse estado MUST ser rejeitado. Ao perder o último filho não deletado, o pai MUST permanecer com a última soma (não zerar) e voltar a ser folha.

#### Scenario: Dois filhos de uma hora
- **GIVEN** uma raiz com duração 60
- **WHEN** adiciona duas subtarefas de 60 minutos
- **THEN** a duração do pai passa a 120

#### Scenario: PATCH duração no pai com filhos
- **GIVEN** um pai com filhos não deletados
- **WHEN** envia PATCH alterando `duracaoMinutos` do pai
- **THEN** o sistema rejeita a operação

#### Scenario: Último filho excluído
- **GIVEN** um pai cuja soma atual é 90 e resta um filho
- **WHEN** esse filho é soft-deletado
- **THEN** o pai fica sem filhos não deletados
- **AND** `duracaoMinutos` do pai permanece 90

### Requirement: Projeto default do filho
Ao criar subtarefa sem `projetoId`, o sistema MUST copiar o `projetoId` do pai. `projetoId` enviado no create MUST prevalecer. Tags MUST NÃO ser copiadas do pai.

#### Scenario: Herda projeto omitido
- **GIVEN** um pai vinculado ao projeto P
- **WHEN** cria subtarefa sem `projetoId`
- **THEN** o filho fica com `projetoId` = P

#### Scenario: Projeto explícito
- **GIVEN** um pai no projeto P
- **WHEN** cria subtarefa com `projetoId` de outro projeto ativo do usuário
- **THEN** o filho fica com o projeto enviado

### Requirement: Só folhas no planner e no atraso
Tarefa com filhos não deletados MUST NÃO entrar na geração do plano nem no job que marca `ATRASADA` pelo próprio prazo. Folhas MUST continuar usando prazo, prioridade, saldo e score como hoje.

#### Scenario: Pai fora da geração
- **GIVEN** um pai com dois filhos pendentes e saldo derivado 120
- **WHEN** o plano é gerado
- **THEN** o pai não recebe bloco
- **AND** cada filho elegível pode receber bloco pelo score próprio

#### Scenario: Prazo do pai não atrasa o pai
- **GIVEN** um pai com prazo no passado e filhos com prazo futuro
- **WHEN** o job de atraso roda
- **THEN** o pai não vira `ATRASADA` por causa do próprio prazo
- **AND** cada filho segue a regra de atraso pelo prazo do filho

### Requirement: Finalizar pai com filhos em cascata
PATCH `FINALIZADA` no pai MUST marcar `FINALIZADA` o pai e todos os filhos não deletados. Esse fluxo MUST NÃO conceder XP de bloco nem bônus `TAREFA_FINALIZADA`. Quando o último filho não deletado ficar `FINALIZADA`, o pai MUST ficar `FINALIZADA`.

#### Scenario: Confirmar finalizar pai
- **GIVEN** um pai com dois filhos `PENDENTE`
- **WHEN** o status do pai vai para `FINALIZADA`
- **THEN** pai e filhos ficam `FINALIZADA`
- **AND** nenhum evento de XP é criado por essa operação

#### Scenario: Último filho finalizado
- **GIVEN** um pai com um filho `FINALIZADA` e outro que acaba de ser finalizado por sessão
- **WHEN** o segundo filho fica `FINALIZADA`
- **THEN** o pai fica `FINALIZADA`

### Requirement: Reabrir é assimétrico
Reabrir o pai (status diferente de `FINALIZADA`) MUST NÃO reabrir filhos `FINALIZADA`. Reabrir um filho MUST reabrir o pai se o pai estiver `FINALIZADA`. Criar subtarefa em pai `FINALIZADA` MUST reabrir o pai.

#### Scenario: Reabrir só o pai
- **GIVEN** pai e filhos `FINALIZADA`
- **WHEN** o pai volta para `PENDENTE`
- **THEN** os filhos permanecem `FINALIZADA`

#### Scenario: Reabrir filho reabre o pai
- **GIVEN** pai e um filho `FINALIZADA`
- **WHEN** esse filho volta para `PENDENTE`
- **THEN** o pai deixa de estar `FINALIZADA`

#### Scenario: Filho novo em pai finalizado
- **GIVEN** um pai `FINALIZADA` sem bloco `CONCLUIDO`
- **WHEN** cria uma subtarefa
- **THEN** o filho nasce `PENDENTE`
- **AND** o pai deixa de estar `FINALIZADA`

### Requirement: Cancelar pai em cascata
PATCH `CANCELADA` no pai MUST marcar `CANCELADA` o pai e todos os filhos não deletados.

#### Scenario: Cancelar pai com filhos
- **GIVEN** um pai com filhos abertos
- **WHEN** o status do pai vai para `CANCELADA`
- **THEN** pai e filhos ficam `CANCELADA`

### Requirement: Excluir e restaurar em cascata
Soft delete do pai MUST soft-deletar os filhos ainda ativos. Restaurar o pai MUST restaurar todos os filhos soft-deletados daquele pai. Restaurar um filho MUST restaurar o pai se o pai estiver deletado; irmãos deletados MUST permanecer deletados.

#### Scenario: Excluir pai
- **GIVEN** um pai com dois filhos ativos
- **WHEN** o pai é excluído
- **THEN** pai e filhos ficam com `deletedAt` preenchido

#### Scenario: Restaurar pai
- **GIVEN** um pai e seus filhos soft-deletados
- **WHEN** o pai é restaurado
- **THEN** pai e todos esses filhos voltam ativos

#### Scenario: Restaurar um filho
- **GIVEN** pai e filhos A e B soft-deletados
- **WHEN** restaura só A
- **THEN** A e o pai voltam ativos
- **AND** B permanece deletado

### Requirement: Resposta e listagem expõem o pai
Respostas de tarefa MUST incluir `parentId` e, quando houver pai, `parentTitulo`. Listagem autenticada MUST continuar incluindo filhos (lista plana); filtros atuais aplicam-se a cada tarefa.

#### Scenario: Get do filho
- **GIVEN** uma subtarefa cujo pai se chama "Estudar Security"
- **WHEN** busca a subtarefa
- **THEN** a resposta tem `parentId` do pai
- **AND** `parentTitulo` é "Estudar Security"

#### Scenario: Lista inclui folhas e raízes
- **GIVEN** uma raiz e duas subtarefas ativas
- **WHEN** lista tarefas sem filtro extra de hierarquia
- **THEN** as três aparecem no resultado
