# ui-habito-sessao Specification

## Purpose

Define o estado de sessão exibido no card de hábito da lista `/habitos` (Iniciar, Concluir direto, Finalizar, Pausar) e garante que esse estado reflita o servidor, inclusive depois de recarregar a página.

## Requirements

### Requirement: Card de hábito reflete a sessão do servidor
Na lista `/habitos`, o card de um hábito elegível hoje MUST exibir Finalizar e Pausar sempre que a sessão ativa do usuário, obtida do servidor, pertencer a esse hábito; caso contrário MUST exibir Iniciar e Concluir. O estado MUST ser o mesmo antes e depois de recarregar a página.

#### Scenario: Recarregar com hábito em andamento
- **GIVEN** o usuário iniciou o hábito "Ler" em `/habitos`
- **WHEN** recarrega a página (F5)
- **THEN** o card de "Ler" mostra Finalizar e Pausar, sem o botão Iniciar
- **AND** a barra global de sessão mostra "Ler" com o tempo contado desde o `iniciadoEm` do servidor

#### Scenario: Outro hábito em andamento
- **GIVEN** a sessão ativa do servidor é do hábito "Meditar"
- **WHEN** o usuário vê o card de "Ler"
- **THEN** o card de "Ler" mostra Iniciar e Concluir

#### Scenario: Sessão encerrada em outro dispositivo
- **GIVEN** o card de "Ler" mostrava Finalizar e a sessão foi concluída no app Android
- **WHEN** o usuário recarrega `/habitos`
- **THEN** o card de "Ler" mostra Iniciar e Concluir e a barra global não aparece

#### Scenario: Falha ao consultar a sessão ativa
- **GIVEN** a consulta da sessão ativa falha (erro 500, timeout ou resposta HTML do proxy)
- **WHEN** a página carrega
- **THEN** os cards ficam no estado Iniciar/Concluir e nenhuma ação é disparada automaticamente

### Requirement: Iniciar hábito já em andamento retoma a sessão na UI
Ao clicar Iniciar num hábito cuja execução já está em andamento no servidor (ex.: outra aba), a UI MUST tratar a resposta como início bem-sucedido: registrar a sessão na barra global com o `iniciadoEm` devolvido e mostrar Finalizar/Pausar no card, sem toast de erro.

#### Scenario: Iniciar em segunda aba
- **GIVEN** o hábito "Ler" foi iniciado em outra aba e esta aba ainda mostra Iniciar
- **WHEN** o usuário clica Iniciar
- **THEN** a barra global aparece com "Ler" e o tempo desde o início original
- **AND** o card mostra Finalizar e Pausar e nenhum toast de erro é exibido

#### Scenario: Iniciar hábito já concluído hoje
- **GIVEN** o hábito "Ler" já foi concluído hoje em outra aba
- **WHEN** o usuário clica Iniciar
- **THEN** a UI exibe a mensagem de erro devolvida pela API e o card continua com Iniciar/Concluir
