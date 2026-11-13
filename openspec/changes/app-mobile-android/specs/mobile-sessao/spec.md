# Spec Delta

## Purpose

Define como o app Android autentica o usuário na API REST existente, guarda e renova a sessão com segurança e escolhe o ambiente da API, sem exigir mudança no backend.

## ADDED Requirements

### Requirement: Login por e-mail e senha
O app MUST oferecer uma tela de login com e-mail e senha que chama `POST /auth/login`. Em sucesso, entra na aba Tarefas (spec `mobile-navegacao`). Em 401 MUST mostrar mensagem genérica de credenciais inválidas, sem dizer se o e-mail existe. O app MUST NÃO oferecer cadastro de conta neste corte.

#### Scenario: Login válido
- **GIVEN** o usuário tem conta ativa
- **WHEN** informa e-mail e senha corretos e confirma
- **THEN** o app abre a aba Tarefas autenticada

#### Scenario: Credenciais inválidas
- **WHEN** a API responde 401 ao login
- **THEN** o app mostra "E-mail ou senha inválidos" e continua na tela de login
- **AND** a senha digitada é apagada do campo

#### Scenario: API inacessível
- **WHEN** o login falha por erro de rede ou timeout
- **THEN** o app mostra uma mensagem de falha de conexão com a opção de tentar de novo

### Requirement: Armazenamento seguro da sessão
O access token MUST ficar só em memória. O refresh token recebido em `Set-Cookie` MUST ser guardado criptografado com chave do Android Keystore e MUST ser enviado como `Cookie` apenas nas requisições para `/auth/*`. Tokens, senha e cookies MUST NÃO aparecer em logs nem em mensagens de erro.

#### Scenario: Refresh persistido após login
- **WHEN** o login retorna `Set-Cookie` com o refresh
- **THEN** o refresh fica guardado no armazenamento criptografado do app
- **AND** nenhuma requisição fora de `/auth/*` carrega o cookie de refresh

#### Scenario: Reabrir o app com sessão guardada
- **GIVEN** existe refresh guardado e ainda válido
- **WHEN** o usuário abre o app de novo
- **THEN** o app renova a sessão via `POST /auth/refresh` e abre a aba Tarefas sem pedir login

### Requirement: Renovação transparente do access token
Quando uma chamada autenticada receber 401, o app MUST renovar a sessão uma única vez via `POST /auth/refresh` e repetir a chamada original. Renovações concorrentes MUST ser serializadas, para que um único refresh rotacionado seja usado por vez.

#### Scenario: Access expirado durante o uso
- **GIVEN** o access token expirou
- **WHEN** o app faz uma chamada autenticada e recebe 401
- **THEN** o app renova a sessão, guarda o novo refresh e repete a chamada com sucesso

#### Scenario: Duas chamadas recebem 401 ao mesmo tempo
- **WHEN** duas chamadas autenticadas recebem 401 simultaneamente
- **THEN** só um `POST /auth/refresh` é enviado
- **AND** as duas chamadas são repetidas com o novo access token

### Requirement: Sessão expirada ou revogada
Se `POST /auth/refresh` falhar com 401, o app MUST apagar a sessão guardada e voltar para o login com mensagem genérica de sessão expirada, sem citar reuso de token.

#### Scenario: Refresh rejeitado
- **GIVEN** o refresh guardado foi revogado ou expirou
- **WHEN** a renovação recebe 401
- **THEN** o refresh guardado é apagado
- **AND** o app mostra a tela de login com "Sua sessão expirou. Entre novamente."

### Requirement: Logout
O app MUST oferecer Sair, que chama `POST /auth/logout` com o access e o cookie de refresh atuais e então apaga a sessão local. Mesmo se a chamada de logout falhar, a sessão local MUST ser apagada.

#### Scenario: Sair com rede
- **WHEN** o usuário toca em Sair
- **THEN** o app chama `POST /auth/logout`, apaga a sessão local e mostra o login

#### Scenario: Sair sem rede
- **GIVEN** o aparelho está sem conexão
- **WHEN** o usuário toca em Sair
- **THEN** a sessão local é apagada e o app mostra o login

### Requirement: URL da API por ambiente no build
A URL da API MUST ser definida no build por ambiente (`local` ou `prod`). O build release MUST falhar se a URL não for HTTPS. Tráfego HTTP sem TLS MUST ser aceito só no build debug e só para hosts locais declarados.

#### Scenario: Release com URL HTTP
- **WHEN** alguém gera o build release com uma URL `http://`
- **THEN** o build falha com mensagem explicando que release exige HTTPS

#### Scenario: Debug contra API local no emulador
- **GIVEN** o ambiente é `local`, apontando para o host do emulador
- **WHEN** o app debug faz login
- **THEN** a chamada HTTP local é permitida
