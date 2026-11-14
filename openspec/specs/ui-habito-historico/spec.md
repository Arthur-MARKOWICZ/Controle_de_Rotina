# ui-habito-historico Specification

## Purpose

Mostrar ao usuário, no detalhe de cada hábito, os dias em que ele foi feito, quanto tempo foi dedicado e se a meta foi atingida, e permitir informar os minutos ao concluir um hábito direto.

## Requirements

### Requirement: Seção Histórico no detalhe do hábito
No drawer de edição do hábito (`/habitos/:id`), o sistema MUST exibir uma seção "Histórico" com os cumprimentos dos últimos 84 dias, do mais recente para o mais antigo. A seção MUST NOT aparecer no modo de criação.

#### Scenario: Hábito com cumprimentos
- **GIVEN** um hábito com cumprimentos no período
- **WHEN** o usuário abre `/habitos/:id`
- **THEN** a seção Histórico lista uma linha por dia com a data, os minutos dedicados e a meta (ex.: `25 / 30 min`)

#### Scenario: Hábito sem cumprimentos
- **GIVEN** um hábito sem cumprimentos no período
- **WHEN** o usuário abre `/habitos/:id`
- **THEN** a seção mostra o estado vazio "Nenhum cumprimento nos últimos 84 dias."

#### Scenario: Modo criação
- **WHEN** o usuário abre `/habitos/nova`
- **THEN** a seção Histórico não é exibida e o histórico não é consultado

### Requirement: Sinalização de tempo suficiente
Cada linha do histórico MUST indicar por texto, e não só por cor, se o tempo foi suficiente. Linha insuficiente MUST mostrar quantos minutos faltaram.

#### Scenario: Tempo suficiente
- **GIVEN** um dia com `suficiente` verdadeiro
- **WHEN** a linha é exibida
- **THEN** aparece o rótulo "Meta atingida"

#### Scenario: Tempo insuficiente
- **GIVEN** um dia com 25 de 30 minutos
- **WHEN** a linha é exibida
- **THEN** aparece o rótulo "Faltaram 5 min"

#### Scenario: Concluir direto antigo sem tempo
- **GIVEN** um dia com `origem` `TIMER` e `minutosDedicados` 0 (concluído direto antes desta mudança)
- **WHEN** a linha é exibida
- **THEN** aparece "Tempo não registrado" no lugar de minutos e de "Faltaram"

### Requirement: Erro ao carregar o histórico
Se a consulta do histórico falhar, a seção MUST exibir mensagem de erro com opção de tentar de novo, sem afetar o formulário de edição do hábito.

#### Scenario: Falha do servidor
- **GIVEN** a consulta do histórico responde 500
- **WHEN** o usuário abre `/habitos/:id`
- **THEN** a seção mostra "Não foi possível carregar o histórico." e o botão "Tentar de novo"
- **AND** o formulário de edição continua utilizável

#### Scenario: Hábito não encontrado
- **GIVEN** a consulta do histórico responde 404
- **WHEN** a seção é carregada
- **THEN** a seção mostra a mensagem de erro do servidor "Hábito não encontrado"

#### Scenario: Resposta inválida do proxy
- **GIVEN** a consulta retorna HTML em vez de JSON
- **WHEN** a seção é carregada
- **THEN** a seção mostra a mensagem de erro e não quebra a página

### Requirement: Minutos no concluir direto de hábito
Toda ação "Concluir direto" de hábito na web (card do hábito, lista de hábitos do dia, bloco de hábito na Home) MUST abrir um diálogo de minutos pré-preenchido com a duração do hábito antes de enviar. O valor confirmado MUST ser enviado como `minutos`.

#### Scenario: Confirmar sem alterar
- **GIVEN** um hábito de 30 min
- **WHEN** o usuário clica em Concluir e confirma o diálogo sem mudar o valor
- **THEN** a conclusão é enviada com `minutos` 30

#### Scenario: Informar outro valor
- **WHEN** o usuário troca o valor para 20 e confirma
- **THEN** a conclusão é enviada com `minutos` 20

#### Scenario: Cancelar
- **WHEN** o usuário fecha o diálogo sem confirmar
- **THEN** nenhuma conclusão é enviada e o hábito continua pendente

#### Scenario: Valor inválido
- **WHEN** o usuário informa 0, vazio ou mais de 1440 e tenta confirmar
- **THEN** o diálogo mostra "Informe entre 1 e 1440 minutos." e não envia

#### Scenario: Servidor rejeita os minutos
- **GIVEN** o servidor responde 400 "Minutos informados inválidos"
- **WHEN** o usuário confirma
- **THEN** a mensagem de erro é exibida e o hábito continua pendente
