# ui-tarefa-sessao Specification

## Purpose

Define a interface para iniciar e concluir sessões de tarefa no Home e na lista, incluindo escolha de duração e acompanhamento do timer em qualquer rota.

## Requirements

### Requirement: CTAs de tarefa no Home
Na lista de blocos do plano `APROVADO`, cada bloco de tarefa não concluído MUST exibir **Iniciar** quando não estiver em andamento, e **Concluir** (direto) junto ao Iniciar quando o status efetivo permitir conclusão direta; com execução `EM_ANDAMENTO`, as ações de concluir/abortar ficam na barra de sessão ativa.

#### Scenario: Bloco de tarefa não iniciado
- **GIVEN** o plano do dia está aprovado e o bloco de tarefa está `NAO_INICIADO` ou `ABORTADO`
- **WHEN** o usuário vê o card do bloco
- **THEN** aparecem os botões Iniciar e Concluir
- **AND** Concluir dispara conclusão direta sem pedir duração

#### Scenario: Bloco de hábito inalterado
- **GIVEN** um bloco de hábito no mesmo plano
- **WHEN** o usuário vê o card
- **THEN** os CTAs de hábito existentes (Iniciar / Concluir direto) permanecem disponíveis

### Requirement: CTAs na lista de tarefas
Em `/tarefas`, cards de tarefas elegíveis (não deletadas, com restante > 0, status que permita progresso, **sem filhos não deletados**) MUST oferecer Iniciar e Concluir. Card de pai com filhos MUST NÃO oferecer essas ações de sessão.

#### Scenario: Tarefa com restante
- **GIVEN** uma tarefa pendente ou em progresso com restante maior que zero e sem filhos não deletados
- **WHEN** o usuário vê o card na lista
- **THEN** há ações Iniciar e Concluir

#### Scenario: Tarefa sem restante ou finalizada
- **GIVEN** uma tarefa finalizada ou com restante zero
- **WHEN** o usuário vê o card na lista
- **THEN** Iniciar e Concluir de sessão não são oferecidos

#### Scenario: Pai com filhos na lista
- **GIVEN** uma tarefa com filhos não deletados
- **WHEN** o usuário vê o card do pai na lista
- **THEN** Iniciar e Concluir de sessão não são oferecidos

### Requirement: Picker de duração da sessão
Antes de Iniciar ou Concluir pela lista, quando a ação for criar sessão avulsa, a UI MUST pedir a duração com presets 15, 25, 30, 45 e 60 minutos, opção Restante, e input customizado; a escolha enviada MUST respeitar piso 1 e teto = restante.

#### Scenario: Abrir picker ao iniciar pela lista
- **GIVEN** a tarefa não tem bloco aberto no plano aprovado de hoje e tem restante 80
- **WHEN** o usuário escolhe Iniciar
- **THEN** um picker de duração é exibido com presets e Restante
- **AND** confirmar com 30 envia iniciar com 30 minutos

#### Scenario: Reuso de bloco do plano pula o picker
- **GIVEN** a tarefa já tem bloco aberto no plano aprovado de hoje
- **WHEN** o usuário escolhe Iniciar ou Concluir na lista
- **THEN** a ação segue sem pedir duração

### Requirement: Barra global de sessão ativa
Enquanto existir execução `EM_ANDAMENTO` do usuário (tarefa ou hábito) relevante para o dia corrente, o AppShell MUST exibir uma barra de sessão com título, tempo restante (ou excedido), e ações Concluir e Abortar, visível em qualquer rota autenticada da app.

#### Scenario: Iniciar na lista e navegar
- **GIVEN** o usuário iniciou uma sessão pela lista de tarefas
- **WHEN** navega para outra rota autenticada
- **THEN** a barra de sessão permanece visível com Concluir e Abortar

#### Scenario: Sem sessão em andamento
- **GIVEN** não há execução `EM_ANDAMENTO`
- **WHEN** o usuário navega pela app
- **THEN** a barra de sessão não é exibida

### Requirement: Feedback de conclusão
Ao concluir (timer ou direto), a UI MUST mostrar feedback de sucesso e, quando houver ganho de XP no payload, refletir o ganho (toast ou equivalente já usado no Home).

#### Scenario: Concluir com XP
- **WHEN** a conclusão retorna ganho de XP maior que zero
- **THEN** o usuário vê feedback incluindo o XP ganho
