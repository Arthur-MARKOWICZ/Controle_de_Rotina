# Spec Delta

## Purpose

Exibe no Histórico um cartão com o ritmo real do usuário frente às suas estimativas, geral e por tag/projeto, para que ele ajuste as próximas durações com base no que de fato acontece.

## ADDED Requirements

### Requirement: Cartão de calibração no Histórico
A tela `/historico` MUST exibir o cartão "Seu ritmo real" em ambas as visões (Dia e Semana), carregado uma vez por abertura da tela e independente da navegação de período, com a legenda "últimos 90 dias".

#### Scenario: Razão geral exibida
- **GIVEN** a API devolve `geral` suficiente com `razao = 1.4` e `amostras = 12`
- **WHEN** o usuário abre o Histórico
- **THEN** o cartão mostra "Você costuma levar 1,4× o estimado" e "12 blocos"

#### Scenario: Trocar de semana não recarrega o cartão
- **GIVEN** o cartão já carregado
- **WHEN** o usuário navega para a semana anterior
- **THEN** a calibração não é consultada de novo e o cartão continua visível

### Requirement: Frase conforme a razão
A frase geral MUST usar uma casa decimal com vírgula. Razão entre 0,95 e 1,05 (inclusivos) MUST mostrar "Suas estimativas estão em dia"; acima, "Você costuma levar N× o estimado"; abaixo, "Você costuma terminar em N× do estimado".

#### Scenario: Estimativa precisa
- **GIVEN** `geral.razao = 1.03`
- **WHEN** o cartão renderiza
- **THEN** mostra "Suas estimativas estão em dia"

#### Scenario: Termina antes
- **GIVEN** `geral.razao = 0.8`
- **WHEN** o cartão renderiza
- **THEN** mostra "Você costuma terminar em 0,8× do estimado"

### Requirement: Recortes por tag e projeto
O cartão MUST listar os recortes suficientes de `porTag` (com `#nome` e a cor da tag) e de `porProjeto`, na ordem da API, até 3 de cada, cada um com sua razão no formato "1,4×". Recortes insuficientes MUST NÃO aparecer nessa lista.

#### Scenario: Recorte por tag
- **GIVEN** `porTag` com `faculdade` suficiente em 1.4 e `leitura` insuficiente
- **WHEN** o cartão renderiza
- **THEN** aparece "#faculdade 1,4×" e `leitura` não aparece

#### Scenario: Nenhum recorte suficiente
- **GIVEN** geral suficiente e todos os recortes insuficientes
- **WHEN** o cartão renderiza
- **THEN** só a frase geral aparece, sem lista vazia

### Requirement: Estado sem dados
Quando `geral.suficiente = false`, o cartão MUST mostrar "Ainda sem dados suficientes" e quantos blocos com timer faltam para o mínimo, sem nenhum número de razão.

#### Scenario: Faltam amostras
- **GIVEN** `geral.amostras = 2` e `minimoAmostras = 5`
- **WHEN** o cartão renderiza
- **THEN** mostra "Ainda sem dados suficientes" e "faltam 3 blocos concluídos com timer"
- **AND** não mostra nenhum valor "×"

### Requirement: Carregamento e erro isolados
O cartão MUST mostrar skeleton enquanto carrega. Falha na consulta (status de erro, timeout ou resposta não-JSON do proxy) MUST mostrar alerta inline com a mensagem da API ou "Não foi possível carregar sua calibração." dentro do cartão, sem afetar os resumos de dia/semana.

#### Scenario: Erro 500
- **GIVEN** `GET /historico/calibracao` responde 500
- **WHEN** o usuário abre o Histórico
- **THEN** o cartão mostra o alerta de erro
- **AND** os indicadores do período continuam exibidos

#### Scenario: Proxy devolve HTML
- **GIVEN** a resposta é o `index.html` do SPA
- **WHEN** o cartão tenta ler o JSON
- **THEN** mostra o alerta de erro em vez de quebrar a página
