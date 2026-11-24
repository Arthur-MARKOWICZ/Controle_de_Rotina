# Spec Delta

## MODIFIED Requirements

### Requirement: Card de objetivo
Cada objetivo MUST aparecer como card com título, descrição truncada (quando houver) e o progresso do objetivo: a barra de progresso quando houver progresso, ou o convite "Vincule tarefas, hábitos ou projetos para medir o progresso." quando não houver, sem barra e sem 0%. O título MUST ser um link para a página de detalhe do objetivo. Objetivo ativo MUST mostrar o prazo em data local (ou nada, se não tiver prazo); concluído MUST mostrar a data de conclusão; arquivado MUST ficar visualmente atenuado, inclusive a barra.

#### Scenario: Ativo com prazo futuro
- **GIVEN** hoje é 17/11/2026 em BRT e um objetivo ativo com prazo 20/11/2026
- **WHEN** a lista é renderizada
- **THEN** o card mostra o prazo em formato local e quantos dias faltam

#### Scenario: Ativo sem prazo
- **GIVEN** um objetivo ativo sem prazo
- **WHEN** a lista é renderizada
- **THEN** o card não mostra linha de prazo

#### Scenario: Concluído mostra data de conclusão
- **GIVEN** um objetivo concluído em 12/11/2026
- **WHEN** a visão Concluídos é renderizada
- **THEN** o card mostra que foi concluído em 12/11/2026 e não mostra prazo

#### Scenario: Card com progresso
- **GIVEN** um objetivo ativo com progresso de 40%, 120 de 300 minutos
- **WHEN** a lista é renderizada
- **THEN** o card mostra uma barra de progresso em 40% e o texto "2h de 5h concluídas"

#### Scenario: Progresso ainda indisponível
- **GIVEN** um objetivo cujo progresso vem nulo da API
- **WHEN** a lista é renderizada
- **THEN** o card mostra "Vincule tarefas, hábitos ou projetos para medir o progresso."
- **AND** não há barra de progresso nem indicação de 0%

#### Scenario: Concluído mantém o progresso congelado
- **GIVEN** um objetivo concluído com progresso de 60%
- **WHEN** a visão Concluídos é renderizada
- **THEN** o card mostra a barra em 60%

#### Scenario: Título abre o detalhe
- **GIVEN** a visão Concluídos na página 2
- **WHEN** o usuário aciona o título de um card
- **THEN** navega para `/objetivos/:id` daquele objetivo
- **AND** o link "Voltar a Objetivos" do detalhe leva de volta à visão Concluídos, página 2

## ADDED Requirements

### Requirement: Indicador de progresso do objetivo
O indicador MUST mostrar o percentual devolvido pela API, sem recalcular, e um texto em tempo no formato "Xh Ymin de Xh Ymin concluídas". Ele MUST ser anunciado como barra de progresso, com valor mínimo 0, máximo 100, valor atual igual ao percentual, o texto em tempo como valor legível e um nome que inclui o título do objetivo. Percentual fora de 0–100 MUST ser limitado a esse intervalo. Progresso nulo ou com total de minutos ≤ 0 MUST contar como "sem progresso".

#### Scenario: Valores anunciados
- **GIVEN** um objetivo "Perder 5 kg" com progresso de 40%, 120 de 300 minutos
- **WHEN** o indicador é renderizado
- **THEN** há uma barra de progresso com valor 40, mínimo 0 e máximo 100
- **AND** o valor legível é "2h de 5h concluídas" e o nome cita "Perder 5 kg"

#### Scenario: Zero por cento
- **GIVEN** um progresso de 0%, 0 de 90 minutos
- **WHEN** o indicador é renderizado
- **THEN** a barra mostra 0% sem preenchimento e o texto "0min de 1h 30min concluídas"

#### Scenario: Cem por cento
- **GIVEN** um progresso de 100%, 300 de 300 minutos
- **WHEN** o indicador é renderizado
- **THEN** a barra está cheia, com valor 100

#### Scenario: Percentual acima de 100 vindo da API
- **GIVEN** a API devolve percentual 130
- **WHEN** o indicador é renderizado
- **THEN** a barra está cheia e o valor anunciado é 100

#### Scenario: Percentual negativo vindo da API
- **GIVEN** a API devolve percentual -5
- **WHEN** o indicador é renderizado
- **THEN** a barra está vazia e o valor anunciado é 0

#### Scenario: Total de minutos zero com objeto presente
- **GIVEN** a API devolve progresso com `minutosTotais` 0
- **WHEN** o indicador é renderizado
- **THEN** é tratado como sem progresso: não há barra nem 0%

### Requirement: Progresso no detalhe do objetivo
A página de detalhe MUST mostrar o indicador de progresso entre o cabeçalho e a seção de itens, em qualquer status. Sem progresso e com itens vinculados carregados, MUST mostrar "Os itens vinculados ainda não somam tempo. Tarefas canceladas e hábitos pausados não contam." no lugar da barra. Sem progresso e sem itens, ou com itens carregando ou em erro, MUST NOT mostrar barra nem texto próprio. Após editar, concluir, arquivar ou reabrir, MUST refletir o progresso da resposta.

#### Scenario: Detalhe com progresso
- **GIVEN** um objetivo ativo com progresso de 80%, 120 de 150 minutos
- **WHEN** o usuário abre o detalhe
- **THEN** a página mostra a barra em 80% e "2h de 2h 30min concluídas" antes da seção de itens

#### Scenario: Itens que não somam tempo
- **GIVEN** um objetivo ligado só a um hábito pausado, com progresso nulo
- **WHEN** o usuário abre o detalhe e os itens carregam
- **THEN** a página mostra "Os itens vinculados ainda não somam tempo. Tarefas canceladas e hábitos pausados não contam."
- **AND** não há barra de progresso

#### Scenario: Objetivo sem itens não repete o convite
- **GIVEN** um objetivo sem vínculos, com progresso nulo
- **WHEN** o usuário abre o detalhe
- **THEN** não há barra nem texto de progresso
- **AND** só a seção de itens mostra o convite de vínculo

#### Scenario: Falha nos itens não inventa explicação
- **GIVEN** um objetivo com progresso nulo e a busca de itens falhando
- **WHEN** o usuário abre o detalhe
- **THEN** não há barra nem texto de progresso
- **AND** a seção de itens mostra o alerta de falha

#### Scenario: Concluir atualiza o progresso
- **GIVEN** o detalhe de um objetivo ativo com progresso de 50%
- **WHEN** o usuário conclui e a API responde o objetivo concluído com progresso de 55%
- **THEN** a barra passa a mostrar 55%
