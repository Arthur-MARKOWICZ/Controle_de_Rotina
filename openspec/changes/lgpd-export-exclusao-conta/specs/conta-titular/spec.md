# Spec Delta

## Purpose

Garante ao titular os direitos da LGPD sobre a própria conta na API: exportar todos os seus dados e excluir a conta de forma definitiva, sem deixar dados órfãos e sem que sessões abertas continuem funcionando.

## ADDED Requirements

### Requirement: Exportar todos os dados do titular
O sistema MUST expor `GET /me/export`, autenticado, que responde `200` com um JSON entregue como anexo (`Content-Disposition: attachment`) contendo a conta (sem hash de senha) e todos os dados do usuário: tarefas e subtarefas, projetos e reservas, tags e vínculos, hábitos, itens Decidir, pendências, objetivos e vínculos, capacidade semanal e overrides, planos com blocos, execuções e eventos de XP.

#### Scenario: Export completo
- **GIVEN** um usuário com pelo menos um registro em cada domínio
- **WHEN** chama `GET /me/export`
- **THEN** recebe `200` com `Content-Disposition: attachment`
- **AND** o JSON traz uma seção por domínio, cada uma com os registros do usuário

#### Scenario: Conta sem dados
- **GIVEN** um usuário recém-cadastrado sem tarefas, hábitos nem planos
- **WHEN** chama `GET /me/export`
- **THEN** recebe `200` com os dados da conta, a capacidade padrão e as demais seções como listas vazias

#### Scenario: Export não traz credenciais
- **WHEN** o usuário exporta seus dados
- **THEN** o JSON não contém hash de senha nem refresh tokens

### Requirement: Export isolado por usuário
O export MUST conter apenas registros do usuário autenticado e MUST NOT conter nenhum dado de outro usuário.

#### Scenario: Dados de outro usuário ausentes
- **GIVEN** os usuários A e B, ambos com dados em todos os domínios
- **WHEN** A chama `GET /me/export`
- **THEN** nenhum id de registro de B aparece no JSON

### Requirement: Export inclui registros removidos logicamente
O export MUST incluir tarefas e hábitos com soft-delete, projetos e objetivos arquivados e objetivos concluídos, cada um com o campo que indica seu estado (`deletedAt`, `status`, datas de arquivamento/conclusão).

#### Scenario: Tarefa apagada aparece no export
- **GIVEN** o usuário apagou uma tarefa (soft-delete)
- **WHEN** exporta os dados
- **THEN** a tarefa aparece no export com `deletedAt` preenchido

### Requirement: Export exige autenticação
`GET /me/export` sem token válido MUST responder `401` com ProblemDetail.

#### Scenario: Export sem token
- **WHEN** `GET /me/export` é chamado sem `Authorization`
- **THEN** responde `401` com ProblemDetail `title` "Não autorizado"

### Requirement: Excluir conta com confirmação por senha
O sistema MUST expor `DELETE /me`, autenticado, que exige `senhaAtual` no corpo. Com a senha correta, MUST apagar definitivamente a conta e todos os dados dela, responder `204` e limpar o cookie de refresh.

#### Scenario: Exclusão bem-sucedida
- **GIVEN** um usuário autenticado
- **WHEN** chama `DELETE /me` com a senha atual correta
- **THEN** recebe `204` e um `Set-Cookie` que expira o cookie de refresh
- **AND** o e-mail da conta fica livre para um novo cadastro

#### Scenario: Senha incorreta
- **WHEN** chama `DELETE /me` com `senhaAtual` errada
- **THEN** recebe `401` com ProblemDetail `title` "Não autorizado" e `detail` "Credenciais inválidas."
- **AND** a conta e todos os dados continuam intactos

#### Scenario: Senha ausente
- **WHEN** chama `DELETE /me` sem `senhaAtual` ou com `senhaAtual` em branco
- **THEN** recebe `400` com ProblemDetail `title` "Dados inválidos" e `errors` apontando `senhaAtual`
- **AND** nada é apagado

#### Scenario: Corpo malformado
- **WHEN** chama `DELETE /me` com JSON inválido
- **THEN** recebe `400` com ProblemDetail `title` "Requisição inválida" e nada é apagado

### Requirement: Exclusão não deixa dados órfãos
Depois de `DELETE /me`, MUST NOT restar no banco nenhuma linha pertencente ao usuário, em nenhum domínio, nem linhas dependentes (blocos de plano, vínculos de tag, vínculos de objetivo, dias de hábito). Dados de outros usuários MUST permanecer intactos.

#### Scenario: Zero linhas do usuário após exclusão
- **GIVEN** um usuário com dados em todos os domínios, incluindo subtarefa, bloco executado e vínculos de tag e objetivo
- **WHEN** exclui a conta
- **THEN** nenhuma tabela contém linhas desse usuário ou dependentes delas

#### Scenario: Outro usuário intacto
- **GIVEN** os usuários A e B com dados em todos os domínios
- **WHEN** A exclui a conta
- **THEN** a contagem de linhas de B em cada tabela não muda

### Requirement: Exclusão é atômica
A exclusão MUST ocorrer em uma única transação: ou tudo do usuário é apagado, ou nada é.

#### Scenario: Falha no meio não apaga parcialmente
- **GIVEN** uma falha de banco durante a exclusão
- **WHEN** `DELETE /me` é processado
- **THEN** a requisição falha com ProblemDetail e todos os dados do usuário continuam presentes

### Requirement: Sessões param de funcionar após a exclusão
Depois da exclusão, o access token usado MUST deixar de autenticar, os refresh tokens MUST deixar de renovar, e qualquer requisição autenticada com access token ainda não expirado de qualquer dispositivo MUST responder `401` com ProblemDetail `title` "Conta excluída".

#### Scenario: Access token de outro dispositivo
- **GIVEN** a conta foi excluída pelo dispositivo 1
- **WHEN** o dispositivo 2 chama `GET /tarefas` com um access token ainda não expirado
- **THEN** recebe `401` com ProblemDetail `title` "Conta excluída"

#### Scenario: Escrita com token de conta excluída não vira 500
- **GIVEN** a conta foi excluída
- **WHEN** um dispositivo chama `POST /tarefas` com access token ainda válido
- **THEN** recebe `401` "Conta excluída" e nenhuma linha é inserida

#### Scenario: Refresh após exclusão
- **GIVEN** a conta foi excluída
- **WHEN** qualquer dispositivo chama `POST /auth/refresh` com seu refresh token
- **THEN** recebe `401` e nenhum token novo é emitido

#### Scenario: Segunda exclusão com o mesmo token
- **GIVEN** a conta foi excluída
- **WHEN** o mesmo token chama `DELETE /me` de novo
- **THEN** recebe `401` e nada mais é alterado

### Requirement: MCP não exclui a conta
O servidor MCP MUST NOT expor ferramenta nem chamada que exclua a conta ou exporte os dados. Ao receber `401` "Conta excluída", MUST limpar a sessão local e falhar com erro específico de conta excluída, sem tentar renovar a sessão.

#### Scenario: Nenhuma ferramenta de exclusão
- **WHEN** o servidor MCP lista suas ferramentas
- **THEN** nenhuma delas chama `DELETE /me` nem `GET /me/export`

#### Scenario: MCP recebe conta excluída
- **GIVEN** a sessão do MCP pertence a uma conta excluída
- **WHEN** uma ferramenta chama a API e recebe `401` "Conta excluída"
- **THEN** o MCP não chama `/auth/refresh`, limpa a sessão local e devolve a mensagem de conta excluída
