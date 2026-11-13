# Spec Delta

## Purpose

Conduz o usuário por um ritual semanal curto em `/revisao` — olhar a semana, limpar atrasadas e esperas, esvaziar o Decidir e sair com o plano da próxima semana aprovado ou recusado de forma explícita.

## ADDED Requirements

### Requirement: Rota e acesso à revisão
O sistema MUST oferecer a rota autenticada `/revisao` com o título **Revisão da semana**, acessível pelo menu **Mais** e por um atalho na página `/plano`. Usuário não autenticado MUST seguir o fluxo de login existente.

#### Scenario: Abrir pelo menu Mais
- **GIVEN** o usuário autenticado
- **WHEN** aciona **Revisão da semana** no menu Mais
- **THEN** navega para `/revisao`
- **AND** vê o passo 1

#### Scenario: Abrir pelo atalho do plano
- **GIVEN** o usuário em `/plano`
- **WHEN** aciona o atalho da revisão
- **THEN** navega para `/revisao`

### Requirement: Quatro passos em ordem com progresso
A revisão MUST apresentar quatro passos nesta ordem: **Sua semana**, **Atrasadas e em espera**, **Decidir**, **Plano da próxima semana**. A UI MUST indicar o passo atual e o total (ex.: "Passo 2 de 4") e MUST permitir voltar ao passo anterior sem perder o que já foi feito no servidor.

#### Scenario: Avançar mostra o próximo passo
- **GIVEN** o usuário no passo 1
- **WHEN** aciona **Continuar**
- **THEN** vê o passo 2
- **AND** o indicador mostra "Passo 2 de 4"

#### Scenario: Voltar ao passo anterior
- **GIVEN** o usuário no passo 3
- **WHEN** aciona **Voltar**
- **THEN** vê o passo 2 com os dados atuais do servidor

### Requirement: Todo passo é pulável
Cada passo MUST oferecer **Pular** sem exigir nenhuma ação. Pular MUST NÃO alterar dados no servidor. Pular o passo 4 MUST levar à conclusão registrando que o plano não foi tratado.

#### Scenario: Pular passo sem efeito
- **GIVEN** o usuário no passo 2 com tarefas atrasadas listadas
- **WHEN** aciona **Pular**
- **THEN** vê o passo 3
- **AND** nenhuma tarefa foi alterada

#### Scenario: Pular todos os passos
- **GIVEN** o usuário no passo 1
- **WHEN** aciona **Pular** em todos os quatro passos
- **THEN** vê a tela de conclusão
- **AND** a conclusão informa que o plano da próxima semana não foi gerado

### Requirement: Passo 1 mostra o resumo da semana
O passo 1 MUST exibir o resumo da semana corrente (segunda a domingo, BRT) com tarefas finalizadas, blocos concluídos, minutos trabalhados e XP ganho, a partir do histórico semanal existente. Semana sem atividade MUST mostrar estado vazio sem bloquear o avanço.

#### Scenario: Semana com atividade
- **GIVEN** o usuário finalizou 3 tarefas nesta semana
- **WHEN** abre o passo 1
- **THEN** vê "3" tarefas finalizadas junto aos demais números da semana

#### Scenario: Semana vazia
- **GIVEN** o usuário sem nenhuma atividade na semana
- **WHEN** abre o passo 1
- **THEN** vê um estado vazio
- **AND** **Continuar** permanece disponível

#### Scenario: Domingo à noite em BRT
- **GIVEN** agora é domingo 23:30 em BRT
- **WHEN** abre o passo 1
- **THEN** o resumo é da semana que começou na segunda anterior, não da seguinte

#### Scenario: Falha ao carregar o resumo
- **GIVEN** o histórico semanal responde com erro
- **WHEN** abre o passo 1
- **THEN** vê mensagem de erro com opção de tentar de novo
- **AND** pode pular o passo

### Requirement: Passo 2 lista atrasadas e tarefas em espera
O passo 2 MUST listar, em grupos separados, as tarefas `ATRASADA` e as tarefas bloqueadas por espera do usuário (não deletadas). Para tarefas bloqueadas MUST mostrar o alvo da espera de forma resumida. Sem itens nos dois grupos, MUST mostrar estado vazio positivo.

#### Scenario: Grupos separados
- **GIVEN** uma tarefa `ATRASADA` e uma tarefa bloqueada por espera
- **WHEN** abre o passo 2
- **THEN** vê a primeira no grupo **Atrasadas** e a segunda no grupo **Em espera**, com o título do alvo

#### Scenario: Nada pendente
- **GIVEN** o usuário sem tarefas atrasadas nem bloqueadas
- **WHEN** abre o passo 2
- **THEN** vê um estado vazio indicando que não há o que revisar

### Requirement: Ações rápidas sobre atrasadas
Para cada tarefa atrasada o passo 2 MUST oferecer **Reagendar** (novo prazo futuro) e **Descartar** (status `CANCELADA`, com confirmação). Após sucesso, a tarefa MUST sair do grupo. Em erro, a tarefa MUST permanecer como estava, com a mensagem do servidor visível.

#### Scenario: Reagendar remove do grupo
- **GIVEN** uma tarefa `ATRASADA` no passo 2
- **WHEN** o usuário reagenda para a próxima sexta e confirma
- **THEN** a tarefa sai do grupo **Atrasadas**
- **AND** passa a ter o novo prazo e deixa de estar atrasada

#### Scenario: Descartar exige confirmação
- **GIVEN** uma tarefa `ATRASADA` no passo 2
- **WHEN** o usuário aciona **Descartar** e cancela a confirmação
- **THEN** a tarefa continua no grupo sem alteração

#### Scenario: Prazo no passado recusado
- **GIVEN** uma tarefa `ATRASADA` no passo 2
- **WHEN** o usuário tenta reagendar para uma data já passada
- **THEN** vê a mensagem de prazo inválido
- **AND** a tarefa continua no grupo **Atrasadas**

#### Scenario: Tarefa removida em outra aba
- **GIVEN** uma tarefa listada no passo 2 que foi deletada em outra aba
- **WHEN** o usuário tenta reagendá-la
- **THEN** vê que a tarefa não foi encontrada
- **AND** ela sai da lista após recarregar

### Requirement: Ações rápidas sobre tarefas em espera
Para cada tarefa em espera o passo 2 MUST oferecer **Remover espera** (com confirmação) e **Descartar**. Remover a última espera irresolvida MUST tirar a tarefa do grupo. Tarefa bloqueada por pai ou projeto MUST indicar onde a espera está, sem oferecer remover o que não é dela.

#### Scenario: Remover espera desbloqueia
- **GIVEN** uma tarefa com uma única espera irresolvida
- **WHEN** o usuário remove a espera e confirma
- **THEN** a tarefa sai do grupo **Em espera**

#### Scenario: Bloqueio herdado do projeto
- **GIVEN** uma tarefa bloqueada porque o projeto dela espera outro recurso
- **WHEN** abre o passo 2
- **THEN** a tarefa aparece em **Em espera** indicando o projeto
- **AND** não há ação de remover espera na própria tarefa

### Requirement: Passo 3 esvazia o Decidir
O passo 3 MUST listar os itens abertos do Decidir e oferecer **Promover** (com o fluxo de promoção existente) e **Descartar** (com confirmação). Item tratado MUST sair da lista. Fila vazia MUST mostrar estado vazio positivo. Itens com prazo de decisão vencido MUST ter o destaque existente.

#### Scenario: Promover tira da lista
- **GIVEN** dois itens abertos no passo 3
- **WHEN** o usuário promove um deles
- **THEN** resta um item na lista
- **AND** a tarefa criada passa a existir em `/tarefas`

#### Scenario: Fila já vazia
- **GIVEN** o usuário sem itens abertos no Decidir
- **WHEN** abre o passo 3
- **THEN** vê o estado vazio e pode continuar

#### Scenario: Item já promovido em outra aba
- **GIVEN** um item listado que já foi promovido em outra aba
- **WHEN** o usuário tenta promovê-lo
- **THEN** vê a mensagem de conflito do servidor
- **AND** nenhuma tarefa nova é criada pela revisão

### Requirement: Passo 4 gera a proposta da próxima semana
O passo 4 MUST gerar a proposta a partir da próxima segunda-feira (BRT) usando o planner existente e exibir os dias propostos com seus blocos, conflitos e itens não alocados. Se a próxima segunda já tiver plano aprovado, MUST informar isso e oferecer ir a `/plano`, sem gerar.

#### Scenario: Gerar proposta
- **GIVEN** a próxima segunda sem plano aprovado
- **WHEN** o usuário aciona **Gerar plano** no passo 4
- **THEN** vê os dias propostos a partir da próxima segunda com seus blocos

#### Scenario: Revisão feita no domingo
- **GIVEN** hoje é domingo em BRT
- **WHEN** gera a proposta no passo 4
- **THEN** a proposta começa na segunda-feira de amanhã

#### Scenario: Semana já aprovada
- **GIVEN** a próxima segunda já tem plano aprovado
- **WHEN** o usuário aciona **Gerar plano**
- **THEN** vê que a semana já tem plano aprovado e o link para `/plano`
- **AND** nenhum plano aprovado é alterado

#### Scenario: Sem capacidade na janela
- **GIVEN** capacidade zero em todos os dias da janela
- **WHEN** gera a proposta
- **THEN** vê a mensagem do planner de falta de tempo e os itens não alocados

### Requirement: Desfecho explícito do plano
Com a proposta exibida, o passo 4 MUST oferecer **Aprovar plano** e **Não aprovar agora**. Aprovar MUST aceitar todos os dias da proposta; não aprovar MUST descartá-la. Ambos MUST levar à conclusão com o desfecho. Avançar sem escolher MUST NÃO ser possível, exceto por **Pular**.

#### Scenario: Aprovar
- **GIVEN** uma proposta exibida no passo 4
- **WHEN** o usuário aciona **Aprovar plano**
- **THEN** os dias da proposta ficam aprovados
- **AND** a conclusão informa "plano da semana aprovado"

#### Scenario: Não aprovar
- **GIVEN** uma proposta exibida no passo 4
- **WHEN** o usuário aciona **Não aprovar agora**
- **THEN** a proposta é descartada
- **AND** a conclusão informa que o plano não foi aprovado por escolha do usuário

#### Scenario: Clique duplo em aprovar
- **GIVEN** uma proposta exibida no passo 4
- **WHEN** o usuário aciona **Aprovar plano** duas vezes seguidas
- **THEN** só uma aprovação é enviada
- **AND** o botão fica desabilitado durante a requisição

#### Scenario: Proposta já resolvida em outra aba
- **GIVEN** a proposta exibida foi aceita ou descartada em outra aba
- **WHEN** o usuário aciona **Aprovar plano**
- **THEN** vê a mensagem de proposta inválida do servidor
- **AND** pode gerar de novo

### Requirement: Tela de conclusão
Ao terminar ou pular o passo 4, a revisão MUST mostrar uma conclusão com o que foi feito na sessão (quantas tarefas tratadas no passo 2, itens tratados no passo 3) e o desfecho do plano, com links para `/plano` e `/tarefas`.

#### Scenario: Resumo da sessão
- **GIVEN** o usuário reagendou 2 tarefas, promoveu 1 item e aprovou o plano
- **WHEN** chega à conclusão
- **THEN** vê 2 tarefas tratadas, 1 item decidido e "plano da semana aprovado"

### Requirement: Sessão expirada no meio da revisão
Se a sessão expirar durante a revisão, a UI MUST tentar renovar pelo fluxo de refresh existente e repetir a ação; se a renovação falhar, MUST levar ao login sem aplicar a ação pela metade.

#### Scenario: Token expira entre passos
- **GIVEN** o token de acesso expira enquanto o usuário está no passo 3
- **WHEN** o usuário promove um item
- **THEN** a sessão é renovada e o item é promovido uma única vez
