# Spec Delta

## Purpose

Leva os direitos do titular para as telas de Perfil do web e do Android: exportar os próprios dados, excluir a conta com confirmação forte e reagir de forma clara quando a conta foi excluída em outro dispositivo.

## ADDED Requirements

### Requirement: Exportar dados pelo Perfil
O Perfil (web e Android) MUST oferecer a ação "Exportar meus dados", que baixa (web) ou salva/compartilha (Android) o JSON de `GET /me/export` com um nome de arquivo datado. Durante o download a ação MUST ficar desabilitada com indicação de progresso.

#### Scenario: Export bem-sucedido no web
- **WHEN** o usuário aciona "Exportar meus dados" no Perfil web
- **THEN** o navegador baixa um arquivo `.json` com a data no nome

#### Scenario: Export bem-sucedido no Android
- **WHEN** o usuário aciona "Exportar meus dados" no Perfil do app
- **THEN** o sistema abre a opção de salvar ou compartilhar o arquivo `.json`

#### Scenario: Clique duplo não duplica download
- **WHEN** o usuário aciona a ação duas vezes seguidas
- **THEN** só uma requisição de export é feita

#### Scenario: Falha no export
- **GIVEN** a API responde `500`, timeout ou HTML do proxy
- **WHEN** o usuário exporta
- **THEN** a tela mostra mensagem de erro, a ação volta a ficar disponível e nada é baixado

### Requirement: Excluir conta com confirmação forte
O Perfil (web e Android) MUST oferecer "Excluir conta" numa área de perigo. A exclusão MUST exigir um diálogo que explica que é irreversível, sugere exportar antes, pede a senha atual e só habilita o botão final depois que o usuário digita a palavra `EXCLUIR`.

#### Scenario: Botão final bloqueado
- **GIVEN** o diálogo de exclusão aberto
- **WHEN** a senha está preenchida mas a confirmação não é exatamente `EXCLUIR`
- **THEN** o botão de excluir fica desabilitado e nenhuma requisição é feita

#### Scenario: Cancelar não altera nada
- **WHEN** o usuário fecha o diálogo
- **THEN** nenhuma requisição é feita e o Perfil continua igual

#### Scenario: Exclusão bem-sucedida
- **GIVEN** senha correta e confirmação `EXCLUIR`
- **WHEN** o usuário confirma
- **THEN** a sessão local é limpa e o usuário vai para a tela de login com aviso de que a conta foi excluída

#### Scenario: Senha incorreta
- **GIVEN** a API responde `401` "Credenciais inválidas"
- **WHEN** o usuário confirma a exclusão
- **THEN** o diálogo continua aberto com erro no campo de senha, sem tentar refresh e sem deslogar

#### Scenario: Erro de servidor na exclusão
- **GIVEN** a API responde `500` ou timeout
- **WHEN** o usuário confirma
- **THEN** o diálogo mostra mensagem de erro, mantém a sessão e permite tentar de novo

### Requirement: Reagir a conta excluída em outro dispositivo
Web e Android MUST tratar qualquer `401` com ProblemDetail `title` "Conta excluída" sem tentar renovar a sessão: MUST limpar a sessão local e levar à tela de login com a mensagem de que a conta foi excluída. Outros `401` MUST seguir o fluxo de refresh atual.

#### Scenario: Conta excluída detectada
- **GIVEN** a conta foi excluída em outro dispositivo
- **WHEN** qualquer chamada recebe `401` "Conta excluída"
- **THEN** o cliente não chama `/auth/refresh`, limpa a sessão e mostra o login com a mensagem de conta excluída

#### Scenario: 401 comum continua renovando
- **WHEN** uma chamada recebe `401` com outro `title`
- **THEN** o cliente tenta o refresh como antes

#### Scenario: Corpo do 401 ilegível
- **GIVEN** um `401` com corpo vazio ou não-JSON
- **WHEN** o cliente o recebe
- **THEN** trata como `401` comum e segue o fluxo de refresh
