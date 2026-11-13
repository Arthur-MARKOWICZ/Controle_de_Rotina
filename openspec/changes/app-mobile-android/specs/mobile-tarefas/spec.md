# Spec Delta

## Purpose

Define a tela Tarefas do app Android: listar tarefas abertas, criar tarefa rápida e iniciar ou concluir sessão avulsa, com as mesmas regras de elegibilidade da web.

## ADDED Requirements

### Requirement: Lista de tarefas abertas
A tela Tarefas MUST listar as tarefas não deletadas do usuário via `GET /tarefas`, paginando conforme a rolagem, com título, prioridade, prazo, restante em minutos e indicação de bloqueada. A ordem MUST seguir a que a API devolve.

#### Scenario: Abrir a lista
- **GIVEN** o usuário tem tarefas abertas
- **WHEN** abre a tela Tarefas
- **THEN** as tarefas aparecem com título, prazo e restante

#### Scenario: Rolar até o fim da página
- **GIVEN** existem mais tarefas que o tamanho da página
- **WHEN** o usuário rola até o fim da lista
- **THEN** a próxima página é carregada e anexada

#### Scenario: Lista vazia
- **GIVEN** o usuário não tem tarefas abertas
- **WHEN** abre a tela Tarefas
- **THEN** aparece um estado vazio com a ação Nova tarefa

### Requirement: Quick add de tarefa
O app MUST oferecer criação rápida com título obrigatório (até 120 caracteres) e duração em minutos e prazo opcionais, enviando `POST /tarefas`. Erros de validação da API (Problem Details) MUST aparecer junto ao campo correspondente.

#### Scenario: Criar só com título
- **WHEN** o usuário informa só o título e confirma
- **THEN** a tarefa é criada e aparece na lista sem recarregar a tela inteira

#### Scenario: Título vazio
- **WHEN** o usuário tenta confirmar sem título
- **THEN** o app não envia a requisição e marca o campo título como obrigatório

### Requirement: Elegibilidade para sessão avulsa
Um card de tarefa MUST oferecer Iniciar e Concluir só quando a tarefa não estiver deletada nem `bloqueada`, tiver `restanteMinutos` maior que zero, status que permita progresso e `temFilhos` falso. Nos demais casos, essas ações MUST NÃO aparecer.

#### Scenario: Tarefa elegível
- **GIVEN** uma tarefa pendente, com restante 60, sem filhos e não bloqueada
- **WHEN** o usuário vê o card
- **THEN** há as ações Iniciar e Concluir

#### Scenario: Tarefa bloqueada por espera
- **GIVEN** uma tarefa com `bloqueada` verdadeiro
- **WHEN** o usuário vê o card
- **THEN** Iniciar e Concluir não aparecem e o card mostra "Bloqueada"

#### Scenario: Tarefa pai com filhos
- **GIVEN** uma tarefa com `temFilhos` verdadeiro
- **WHEN** o usuário vê o card
- **THEN** Iniciar e Concluir não aparecem

### Requirement: Picker de duração da sessão avulsa
Ao Iniciar ou Concluir pela lista, o app MUST pedir a duração com presets de 15, 25, 30, 45 e 60 minutos, a opção Restante e um valor customizado. O valor enviado MUST ficar entre 1 e o restante, e presets acima do restante MUST ficar desabilitados.

#### Scenario: Iniciar com 30 minutos
- **GIVEN** a tarefa tem restante 80
- **WHEN** o usuário toca em Iniciar e escolhe 30
- **THEN** o app chama `POST /tarefas/{id}/iniciar` com `duracaoMinutos` 30
- **AND** a barra de sessão ativa aparece

#### Scenario: Concluir direto com Restante
- **GIVEN** a tarefa tem restante 20
- **WHEN** o usuário toca em Concluir e escolhe Restante
- **THEN** o app chama `POST /tarefas/{id}/concluir-direto` com `duracaoMinutos` 20 e mostra o feedback de XP

### Requirement: Recusa da API na sessão
Se a API recusar iniciar ou concluir (por exemplo, tarefa bloqueada ou sem restante), o app MUST mostrar a mensagem do Problem Details e recarregar o card afetado.

#### Scenario: Tarefa ficou bloqueada no meio do caminho
- **GIVEN** o card ainda mostra Iniciar, mas a tarefa foi bloqueada em outro dispositivo
- **WHEN** o usuário toca em Iniciar e a API responde com erro
- **THEN** o app mostra a mensagem de erro e o card passa a mostrar "Bloqueada"

### Requirement: Ações do card alinhadas à esquerda
As ações de um card de tarefa (Iniciar, Concluir e demais) MUST ficar alinhadas à esquerda do card, na mesma posição em todos os cards, qualquer que seja o tamanho do título ou das etiquetas.

#### Scenario: Títulos de tamanhos diferentes
- **GIVEN** duas tarefas elegíveis, uma com título curto e outra com título de duas linhas
- **WHEN** a lista é exibida
- **THEN** o botão Iniciar das duas começa na mesma margem esquerda do card

### Requirement: Editar tarefa
Tocar num card MUST abrir o detalhe da tarefa (`GET /tarefas/{id}`) com título, descrição, prioridade, prazo, duração e projeto editáveis. Salvar MUST chamar `PATCH /tarefas/{id}` só com os campos alterados, e erros de Problem Details MUST aparecer junto ao campo. Um prazo existente MUST poder ser trocado mas não removido, porque a API trata `prazo: null` como "manter". Em tarefa com filhos, a duração MUST ser só leitura (soma dos filhos).

#### Scenario: Mudar o título
- **GIVEN** o detalhe de uma tarefa aberta
- **WHEN** o usuário muda o título e salva
- **THEN** o app envia só `titulo` no `PATCH`
- **AND** o card na lista mostra o novo título

#### Scenario: Trocar o prazo
- **GIVEN** uma tarefa com prazo
- **WHEN** o usuário escolhe outra data e salva
- **THEN** o app envia só `prazo` com a nova data
- **AND** o detalhe não oferece remover o prazo

#### Scenario: Título vazio na edição
- **WHEN** o usuário apaga o título e tenta salvar
- **THEN** o app não envia a requisição e marca o campo título

#### Scenario: Duração de tarefa pai
- **GIVEN** uma tarefa com `temFilhos` verdadeiro
- **WHEN** o usuário abre o detalhe
- **THEN** o campo duração não é editável e mostra a soma dos filhos

#### Scenario: Sair sem salvar
- **GIVEN** o usuário alterou campos no detalhe
- **WHEN** fecha o detalhe sem salvar
- **THEN** o app pede confirmação antes de descartar as alterações

### Requirement: Lista em árvore de subtarefas
A lista de tarefas MUST mostrar só tarefas-raiz (`somenteRaizes=true`). Um card com `temFilhos` verdadeiro MUST ter um controle de expandir, recolhido por padrão, que carrega as subtarefas via `GET /tarefas?parentId=` e as mostra aninhadas sob o pai. Cada subtarefa aninhada MUST ter as mesmas ações de sessão e edição de uma tarefa comum.

#### Scenario: Pai recolhido
- **GIVEN** uma tarefa-raiz com duas subtarefas
- **WHEN** a lista é exibida
- **THEN** aparece só o card do pai, com o controle de expandir

#### Scenario: Expandir o pai
- **WHEN** o usuário toca em expandir no card do pai
- **THEN** as subtarefas abertas aparecem aninhadas sob o pai
- **AND** as subtarefas elegíveis oferecem Iniciar e Concluir

### Requirement: Criar subtarefa
O detalhe de uma tarefa-raiz MUST ter uma seção Subtarefas que lista os filhos e oferece adicionar subtarefa (título obrigatório, duração opcional), enviando `POST /tarefas` com `parentId` do pai. O detalhe de uma subtarefa MUST NÃO oferecer adicionar subtarefa, porque só existe um nível. Recusas da API (por exemplo, pai com bloco concluído) MUST aparecer como mensagem do Problem Details.

#### Scenario: Adicionar subtarefa
- **GIVEN** o detalhe de uma tarefa-raiz aberto
- **WHEN** o usuário adiciona a subtarefa "Comprar tinta"
- **THEN** o app envia `POST /tarefas` com `parentId` do pai
- **AND** "Comprar tinta" aparece na seção Subtarefas e o pai passa a ter `temFilhos`

#### Scenario: Detalhe de subtarefa
- **GIVEN** o detalhe de uma tarefa com `parentId` preenchido
- **WHEN** o usuário vê o detalhe
- **THEN** não há a ação de adicionar subtarefa
- **AND** aparece "Subtarefa de {título do pai}"

#### Scenario: Pai recusado pela API
- **GIVEN** a API responde erro ao criar a subtarefa
- **WHEN** o usuário confirma
- **THEN** a mensagem do Problem Details aparece e o formulário mantém o título digitado
