# Spec Delta

## Purpose

Mantém um contrato OpenAPI da API como fonte única dos formatos de requisição e resposta, de modo que backend, front web, MCP e app mobile não divirjam sem que a divergência seja detectada antes do deploy.

## ADDED Requirements

### Requirement: Spec OpenAPI versionado e sincronizado com o backend
O repositório MUST conter um spec OpenAPI 3.1 da API em `contract/openapi.json`, gerado a partir do backend. A suíte de testes do backend MUST falhar quando o spec gerado pela aplicação diferir do arquivo versionado, e a falha MUST indicar como regenerar o spec.

#### Scenario: Spec atualizado
- **GIVEN** nenhum endpoint ou DTO mudou desde a última regeneração
- **WHEN** a suíte do backend roda
- **THEN** o teste de contrato passa

#### Scenario: DTO mudou sem regenerar o spec
- **GIVEN** um campo foi adicionado a um record de resposta
- **WHEN** a suíte do backend roda sem `contract/openapi.json` ter sido regenerado
- **THEN** o teste de contrato falha
- **AND** a mensagem indica o comando para regenerar o spec

#### Scenario: Spec determinístico
- **GIVEN** o backend não mudou
- **WHEN** o spec é gerado duas vezes seguidas
- **THEN** os dois arquivos são idênticos byte a byte

### Requirement: Campos de resposta sempre presentes e nulidade explícita
Todo campo de um schema usado em resposta MUST constar como obrigatório no spec. Um campo MUST aceitar `null` no spec somente quando o backend o declara anulável. A resposta real MUST sempre incluir todos os campos do schema, inclusive os de valor nulo.

#### Scenario: Campo não anulável
- **GIVEN** `TarefaResponse.id` não é declarado anulável
- **WHEN** o spec é gerado
- **THEN** `id` aparece em `required` e o tipo não aceita `null`

#### Scenario: Campo anulável
- **GIVEN** `TarefaResponse.prazo` é declarado anulável
- **WHEN** o spec é gerado
- **THEN** `prazo` aparece em `required` e o tipo aceita `null`

#### Scenario: Resposta com valor nulo
- **GIVEN** uma tarefa sem prazo
- **WHEN** o cliente busca a tarefa
- **THEN** o JSON contém a chave `prazo` com valor `null`

### Requirement: Campos de requisição obrigatórios só quando validados
Num schema usado só como corpo de requisição, um campo MUST constar como obrigatório somente quando o backend exige sua presença por validação. Os demais campos MUST ser opcionais no spec, de modo que corpos parciais, como os de `PATCH`, continuem válidos.

#### Scenario: Campo validado como obrigatório
- **GIVEN** `CriarTagRequest.nome` exige valor não vazio
- **WHEN** o spec é gerado
- **THEN** `nome` aparece em `required` de `CriarTagRequest`

#### Scenario: PATCH parcial
- **GIVEN** `AtualizarTagRequest` não exige nenhum campo
- **WHEN** o spec é gerado
- **THEN** `AtualizarTagRequest` não tem campos em `required`
- **AND** um cliente tipado pelo spec pode enviar só `{ "cor": "#FF0000" }`

### Requirement: Endpoint do spec fechado em produção
O endpoint que serve o spec MUST ficar desativado por padrão e MUST NÃO responder em produção. Ele MUST ficar disponível apenas nos ambientes de teste e de desenvolvimento local.

#### Scenario: Produção
- **GIVEN** a aplicação roda com a configuração de produção
- **WHEN** um cliente requisita `/v3/api-docs`
- **THEN** o spec não é devolvido

#### Scenario: Desenvolvimento local
- **GIVEN** a aplicação roda no perfil `local`
- **WHEN** um cliente requisita `/v3/api-docs`
- **THEN** recebe o spec OpenAPI em JSON

### Requirement: Formato estável de paginação
Toda listagem paginada (`GET /tarefas`, `/habitos`, `/projetos`, `/decidir`) MUST responder `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`. Campos de paginação MUST NÃO aparecer na raiz da resposta, e esse formato MUST constar no spec com todos os campos obrigatórios.

#### Scenario: Página intermediária
- **GIVEN** o usuário tem 45 tarefas
- **WHEN** lista `GET /tarefas?page=1&size=20`
- **THEN** recebe 20 itens em `content`
- **AND** `page` é `{ size: 20, number: 1, totalElements: 45, totalPages: 3 }`

#### Scenario: Lista vazia
- **GIVEN** o usuário não tem hábitos
- **WHEN** lista `GET /habitos`
- **THEN** `content` é `[]` e `page.totalElements` e `page.totalPages` são `0`

#### Scenario: Página além do fim
- **GIVEN** o usuário tem 5 projetos
- **WHEN** lista `GET /projetos?page=3&size=20`
- **THEN** `content` é `[]` e `page.totalElements` continua `5`

#### Scenario: Sem campos antigos na raiz
- **WHEN** qualquer listagem paginada responde
- **THEN** a raiz do JSON não contém `totalElements`, `last`, `pageable` nem `sort`

### Requirement: Clientes TypeScript tipados pelo contrato
O front web e o MCP MUST derivar de `contract/openapi.json` os tipos de requisição e resposta dos domínios tag e tarefa e dos tipos de página. O CI MUST falhar quando os tipos gerados commitados estiverem desatualizados em relação ao spec.

#### Scenario: Campo removido do backend
- **GIVEN** um campo de `TarefaResponse` usado pelo front foi removido e o spec foi regenerado
- **WHEN** o build do front roda com os tipos regenerados
- **THEN** o build falha de forma tipada no ponto de uso

#### Scenario: Tipos esquecidos
- **GIVEN** `contract/openapi.json` mudou e os tipos gerados do front ou do MCP não foram regenerados
- **WHEN** o CI roda
- **THEN** o job de validação falha

### Requirement: Modelos do app mobile validados contra o contrato
A suíte do mobile MUST verificar cada modelo Kotlin que espelha um schema do contrato. O modelo pode ter só parte dos campos, mas todo campo dele MUST existir no schema. Um campo não anulável no modelo MUST ser obrigatório e não anulável no schema, e os valores de enum MUST ser os mesmos.

#### Scenario: Modelo em dia
- **GIVEN** os modelos Kotlin espelham o spec atual
- **WHEN** os testes do mobile rodam
- **THEN** o teste de contrato passa

#### Scenario: Campo que não existe mais no backend
- **GIVEN** `parentTitulo` foi removido de `TarefaResponse` e o spec foi regenerado
- **WHEN** os testes do mobile rodam com `Tarefa.parentTitulo` ainda no modelo
- **THEN** o teste de contrato falha nomeando o modelo e o campo

#### Scenario: Enum divergente
- **GIVEN** um novo valor foi adicionado a `StatusTarefa` no backend e o spec foi regenerado
- **WHEN** os testes do mobile rodam sem o valor existir no enum Kotlin
- **THEN** o teste de contrato falha nomeando o enum e o valor ausente

#### Scenario: Campo anulável tratado como obrigatório
- **GIVEN** o spec declara `prazo` anulável e o modelo Kotlin o declara não anulável
- **WHEN** os testes do mobile rodam
- **THEN** o teste de contrato falha nomeando o modelo e o campo
