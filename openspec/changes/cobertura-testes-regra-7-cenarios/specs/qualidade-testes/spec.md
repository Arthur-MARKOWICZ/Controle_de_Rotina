# Spec Delta

## Purpose

Define a política de qualidade da suíte automatizada do projeto nas quatro stacks (backend, frontend web, MCP e mobile): quantos e quais cenários cada unidade com lógica precisa ter, o que cada teste deve verificar, como a cobertura é medida e como o CI a impõe.

## ADDED Requirements

### Requirement: Regra mínima de cenários por unidade com lógica
Toda unidade com lógica (service, motor, job, validator, filtro, endpoint, hook, componente com comportamento, utilitário, ViewModel) MUST ter, somados todos os arquivos que a testam diretamente, no mínimo 7 cenários: ≥1 caminho feliz, ≥3 anomalias e ≥3 erros esperados. Unidades sem lógica (DTO/record sem regra, tipos, re-exports, config declarativa) MUST ficar fora da regra.

#### Scenario: Unidade nova com lógica
- **GIVEN** um PR que adiciona ou altera uma unidade com lógica
- **WHEN** o PR é revisado
- **THEN** a unidade tem pelo menos 1 teste de caminho feliz, 3 de anomalia e 3 de erro esperado
- **AND** o PR é considerado incompleto se faltar qualquer um dos três tipos

#### Scenario: Unidade sem lógica
- **GIVEN** um record de resposta sem nenhuma regra de mapeamento ou cálculo
- **WHEN** a regra mínima é aplicada
- **THEN** a unidade não é obrigada a ter os 7 cenários

#### Scenario: Cenários distribuídos em mais de um arquivo
- **GIVEN** um service testado por um teste unitário e por um teste de integração do endpoint
- **WHEN** os cenários são contados
- **THEN** a soma dos cenários que exercitam diretamente aquele service conta para o mínimo

### Requirement: Nome do teste identifica o tipo de cenário
O nome de cada teste MUST deixar claro o resultado esperado e a condição, de forma que se identifique se é caminho feliz, anomalia ou erro esperado sem ler o corpo (ex.: `deveRetornar409QuandoPlanoJaAprovado`, `naoDeveConcederXpDuasVezesParaMesmoEvento`).

#### Scenario: Teste de erro com nome descritivo
- **WHEN** um teste verifica que aprovar um plano já aprovado falha
- **THEN** seu nome cita o status/erro esperado e a condição que o provoca

### Requirement: Teste de erro esperado verifica status e identificador do problema
Um teste de erro esperado MUST verificar o status (ou tipo de exceção) **e** o identificador específico do problema — no backend, os campos do ProblemDetail que distinguem o erro (`title`, `type` ou propriedade específica); no front/mobile/MCP, a mensagem ou estado de erro exibido/propagado. Verificar só "deu erro" MUST NÃO bastar.

#### Scenario: Erro de API no backend
- **GIVEN** uma requisição que cria tag com nome já existente
- **WHEN** o teste verifica a resposta
- **THEN** confere o status 409
- **AND** confere o campo do ProblemDetail que identifica o conflito de nome de tag

#### Scenario: Exceção de domínio em teste unitário
- **WHEN** um teste unitário verifica que um evento de XP duplicado é rejeitado
- **THEN** confere o tipo específico da exceção de domínio, não uma exceção genérica

#### Scenario: Erro exibido na UI
- **GIVEN** uma API mockada que responde 409
- **WHEN** o componente processa a resposta
- **THEN** o teste confere a mensagem de erro visível ao usuário

### Requirement: Teste de anomalia verifica que o estado não foi corrompido
Um teste de anomalia (recurso de outro usuário, chamada duplicada/concorrente, item soft-deleted/arquivado, lista vazia, limites como 0/máximo/meia-noite BRT/virada de semana, token expirado no meio do fluxo, resposta vazia/malformada) MUST verificar, além do resultado, que nenhum estado foi alterado indevidamente — banco no backend, estado da tela/cache no cliente.

#### Scenario: Recurso de outro usuário
- **GIVEN** uma tarefa pertencente ao usuário B
- **WHEN** o usuário A tenta alterá-la
- **THEN** a operação falha sem revelar a existência do recurso
- **AND** o teste confere que a tarefa de B permanece inalterada no banco

#### Scenario: Chamada duplicada
- **GIVEN** um evento de XP já concedido
- **WHEN** a concessão é chamada novamente com o mesmo evento
- **THEN** o saldo de XP do usuário não muda

#### Scenario: Resposta malformada no cliente
- **GIVEN** o proxy devolve HTML em vez de JSON
- **WHEN** o cliente da API processa a resposta
- **THEN** o erro é tratado e o estado anterior da tela é preservado

### Requirement: Cobertura medida em todas as stacks
O comando de testes de cada stack MUST poder gerar relatório de cobertura de linhas e branches: backend (`mvn test`), frontend web e MCP (execução de testes com cobertura), mobile (testes JVM do módulo compartilhado). O relatório MUST ser gerado em formato legível por humano (HTML) e por máquina.

#### Scenario: Relatório no backend
- **WHEN** `mvn test` termina com sucesso
- **THEN** existe um relatório de cobertura de linhas e branches do backend

#### Scenario: Relatório no frontend e MCP
- **WHEN** os testes com cobertura são executados em `frontend/frontend-web` ou em `mcp`
- **THEN** é gerado relatório de cobertura de linhas e branches da respectiva stack

#### Scenario: Relatório no mobile
- **WHEN** os testes JVM do módulo compartilhado mobile são executados com cobertura
- **THEN** é gerado relatório de cobertura do módulo compartilhado

### Requirement: Todas as suítes rodam no CI
O CI MUST executar as suítes de backend, frontend web, MCP e mobile e falhar se qualquer teste falhar. A suíte do MCP MUST rodar no mesmo job de validação que antecede o deploy.

#### Scenario: Teste do MCP falha
- **GIVEN** um push para `main` com um teste do MCP quebrado
- **WHEN** o workflow de validação roda
- **THEN** o job de validação falha
- **AND** o deploy não é executado

### Requirement: Relatórios de cobertura publicados no CI
O CI MUST publicar os relatórios de cobertura de cada stack como artefato da execução, inclusive quando os testes falham.

#### Scenario: Artefato disponível
- **WHEN** o workflow de validação termina, com sucesso ou falha
- **THEN** os relatórios de cobertura gerados ficam disponíveis como artefato da execução

### Requirement: Gate mínimo de cobertura
O CI MUST falhar quando a cobertura de uma stack ficar abaixo do limite configurado para ela. O limite MUST nunca ser reduzido abaixo do valor vigente e, ao concluir esta mudança, MUST ser de pelo menos 70% de linhas e 60% de branches em cada stack.

#### Scenario: Cobertura abaixo do limite
- **GIVEN** um limite de 70% de linhas para o backend
- **WHEN** um push leva a cobertura de linhas do backend para 68%
- **THEN** o job de validação falha indicando o limite violado

#### Scenario: Cobertura no limite
- **GIVEN** um limite de 60% de branches para o MCP
- **WHEN** a cobertura de branches do MCP é exatamente 60%
- **THEN** o gate passa

### Requirement: Regra documentada para contribuidores e agentes
A regra "1 caminho feliz + 3 anomalias + 3 erros" e o que cada tipo deve verificar MUST estar documentados em `AGENTS.md` e referenciados nos READMEs de teste de cada stack, junto com o comando para gerar cobertura localmente.

#### Scenario: Agente consulta a regra
- **WHEN** um agente ou contribuidor lê `AGENTS.md`
- **THEN** encontra a regra mínima, exemplos de cada tipo de cenário e os comandos de cobertura por stack
