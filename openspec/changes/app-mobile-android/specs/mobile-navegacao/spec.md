# Spec Delta

## Purpose

Define a navegação do app Android: qual aba abre primeiro, quais abas existem e onde fica o Perfil.

## ADDED Requirements

### Requirement: Tarefas é a aba inicial
Depois do login, e ao reabrir o app com sessão restaurada, o app MUST abrir na aba Tarefas. A aba escolhida MUST sobreviver à rotação de tela.

#### Scenario: Entrar no app
- **GIVEN** o usuário acabou de fazer login
- **WHEN** a área autenticada aparece
- **THEN** a aba selecionada é Tarefas

#### Scenario: Reabrir com sessão guardada
- **GIVEN** existe sessão guardada e válida
- **WHEN** o usuário abre o app
- **THEN** a aba selecionada é Tarefas

### Requirement: Abas da navegação inferior
A barra inferior MUST ter, nesta ordem: Tarefas, Plano, Hábitos, Projetos e Decidir. O plano do dia MUST ficar só na aba Plano, e nenhuma outra aba MUST mostrá-lo.

#### Scenario: Ordem das abas
- **WHEN** o usuário vê a barra inferior
- **THEN** as abas aparecem como Tarefas, Plano, Hábitos, Projetos, Decidir

#### Scenario: Abrir o plano
- **GIVEN** o usuário está na aba Tarefas
- **WHEN** toca em Plano
- **THEN** a aba Plano mostra o plano do dia (spec `mobile-hoje`)

### Requirement: Perfil fora da barra inferior
O Perfil (e-mail e Sair) MUST ser aberto por um ícone na barra superior, visível em todas as abas, e MUST NÃO ocupar lugar na barra inferior.

#### Scenario: Abrir o perfil
- **WHEN** o usuário toca no ícone de perfil na barra superior
- **THEN** a tela de Perfil abre com o e-mail e a ação Sair
- **AND** voltar retorna para a aba de onde veio
