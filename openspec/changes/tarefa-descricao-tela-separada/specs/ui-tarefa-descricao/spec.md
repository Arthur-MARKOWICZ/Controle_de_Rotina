# Spec Delta

## Purpose

Permite editar a descrição da tarefa numa superfície content-first separada dos metadados, dentro do drawer de criar/editar, com draft local até o salvamento do formulário.

## ADDED Requirements

### Requirement: Entrada de descrição no formulário meta
No formulário meta do drawer de tarefa (criar ou editar), o sistema MUST exibir uma linha acionável de descrição em vez de um campo de texto multilinha inline. Se a descrição estiver vazia, a linha MUST mostrar o rótulo “Adicionar descrição”; se houver texto, MUST mostrar um preview truncado do conteúdo.

#### Scenario: Descrição vazia
- **GIVEN** o drawer de tarefa está na view meta e a descrição está vazia
- **WHEN** o usuário vê o formulário
- **THEN** há uma linha acionável com o texto “Adicionar descrição”
- **AND** não há textarea de descrição inline no formulário meta

#### Scenario: Descrição com conteúdo
- **GIVEN** o drawer de tarefa está na view meta e a descrição tem texto
- **WHEN** o usuário vê o formulário
- **THEN** a linha de descrição mostra um preview truncado desse texto

### Requirement: View aninhada da nota
Ao acionar a linha de descrição, o sistema MUST substituir o conteúdo do drawer pela view da nota no mesmo drawer (sem navegar para outra rota). A view da nota MUST ter título “Descrição”, controlo de voltar ao meta, área de edição de texto em superfície ampla, e ação “Pronto” no rodapé. As ações de salvar/criar/excluir do meta MUST NÃO aparecer nessa view.

#### Scenario: Abrir a nota a partir do meta
- **GIVEN** o drawer está na view meta
- **WHEN** o usuário aciona a linha de descrição
- **THEN** o drawer mostra a view da nota com título “Descrição”
- **AND** a URL permanece em `/tarefas/nova` ou `/tarefas/:id` conforme o modo
- **AND** o foco inicial vai para a área de edição da descrição

#### Scenario: Confirmar com Pronto
- **GIVEN** o usuário está na view da nota e alterou o texto
- **WHEN** escolhe “Pronto”
- **THEN** o drawer volta à view meta
- **AND** a linha de descrição reflete o texto editado (preview ou “Adicionar descrição”)
- **AND** nenhuma requisição de persistência da tarefa é enviada só por essa ação

### Requirement: Navegação de volta na view da nota
Na view da nota, Esc, o controlo de fechar (X) do header e o controlo de voltar MUST retornar à view meta sem fechar o drawer. Só na view meta Esc, X, backdrop e Cancelar MUST pedir o fechamento do drawer (com confirmação se houver alterações).

#### Scenario: Esc na nota volta ao meta
- **GIVEN** o drawer está na view da nota com alterações locais na descrição
- **WHEN** o usuário pressiona Esc
- **THEN** o drawer volta à view meta e permanece aberto
- **AND** as alterações da descrição permanecem no formulário

#### Scenario: X na nota volta ao meta
- **GIVEN** o drawer está na view da nota
- **WHEN** o usuário aciona o controlo de fechar do header
- **THEN** o drawer volta à view meta e permanece aberto

#### Scenario: Esc no meta fecha o drawer
- **GIVEN** o drawer está na view meta sem alterações
- **WHEN** o usuário pressiona Esc
- **THEN** o drawer fecha

### Requirement: Draft local até salvar o formulário
Alterações feitas na view da nota MUST atualizar apenas o estado local do formulário do drawer. A persistência da descrição MUST ocorrer somente quando o usuário salva ou cria a tarefa pelo rodapé da view meta, junto com os demais campos.

#### Scenario: Salvar inclui a descrição editada
- **GIVEN** o usuário editou a descrição na view da nota, voltou ao meta e os demais campos são válidos
- **WHEN** salva ou cria a tarefa
- **THEN** a descrição enviada é a versão editada na nota

#### Scenario: Descartar alterações inclui a descrição
- **GIVEN** o usuário alterou só a descrição na view da nota e voltou ao meta
- **WHEN** tenta fechar o drawer
- **THEN** o sistema pede confirmação para descartar alterações
