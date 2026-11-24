# Spec Delta

## MODIFIED Requirements

### Requirement: Listagem paginada por status
O sistema MUST listar objetivos do usuário em formato paginado (`content` + `page`), filtrando por um ou mais `status` informados (parâmetro repetido) e usando `ATIVO` quando ausente. Sem ordenação informada pelo cliente, a ordem MUST ser: `ATIVO` por `prazo` crescente com objetivos sem prazo no fim e, em empate, criação mais antiga primeiro; status encerrados (`CONCLUIDO`, `ARQUIVADO` ou os dois) pela data de encerramento decrescente e, em empate, criação mais recente primeiro. A data de encerramento MUST ser `concluidoEm` quando existir e `arquivadoEm` caso contrário.

#### Scenario: Padrão lista só ativos
- **GIVEN** objetivos do usuário em `ATIVO`, `CONCLUIDO` e `ARQUIVADO`
- **WHEN** lista sem informar `status`
- **THEN** recebe apenas os objetivos `ATIVO`

#### Scenario: Ativos ordenados por prazo com sem prazo no fim
- **GIVEN** objetivos ativos com prazos 31/12, 20/11 e um sem prazo
- **WHEN** lista sem ordenação
- **THEN** recebe na ordem: 20/11, 31/12, sem prazo

#### Scenario: Concluídos mais recentes primeiro
- **GIVEN** dois objetivos concluídos em dias diferentes
- **WHEN** lista com `status` `CONCLUIDO`
- **THEN** o concluído mais recentemente vem primeiro

#### Scenario: Encerrados juntos pela data de encerramento
- **GIVEN** um objetivo concluído em 10/11, um arquivado direto de ativo em 15/11 e um concluído em 05/11 e arquivado em 20/11
- **WHEN** lista com `status` `CONCLUIDO` e `status` `ARQUIVADO`
- **THEN** recebe na ordem: arquivado em 15/11, concluído em 10/11, concluído em 05/11
- **AND** nenhum objetivo `ATIVO` aparece

#### Scenario: Arquivado que já foi concluído ordena pela conclusão
- **GIVEN** um objetivo concluído em 05/11 e arquivado em 20/11, e outro arquivado direto de ativo em 12/11
- **WHEN** lista com `status` `ARQUIVADO`
- **THEN** o arquivado direto em 12/11 vem primeiro

#### Scenario: Status repetido igual
- **WHEN** lista com `status` `CONCLUIDO` duas vezes
- **THEN** recebe o mesmo resultado de um único `status` `CONCLUIDO`

#### Scenario: Ativo junto com encerrado
- **WHEN** lista com `status` `ATIVO` e `status` `CONCLUIDO`
- **THEN** recebe 400 com `title` "Combinação de status inválida"

#### Scenario: Lista vazia
- **GIVEN** o usuário não tem objetivos arquivados
- **WHEN** lista com `status` `ARQUIVADO`
- **THEN** recebe 200 com `content` vazio e `page.totalElements` 0

#### Scenario: Status inválido
- **WHEN** lista com `status` `PAUSADO`
- **THEN** recebe 400 com `title` "Parâmetro inválido"

#### Scenario: Encerrados de outro usuário não aparecem
- **GIVEN** outro usuário com objetivos concluídos e arquivados
- **WHEN** o usuário lista com `status` `CONCLUIDO` e `status` `ARQUIVADO`
- **THEN** recebe apenas os próprios objetivos
