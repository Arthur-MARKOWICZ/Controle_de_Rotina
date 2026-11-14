# Spec Delta

## Purpose

Lembra o usuário, sem que ele abra o app Android, do plano do dia e das tarefas com prazo no dia seguinte, usando notificações locais com dados consultados na API.

## ADDED Requirements

### Requirement: Preferências de lembrete por tipo
O app SHALL oferecer, em Perfil > Lembretes, dois tipos de lembrete (resumo do dia e prazo amanhã), cada um com liga/desliga e horário próprios. Na primeira instalação os dois MUST vir desligados, com horários sugeridos 08:00 e 19:00. As preferências ficam no aparelho e sobrevivem a reiniciar o app.

#### Scenario: Primeira abertura
- **WHEN** o usuário abre Perfil > Lembretes pela primeira vez
- **THEN** os dois lembretes aparecem desligados, com horários 08:00 (resumo) e 19:00 (prazo amanhã)

#### Scenario: Mudar horário de um lembrete ligado
- **GIVEN** o resumo do dia ligado às 08:00
- **WHEN** o usuário muda o horário para 07:30
- **THEN** o próximo resumo é agendado para 07:30 e o agendamento das 08:00 deixa de existir

#### Scenario: Desligar um tipo não afeta o outro
- **GIVEN** os dois lembretes ligados
- **WHEN** o usuário desliga o prazo amanhã
- **THEN** nenhum aviso de prazo é disparado e o resumo do dia continua agendado

### Requirement: Permissão de notificação pedida com contexto
No Android 13 ou superior, o app SHALL pedir a permissão de notificação só quando o usuário liga um lembrete, com um texto que explica o motivo antes do diálogo do sistema. Recusar MUST deixar o lembrete desligado, mostrar como ativar nas configurações do sistema e não afetar nenhum outro fluxo do app.

#### Scenario: Permissão concedida
- **WHEN** o usuário liga o resumo do dia e concede a permissão
- **THEN** o lembrete fica ligado e agendado

#### Scenario: Permissão recusada
- **WHEN** o usuário liga um lembrete e recusa a permissão
- **THEN** o lembrete volta a desligado, a tela mostra que as notificações estão bloqueadas com atalho para as configurações do sistema, e as abas do app seguem funcionando

#### Scenario: Permissão revogada depois
- **GIVEN** um lembrete ligado
- **WHEN** o usuário revoga a permissão nas configurações do sistema e o horário chega
- **THEN** nenhuma notificação é exibida, nada falha, e a tela de Lembretes mostra que as notificações estão bloqueadas

#### Scenario: Android 12 ou inferior
- **WHEN** o usuário liga um lembrete num aparelho com Android 12 ou inferior
- **THEN** nenhum diálogo de permissão aparece e o lembrete fica ligado

### Requirement: Agendamento diário no fuso do aparelho
Cada lembrete ligado SHALL disparar uma vez por dia, no horário escolhido e no fuso atual do aparelho, com tolerância de atraso do sistema. Se o disparo só puder ocorrer mais de 2 horas depois do horário (aparelho sem rede, em economia de bateria), o app MUST pular aquele dia sem notificar. Reiniciar o aparelho não cancela o agendamento.

#### Scenario: Horário ainda não passou hoje
- **GIVEN** o horário 08:00 e agora 07:00
- **WHEN** o lembrete é ligado
- **THEN** o próximo disparo é hoje às 08:00

#### Scenario: Horário já passou hoje
- **GIVEN** o horário 08:00 e agora 09:15
- **WHEN** o lembrete é ligado
- **THEN** o próximo disparo é amanhã às 08:00, e não sai notificação agora

#### Scenario: Disparo muito atrasado
- **GIVEN** o horário 08:00
- **WHEN** o sistema só consegue executar o lembrete às 10:30 do mesmo dia
- **THEN** nenhuma notificação é exibida e o próximo disparo é amanhã às 08:00

#### Scenario: Virada de horário de verão ou troca de fuso
- **WHEN** o fuso do aparelho muda entre um disparo e outro
- **THEN** o disparo seguinte usa o horário escolhido no fuso novo

#### Scenario: Execução repetida no mesmo dia
- **GIVEN** um resumo já exibido hoje
- **WHEN** o sistema executa o lembrete de novo no mesmo dia
- **THEN** nenhuma segunda notificação do mesmo tipo é exibida

### Requirement: Conteúdo do resumo do dia
No horário do resumo, o app SHALL consultar o plano e as execuções de hoje e notificar conforme o estado: sem plano → convite a gerar; rascunho → convite a aprovar; aprovado → quantidade e tempo dos blocos ainda não concluídos e o título do próximo. Se o plano aprovado não tiver bloco pendente, MUST não notificar.

#### Scenario: Sem plano hoje
- **WHEN** não existe plano para hoje
- **THEN** a notificação diz que o dia ainda não tem plano e convida a gerar

#### Scenario: Plano em rascunho
- **WHEN** o plano de hoje está em rascunho
- **THEN** a notificação diz que há um plano pronto para aprovar

#### Scenario: Plano aprovado com blocos pendentes
- **GIVEN** um plano aprovado com 4 blocos, 1 concluído, e os 3 restantes somando 2h15
- **WHEN** o resumo dispara
- **THEN** a notificação mostra 3 blocos e 2h15 pendentes, e o título do primeiro bloco não iniciado na ordem do plano

#### Scenario: Plano aprovado todo executado
- **GIVEN** um plano aprovado em que todos os blocos estão concluídos ou abortados
- **WHEN** o resumo dispara
- **THEN** nenhuma notificação é exibida

### Requirement: Conteúdo do aviso de prazo amanhã
No horário do D-1, o app SHALL consultar as tarefas abertas (pendente, em progresso) com prazo entre o início e o fim de amanhã, no fuso do aparelho, incluindo subtarefas. Com uma ou mais tarefas, notifica a quantidade e até 3 títulos, os de prazo mais cedo primeiro. Sem tarefas, MUST não notificar.

#### Scenario: Uma tarefa vence amanhã
- **WHEN** existe uma tarefa pendente com prazo amanhã
- **THEN** a notificação mostra o título dela

#### Scenario: Mais de três tarefas
- **WHEN** cinco tarefas abertas vencem amanhã
- **THEN** a notificação diz que 5 tarefas vencem amanhã e lista os 3 títulos de prazo mais cedo

#### Scenario: Nenhuma tarefa amanhã
- **WHEN** nenhuma tarefa aberta tem prazo amanhã
- **THEN** nenhuma notificação é exibida

#### Scenario: Tarefa finalizada ou cancelada não conta
- **WHEN** a única tarefa com prazo amanhã está finalizada ou cancelada
- **THEN** nenhuma notificação é exibida

#### Scenario: Prazo à meia-noite
- **WHEN** uma tarefa tem prazo exatamente às 00:00 de depois de amanhã no fuso do aparelho
- **THEN** ela não entra no aviso de amanhã

### Requirement: Falha silenciosa sem dados confiáveis
O lembrete SHALL usar a sessão já guardada no app, sem pedir login. Se a sessão expirou, não há rede, a API responde erro ou a resposta é inválida, o app MUST não exibir notificação (nem de erro). Em falha de rede ou da API, MUST manter o agendamento do dia seguinte; sessão expirada segue o requisito "Logout encerra os lembretes".

#### Scenario: Sem rede no horário
- **WHEN** o lembrete dispara sem conexão e a conexão não volta dentro da tolerância
- **THEN** nenhuma notificação é exibida e o próximo disparo segue agendado

#### Scenario: Sessão expirada
- **WHEN** o lembrete dispara e o refresh guardado não vale mais
- **THEN** nenhuma notificação é exibida, os lembretes deixam de ser agendados até o próximo login, e ao abrir o app o usuário vê a tela de login

#### Scenario: Erro da API
- **WHEN** a API responde 500 ou um corpo que não é JSON válido
- **THEN** nenhuma notificação é exibida e o próximo disparo segue agendado

#### Scenario: App aberto durante o disparo
- **GIVEN** o app em primeiro plano renovando a sessão
- **WHEN** o lembrete dispara ao mesmo tempo
- **THEN** a sessão continua válida nos dois e o usuário não é deslogado

### Requirement: Privacidade na tela bloqueada
Notificações de lembrete SHALL esconder títulos de tarefa e bloco na tela bloqueada, mostrando só um texto genérico ("Você tem um lembrete do Rotina") até o aparelho ser desbloqueado.

#### Scenario: Aparelho bloqueado
- **WHEN** um aviso de prazo chega com o aparelho bloqueado
- **THEN** a tela bloqueada mostra o texto genérico, sem títulos

### Requirement: Abrir o app pela notificação
Tocar numa notificação de lembrete SHALL abrir o app na aba correspondente: Plano para o resumo do dia, Tarefas para o prazo amanhã. Sem sessão válida, abre o login.

#### Scenario: Tocar no resumo
- **WHEN** o usuário toca na notificação de resumo com sessão válida
- **THEN** o app abre na aba Plano com o plano de hoje recarregado

#### Scenario: Tocar no aviso de prazo
- **WHEN** o usuário toca na notificação de prazo amanhã com sessão válida
- **THEN** o app abre na aba Tarefas

#### Scenario: Tocar sem sessão
- **WHEN** o usuário toca numa notificação e a sessão expirou
- **THEN** o app abre na tela de login

### Requirement: Logout encerra os lembretes
Ao sair (logout ou sessão expirada no app), o app SHALL cancelar os lembretes agendados e remover as notificações de lembrete visíveis. As preferências continuam salvas no aparelho, e o próximo login reagenda os lembretes ligados.

#### Scenario: Logout com lembretes ligados
- **GIVEN** os dois lembretes ligados e um aviso de prazo visível
- **WHEN** o usuário sai
- **THEN** o aviso some da bandeja e nenhum lembrete dispara até um novo login

#### Scenario: Novo login
- **GIVEN** preferências com o resumo ligado às 08:00
- **WHEN** o usuário entra de novo
- **THEN** o resumo volta a ser agendado para o próximo 08:00
