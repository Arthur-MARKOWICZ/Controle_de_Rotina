# Spec Delta

## MODIFIED Requirements

### Requirement: Destino Objetivos na navegação
O sistema MUST oferecer a rota `/objetivos` com o rótulo **Objetivos**: na sidebar, no grupo primário imediatamente após Projetos; em telas estreitas, dentro do menu "Mais", sem acrescentar item à barra inferior. O atalho de teclado `G` seguido de `O` MUST levar a `/objetivos` e constar na ajuda de atalhos. O título da aba MUST ser "Objetivos" na lista, "Novo objetivo" no drawer de criação, "Objetivo" na página de detalhe e "Editar objetivo" no drawer de edição.

#### Scenario: Abrir pela sidebar
- **GIVEN** o usuário autenticado em tela larga
- **WHEN** aciona o link Objetivos da sidebar
- **THEN** navega para `/objetivos`
- **AND** o link Objetivos aparece logo depois de Projetos

#### Scenario: Abrir pelo menu Mais no mobile
- **GIVEN** o usuário autenticado em tela estreita
- **WHEN** abre o menu "Mais" e aciona Objetivos
- **THEN** navega para `/objetivos`
- **AND** a barra inferior continua com os mesmos 5 destinos

#### Scenario: Atalho G O
- **GIVEN** o foco fora de campo editável
- **WHEN** o usuário pressiona `G` e depois `O`
- **THEN** navega para `/objetivos`

#### Scenario: Atalho ignorado em campo editável
- **GIVEN** o foco num campo de texto
- **WHEN** o usuário digita `g` e depois `o`
- **THEN** permanece na página atual

#### Scenario: Título da aba no detalhe e na edição
- **WHEN** o usuário abre `/objetivos/:id` e depois `/objetivos/:id/editar`
- **THEN** o título da aba é "Objetivo" no detalhe e "Editar objetivo" no drawer

### Requirement: Card de objetivo
Cada objetivo MUST aparecer como card com título, descrição truncada (quando houver) e um espaço reservado para progresso que não exibe percentual nem barra preenchida. O título MUST ser um link para a página de detalhe do objetivo. Objetivo ativo MUST mostrar o prazo em data local (ou nada, se não tiver prazo); concluído MUST mostrar a data de conclusão; arquivado MUST ficar visualmente atenuado.

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

#### Scenario: Progresso ainda indisponível
- **WHEN** qualquer card é renderizado
- **THEN** o espaço de progresso mostra texto explicativo
- **AND** não há indicação de 0% nem elemento anunciado como barra de progresso

#### Scenario: Título abre o detalhe
- **GIVEN** a visão Concluídos na página 2
- **WHEN** o usuário aciona o título de um card
- **THEN** navega para `/objetivos/:id` daquele objetivo
- **AND** o link "Voltar a Objetivos" do detalhe leva de volta à visão Concluídos, página 2

### Requirement: Editar objetivo no drawer
`/objetivos/:id/editar` MUST abrir o drawer sobre a página de detalhe, preenchido com o objetivo, em qualquer status, com as mesmas validações da criação. Um prazo já vencido que não foi alterado MUST ser aceito, e limpar o prazo MUST removê-lo. O "Editar" do card e o do detalhe MUST levar a essa rota. Após sucesso, o drawer MUST fechar no detalhe com os novos dados e um toast MUST confirmar "Objetivo atualizado". Objetivo inexistente MUST mostrar erro, sem formulário.

#### Scenario: Editar título
- **GIVEN** o drawer de edição de um objetivo ativo aberto sobre o detalhe
- **WHEN** o usuário troca o título e salva
- **THEN** o drawer fecha, o detalhe mostra o novo título e aparece o toast "Objetivo atualizado"

#### Scenario: Editar pelo card
- **GIVEN** a lista de objetivos ativos
- **WHEN** o usuário aciona Editar num card
- **THEN** navega para `/objetivos/:id/editar` com o drawer aberto sobre o detalhe daquele objetivo

#### Scenario: Manter prazo vencido
- **GIVEN** hoje é 05/12/2026 em BRT e um objetivo com prazo 30/11/2026
- **WHEN** o usuário edita só o título e salva
- **THEN** a edição é enviada sem erro de prazo

#### Scenario: Remover prazo
- **GIVEN** um objetivo com prazo
- **WHEN** o usuário limpa o prazo e salva
- **THEN** o detalhe deixa de mostrar prazo

#### Scenario: Objetivo não encontrado
- **WHEN** o usuário abre `/objetivos/:id/editar` de um objetivo inexistente
- **THEN** a página mostra a mensagem de não encontrado da API e nenhum campo editável

### Requirement: Descartar alterações ao fechar o drawer
Fechar o drawer (Cancelar, Esc ou fundo) com o formulário alterado MUST pedir confirmação de descarte; sem alterações MUST fechar direto. Ao fechar o drawer de criação sem criar, o usuário MUST voltar à lista na mesma visão e página. Ao fechar o drawer de edição, o usuário MUST voltar ao detalhe do objetivo.

#### Scenario: Fechar com alterações
- **GIVEN** o drawer com o título alterado
- **WHEN** o usuário pressiona Esc
- **THEN** aparece a confirmação de descarte
- **AND** ao escolher continuar editando, o drawer segue aberto com os dados

#### Scenario: Fechar sem alterações volta à visão
- **GIVEN** o usuário abriu o drawer de novo objetivo a partir da visão Concluídos
- **WHEN** fecha sem alterar nada
- **THEN** o drawer fecha sem confirmação e a lista continua na visão Concluídos

#### Scenario: Fechar edição volta ao detalhe
- **GIVEN** o drawer de edição aberto sobre o detalhe de um objetivo
- **WHEN** o usuário fecha sem alterar nada
- **THEN** o drawer fecha sem confirmação e o usuário continua em `/objetivos/:id`

## ADDED Requirements

### Requirement: Página de detalhe do objetivo
`/objetivos/:id` MUST mostrar uma página própria do objetivo, em qualquer status, com título, status, prazo (ou data de conclusão, conforme o status), descrição e um link "Voltar a Objetivos". O destaque de prazo vencido MUST seguir as mesmas regras do card. Objetivo inexistente ou de outro usuário MUST mostrar a mensagem de não encontrado, sem ações e sem itens.

#### Scenario: Abrir detalhe de objetivo ativo
- **GIVEN** um objetivo ativo "Passar na certificação" com prazo 15/12/2026
- **WHEN** o usuário abre `/objetivos/:id`
- **THEN** vê o título, o status Ativo, o prazo em formato local e a descrição

#### Scenario: Detalhe de objetivo vencido
- **GIVEN** hoje é 17/11/2026 em BRT e um objetivo ativo com prazo 16/11/2026
- **WHEN** o usuário abre o detalhe
- **THEN** a página mostra "Prazo vencido" em texto

#### Scenario: Objetivo de outro usuário
- **WHEN** o usuário abre `/objetivos/:id` e a API responde 404 "Objetivo não encontrado" com detail "Objetivo inexistente ou sem permissão de acesso."
- **THEN** a página mostra "Objetivo inexistente ou sem permissão de acesso."
- **AND** não mostra ações nem a seção de itens

#### Scenario: Falha ao carregar o objetivo
- **WHEN** a busca do objetivo falha por erro de servidor ou de rede
- **THEN** a página mostra "Não foi possível carregar o objetivo." em alerta e nenhuma ação

### Requirement: Itens vinculados no detalhe
O detalhe MUST listar os itens vinculados ao objetivo em três grupos — Tarefas, Hábitos e Projetos — cada um com sua contagem, na ordem devolvida pela API. Cada item MUST mostrar nome e status e ser um link para a tela do item. Tarefa herdada de um projeto MUST mostrar o selo "via projeto X", com o nome do projeto. Grupo sem itens MUST NOT ser exibido.

#### Scenario: Itens diretos e herdados
- **GIVEN** um objetivo com a tarefa "Estudar S3" vinculada direto e o projeto "Estudos AWS", que tem a tarefa "Simulado 1"
- **WHEN** o usuário abre o detalhe
- **THEN** o grupo Tarefas mostra "Estudar S3" sem selo e "Simulado 1" com o selo "via projeto Estudos AWS"
- **AND** o grupo Projetos mostra "Estudos AWS"

#### Scenario: Item leva à sua tela
- **GIVEN** o detalhe com o hábito "Flashcards" no grupo Hábitos
- **WHEN** o usuário aciona o hábito
- **THEN** navega para `/habitos/:id` daquele hábito

#### Scenario: Vínculo feito em outro formulário aparece ao voltar
- **GIVEN** o usuário vinculou a tarefa "Ler documentação" ao objetivo no drawer da tarefa
- **WHEN** abre o detalhe do objetivo
- **THEN** "Ler documentação" aparece no grupo Tarefas

#### Scenario: Desvínculo feito em outro formulário some ao voltar
- **GIVEN** o hábito "Flashcards" estava vinculado e o usuário o desvinculou no drawer do hábito
- **WHEN** abre o detalhe do objetivo
- **THEN** "Flashcards" não aparece e o grupo Hábitos não é exibido

### Requirement: Estados da seção de itens
A seção de itens MUST mostrar placeholder enquanto carrega. Sem nenhum item em nenhum grupo, MUST mostrar um estado vazio que orienta a vincular pelo formulário da tarefa, do hábito ou do projeto. Se a busca de itens falhar, MUST mostrar "Não foi possível carregar os itens vinculados." em alerta, sem afetar o cabeçalho nem as ações do objetivo.

#### Scenario: Carregando itens
- **WHEN** os itens estão sendo buscados
- **THEN** a seção mostra o placeholder e nenhum grupo

#### Scenario: Objetivo sem itens
- **GIVEN** a API devolve tarefas, hábitos e projetos vazios
- **WHEN** o usuário abre o detalhe
- **THEN** a seção mostra o estado vazio com a orientação de vínculo e nenhum grupo

#### Scenario: Falha de rede nos itens
- **GIVEN** o objetivo carrega, mas a busca de itens falha por erro de rede
- **WHEN** o usuário abre o detalhe
- **THEN** a seção mostra "Não foi possível carregar os itens vinculados." em alerta
- **AND** o cabeçalho e as ações do objetivo continuam visíveis

#### Scenario: Resposta inválida nos itens
- **WHEN** a busca de itens recebe HTML do proxy em vez de JSON
- **THEN** a seção mostra "Não foi possível carregar os itens vinculados." em alerta e nenhum grupo

### Requirement: Ações de ciclo de vida no detalhe
O detalhe MUST oferecer as mesmas ações por status do card (Ativo → Editar, Concluir, Arquivar; Concluído → Editar, Arquivar, Reabrir; Arquivado → Editar, Reabrir), com a mesma confirmação para Concluir e Arquivar. Após sucesso, o usuário MUST continuar no detalhe, com status e ações atualizados, e um toast MUST confirmar a ação. Em falha, MUST mostrar a mensagem da API em alerta, sem toast, e recarregar o objetivo.

#### Scenario: Concluir no detalhe
- **GIVEN** o detalhe de um objetivo ativo
- **WHEN** o usuário aciona Concluir e confirma
- **THEN** continua no detalhe, o status passa a Concluído, as ações passam a Editar, Arquivar e Reabrir
- **AND** aparece o toast "Objetivo concluído"

#### Scenario: Arquivar cancelado no detalhe
- **GIVEN** o detalhe de um objetivo ativo
- **WHEN** o usuário aciona Arquivar e cancela a confirmação
- **THEN** nenhuma requisição é enviada e o status continua Ativo

#### Scenario: Clique duplo durante a ação
- **GIVEN** o usuário confirmou Concluir no detalhe
- **WHEN** clica de novo antes da resposta
- **THEN** apenas uma requisição de concluir é enviada

#### Scenario: Conflito ao concluir
- **GIVEN** o objetivo já foi concluído em outra aba
- **WHEN** o usuário conclui e a API responde 409 "Objetivo já concluído" com detail "O objetivo informado já está concluído."
- **THEN** a página mostra "O objetivo informado já está concluído." em alerta, sem toast de sucesso
- **AND** o objetivo é recarregado e aparece como Concluído

#### Scenario: Falha de servidor ao reabrir
- **WHEN** o usuário reabre e a API responde 500 sem mensagem utilizável
- **THEN** a página mostra "Não foi possível reabrir o objetivo." em alerta

### Requirement: Seletor de objetivos nos formulários
Os formulários de criar e editar tarefa, hábito e projeto MUST oferecer um seletor de vários objetivos, com busca pelo título. As opções MUST ser só os objetivos ativos do usuário. Os objetivos escolhidos MUST aparecer como chips removíveis e ser salvos junto com o item. Remover todos os chips e salvar MUST desvincular o item de todos os objetivos diretos.

#### Scenario: Vincular tarefa a um objetivo
- **GIVEN** o drawer de nova tarefa e um objetivo ativo "Passar na certificação"
- **WHEN** o usuário escolhe esse objetivo no seletor e salva
- **THEN** a tarefa é criada vinculada a "Passar na certificação"

#### Scenario: Desvincular hábito
- **GIVEN** o drawer de um hábito vinculado ao objetivo "Correr 10 km"
- **WHEN** o usuário remove o chip "Correr 10 km" e salva
- **THEN** o hábito é salvo sem esse objetivo

#### Scenario: Arquivado e concluído não são opções
- **GIVEN** o usuário tem os objetivos "A" ativo, "B" concluído e "C" arquivado
- **WHEN** abre o seletor no formulário de um projeto
- **THEN** só "A" aparece como opção

#### Scenario: Busca sem resultado
- **GIVEN** objetivos ativos "Correr 10 km" e "Ler 12 livros"
- **WHEN** o usuário busca "inglês" no seletor
- **THEN** nenhuma opção aparece e o seletor informa que não há objetivo com esse termo

#### Scenario: Usuário sem objetivos ativos
- **GIVEN** o usuário não tem objetivos ativos
- **WHEN** abre o seletor
- **THEN** o seletor informa "Nenhum objetivo ativo" e oferece um link para a página de Objetivos

#### Scenario: Falha ao carregar opções
- **WHEN** a busca de objetivos ativos falha por erro de rede
- **THEN** o seletor mostra "Não foi possível carregar os objetivos." em alerta
- **AND** os chips já escolhidos continuam visíveis e o formulário continua podendo ser salvo

#### Scenario: API recusa objetivo que deixou de estar ativo
- **GIVEN** o usuário escolheu o objetivo "A", que foi arquivado em outra aba antes de salvar
- **WHEN** salva e a API responde 409 "Objetivo não está ativo" com detail "Só é possível vincular a um objetivo ativo."
- **THEN** o formulário continua aberto com os dados e mostra "Só é possível vincular a um objetivo ativo."

### Requirement: Objetivo já vinculado que deixou de estar ativo
Um item já vinculado a um objetivo concluído ou arquivado MUST mostrar esse objetivo como chip selecionado, com o status escrito, sem que ele apareça nas opções. Salvar o formulário sem remover o chip MUST manter o vínculo. Remover o chip MUST desvinculá-lo, e ele MUST NOT poder ser escolhido de novo enquanto não estiver ativo.

#### Scenario: Chip de objetivo concluído
- **GIVEN** uma tarefa vinculada ao objetivo "Perder 5 kg", já concluído
- **WHEN** o usuário abre o drawer da tarefa
- **THEN** o seletor mostra o chip "Perder 5 kg" com o status Concluído
- **AND** "Perder 5 kg" não aparece nas opções

#### Scenario: Salvar outra alteração mantém o vínculo
- **GIVEN** uma tarefa vinculada a um objetivo arquivado
- **WHEN** o usuário altera só o título e salva
- **THEN** a tarefa continua vinculada ao objetivo arquivado

### Requirement: Seletor ausente em subtarefa
O formulário de uma subtarefa MUST NOT oferecer o seletor de objetivos, porque subtarefa não pode ser vinculada a objetivo. Salvar uma subtarefa MUST NOT enviar objetivos.

#### Scenario: Drawer de subtarefa
- **GIVEN** uma tarefa que é subtarefa de outra
- **WHEN** o usuário abre o drawer dela
- **THEN** o seletor de objetivos não aparece

#### Scenario: Salvar subtarefa
- **GIVEN** o drawer de uma subtarefa
- **WHEN** o usuário altera o título e salva
- **THEN** a requisição não leva objetivos e a subtarefa é salva
