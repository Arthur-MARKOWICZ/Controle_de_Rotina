# ui-objetivo Specification

## Purpose

Expõe na interface web os objetivos do usuário — o que ele quer alcançar — numa página própria, mantendo-os visíveis e permitindo criar, editar, concluir, arquivar e reabrir cada objetivo.

## Requirements

### Requirement: Destino Objetivos na navegação
O sistema MUST oferecer a rota `/objetivos` com o rótulo **Objetivos**: na sidebar, no grupo primário imediatamente após Projetos; em telas estreitas, dentro do menu "Mais", sem acrescentar item à barra inferior. O atalho de teclado `G` seguido de `O` MUST levar a `/objetivos` e constar na ajuda de atalhos. O título da aba MUST ser "Objetivos" na lista e "Novo objetivo" / "Objetivo" no drawer.

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

### Requirement: Visões por status
A página `/objetivos` MUST listar os objetivos do usuário em três visões exclusivas — Ativos, Concluídos e Arquivados — com Ativos como padrão. A visão e a página atual MUST ficar na URL, de modo que recarregar ou compartilhar o link reabra a mesma visão. A ordem dos itens MUST ser a devolvida pela API, e a página MUST mostrar o total de objetivos da visão.

#### Scenario: Padrão mostra ativos
- **GIVEN** o usuário tem objetivos ativos, concluídos e arquivados
- **WHEN** abre `/objetivos`
- **THEN** vê apenas os ativos, com a visão Ativos marcada

#### Scenario: Trocar de visão reflete na URL
- **GIVEN** o usuário em `/objetivos`
- **WHEN** seleciona a visão Concluídos
- **THEN** a URL passa a indicar o status concluído e a página volta à primeira
- **AND** a lista mostra apenas objetivos concluídos

#### Scenario: Visão inválida na URL
- **WHEN** o usuário abre `/objetivos` com um status desconhecido na URL
- **THEN** vê a visão Ativos

#### Scenario: Paginação
- **GIVEN** mais objetivos ativos do que cabem numa página
- **WHEN** o usuário avança para a próxima página
- **THEN** vê a página seguinte e a URL reflete a página atual

### Requirement: Estados de carregando, vazio e erro
A página MUST mostrar placeholder de carregamento enquanto busca, estado vazio com orientação quando a visão não tem objetivos e alerta de erro quando a busca falha, no mesmo padrão das demais listas. O estado vazio de Ativos MUST oferecer criar o primeiro objetivo; os de Concluídos e Arquivados MUST oferecer voltar aos ativos.

#### Scenario: Carregando
- **WHEN** a lista está sendo buscada
- **THEN** a página mostra o placeholder de carregamento e nenhum card

#### Scenario: Nenhum objetivo ativo
- **GIVEN** o usuário sem objetivos ativos
- **WHEN** abre `/objetivos`
- **THEN** vê mensagem de lista vazia com exemplo de objetivo e ação "Criar primeiro objetivo"

#### Scenario: Visão arquivada vazia
- **GIVEN** o usuário sem objetivos arquivados
- **WHEN** seleciona a visão Arquivados
- **THEN** vê "Nenhum objetivo arquivado" e a ação "Ver ativos"

#### Scenario: Falha ao carregar
- **WHEN** a busca da lista falha (erro de servidor, timeout ou resposta inválida)
- **THEN** a página mostra "Não foi possível carregar os objetivos." em alerta
- **AND** não mostra cards nem estado vazio

### Requirement: Card de objetivo
Cada objetivo MUST aparecer como card com título, descrição truncada (quando houver) e um espaço reservado para progresso que não exibe percentual nem barra preenchida. Objetivo ativo MUST mostrar o prazo em data local (ou nada, se não tiver prazo); concluído MUST mostrar a data de conclusão; arquivado MUST ficar visualmente atenuado.

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

### Requirement: Destaque de prazo vencido
Objetivo ativo cujo prazo é anterior à data de hoje em BRT MUST ser destacado como vencido no card, com texto (não só cor). Prazo igual a hoje MUST aparecer como "vence hoje", sem destaque de vencido. Concluídos e arquivados MUST NOT ser destacados como vencidos.

#### Scenario: Prazo de ontem
- **GIVEN** hoje é 17/11/2026 em BRT e um objetivo ativo com prazo 16/11/2026
- **WHEN** a lista é renderizada
- **THEN** o card mostra "Prazo vencido" e fica visualmente distinguível dos demais

#### Scenario: Prazo de hoje perto da meia-noite
- **GIVEN** o instante atual é 2026-11-18T02:30Z (17/11 23:30 em BRT) e um objetivo ativo com prazo 17/11/2026
- **WHEN** a lista é renderizada
- **THEN** o card mostra que vence hoje, sem destaque de vencido

#### Scenario: Concluído com prazo passado
- **GIVEN** um objetivo concluído cujo prazo já passou
- **WHEN** a visão Concluídos é renderizada
- **THEN** o card não tem destaque de vencido

### Requirement: Ações por status no card
O card MUST oferecer apenas as ações válidas para o status: Ativo → Editar, Concluir, Arquivar; Concluído → Editar, Arquivar, Reabrir; Arquivado → Editar, Reabrir. Enquanto uma ação de qualquer card está em andamento, os botões de ação de todos os cards MUST ficar desabilitados.

#### Scenario: Ações de um ativo
- **WHEN** a visão Ativos é renderizada
- **THEN** cada card oferece Editar, Concluir e Arquivar, e não oferece Reabrir

#### Scenario: Ações de um arquivado
- **WHEN** a visão Arquivados é renderizada
- **THEN** cada card oferece Editar e Reabrir, e não oferece Concluir nem Arquivar

#### Scenario: Clique duplo durante a ação
- **GIVEN** o usuário confirmou Arquivar num card
- **WHEN** clica de novo antes da resposta
- **THEN** apenas uma requisição de arquivar é enviada

#### Scenario: Ação em andamento bloqueia os outros cards
- **GIVEN** uma ação de Reabrir em andamento num card
- **WHEN** a lista é renderizada
- **THEN** os botões de ação dos demais cards ficam desabilitados

### Requirement: Concluir e arquivar com confirmação
Concluir e Arquivar MUST pedir confirmação com o título do objetivo antes de chamar a API. Cancelar MUST NOT enviar requisição nem alterar a lista. Após sucesso, o objetivo MUST sair da visão atual e um toast MUST confirmar ("Objetivo concluído" / "Objetivo arquivado").

#### Scenario: Concluir confirmado
- **GIVEN** um objetivo ativo "Perder 5 kg"
- **WHEN** o usuário aciona Concluir e confirma
- **THEN** o objetivo sai da visão Ativos
- **AND** aparece o toast "Objetivo concluído"

#### Scenario: Arquivar cancelado
- **GIVEN** um objetivo ativo
- **WHEN** o usuário aciona Arquivar e cancela a confirmação
- **THEN** nenhuma requisição é enviada e o card permanece igual

#### Scenario: Arquivar um concluído
- **GIVEN** um objetivo na visão Concluídos
- **WHEN** o usuário aciona Arquivar e confirma
- **THEN** o objetivo sai da visão Concluídos
- **AND** aparece o toast "Objetivo arquivado"

### Requirement: Reabrir sem confirmação
Reabrir MUST chamar a API direto, sem diálogo. Após sucesso, o objetivo MUST sair da visão atual e um toast MUST confirmar "Objetivo reaberto".

#### Scenario: Reabrir arquivado
- **GIVEN** um objetivo na visão Arquivados
- **WHEN** o usuário aciona Reabrir
- **THEN** o objetivo sai da visão Arquivados
- **AND** aparece o toast "Objetivo reaberto"

### Requirement: Erro em ação de ciclo de vida
Quando concluir, arquivar ou reabrir falha, a página MUST mostrar em alerta a mensagem da API (o `detail` da ProblemDetail, ou o `title` na falta dele) ou uma mensagem genérica quando não houver, MUST NOT mostrar toast de sucesso e MUST recarregar a lista para refletir o estado real.

#### Scenario: Conflito por mudança em outra aba
- **GIVEN** um objetivo já concluído em outra aba, ainda listado como ativo
- **WHEN** o usuário conclui e a API responde 409 "Objetivo já concluído" com detail "O objetivo informado já está concluído."
- **THEN** a página mostra "O objetivo informado já está concluído." em alerta
- **AND** a lista é recarregada e o objetivo deixa de aparecer em Ativos

#### Scenario: Objetivo removido
- **WHEN** o usuário arquiva e a API responde 404 "Objetivo não encontrado" com detail "Objetivo inexistente ou sem permissão de acesso."
- **THEN** a página mostra "Objetivo inexistente ou sem permissão de acesso." em alerta, sem toast de sucesso

#### Scenario: Falha de servidor
- **WHEN** o usuário reabre e a API responde 500 sem mensagem utilizável
- **THEN** a página mostra "Não foi possível reabrir o objetivo." em alerta

### Requirement: Criar objetivo no drawer
`/objetivos/nova` MUST abrir um drawer sobre a lista com título (obrigatório, até 80 caracteres), descrição (opcional, até 500) e prazo (opcional, sem data anterior a hoje em BRT). Dados inválidos MUST ser apontados sem chamar a API. Após sucesso, o drawer MUST fechar na visão Ativos, venha de qual visão vier, de modo que o novo objetivo apareça, e um toast MUST confirmar "Objetivo criado".

#### Scenario: Criar só com título
- **GIVEN** o drawer de novo objetivo aberto
- **WHEN** o usuário informa "Perder 5 kg" e salva
- **THEN** o drawer fecha, o objetivo aparece em Ativos e aparece o toast "Objetivo criado"

#### Scenario: Criar a partir de outra visão
- **GIVEN** o usuário abriu o drawer de novo objetivo a partir da visão Concluídos
- **WHEN** salva um objetivo válido
- **THEN** o drawer fecha e a lista mostra a visão Ativos

#### Scenario: Título em branco
- **WHEN** o usuário salva com título só de espaços
- **THEN** o drawer mostra erro no título e nenhuma requisição é enviada

#### Scenario: Prazo anterior a hoje
- **GIVEN** hoje é 17/11/2026 em BRT
- **WHEN** o usuário informa prazo 16/11/2026 e salva
- **THEN** o drawer mostra erro no prazo e nenhuma requisição é enviada

#### Scenario: Erro da API ao criar
- **WHEN** a API responde 400 "Prazo inválido" com detail "O prazo não pode estar no passado."
- **THEN** o drawer permanece aberto com os dados preenchidos e mostra "O prazo não pode estar no passado."

### Requirement: Editar objetivo no drawer
`/objetivos/:id` MUST abrir o drawer preenchido com o objetivo, em qualquer status, com as mesmas validações da criação; um prazo já vencido que não foi alterado MUST ser aceito. Limpar o prazo MUST remover o prazo do objetivo. Após sucesso, o card MUST refletir os novos dados e um toast MUST confirmar "Objetivo atualizado". Objetivo inexistente ou de outro usuário MUST mostrar erro no drawer, sem formulário.

#### Scenario: Editar título
- **GIVEN** o drawer de um objetivo ativo aberto
- **WHEN** o usuário troca o título e salva
- **THEN** o drawer fecha, o card mostra o novo título e aparece o toast "Objetivo atualizado"

#### Scenario: Manter prazo vencido
- **GIVEN** hoje é 05/12/2026 em BRT e um objetivo com prazo 30/11/2026
- **WHEN** o usuário edita só o título e salva
- **THEN** a edição é enviada sem erro de prazo

#### Scenario: Remover prazo
- **GIVEN** um objetivo com prazo
- **WHEN** o usuário limpa o prazo e salva
- **THEN** o card deixa de mostrar prazo

#### Scenario: Objetivo não encontrado
- **WHEN** o usuário abre `/objetivos/:id` de um objetivo inexistente
- **THEN** o drawer mostra a mensagem de não encontrado da API e nenhum campo editável

### Requirement: Descartar alterações ao fechar o drawer
Fechar o drawer (Cancelar, Esc ou fundo) com o formulário alterado MUST pedir confirmação de descarte; sem alterações MUST fechar direto. Ao fechar sem criar um objetivo, o usuário MUST voltar à lista na mesma visão e página.

#### Scenario: Fechar com alterações
- **GIVEN** o drawer com o título alterado
- **WHEN** o usuário pressiona Esc
- **THEN** aparece a confirmação de descarte
- **AND** ao escolher continuar editando, o drawer segue aberto com os dados

#### Scenario: Fechar sem alterações volta à visão
- **GIVEN** o usuário abriu o drawer de edição a partir da visão Concluídos
- **WHEN** fecha sem alterar nada
- **THEN** o drawer fecha sem confirmação e a lista continua na visão Concluídos
