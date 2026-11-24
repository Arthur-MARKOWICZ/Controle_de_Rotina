# Spec Delta

## MODIFIED Requirements

### Requirement: Visões por status
A página `/objetivos` MUST listar os objetivos do usuário em duas visões exclusivas — Ativos e Encerrados — com Ativos como padrão. Encerrados MUST reunir concluídos e arquivados. A visão e a página atual MUST ficar na URL, de modo que recarregar ou compartilhar o link reabra a mesma visão. Links antigos com o status concluído ou arquivado na URL MUST abrir Encerrados. A ordem dos itens MUST ser a devolvida pela API, e a página MUST mostrar o total de objetivos da visão.

#### Scenario: Padrão mostra ativos
- **GIVEN** o usuário tem objetivos ativos, concluídos e arquivados
- **WHEN** abre `/objetivos`
- **THEN** vê apenas os ativos, com a visão Ativos marcada

#### Scenario: Trocar de visão reflete na URL
- **GIVEN** o usuário em `/objetivos`
- **WHEN** seleciona a visão Encerrados
- **THEN** a URL passa a indicar a visão de encerrados e a página volta à primeira
- **AND** a lista mostra concluídos e arquivados, na ordem da API, e nenhum ativo

#### Scenario: Link antigo de concluídos
- **WHEN** o usuário abre `/objetivos?status=CONCLUIDO`
- **THEN** vê a visão Encerrados marcada

#### Scenario: Link antigo de arquivados
- **WHEN** o usuário abre `/objetivos?status=ARQUIVADO&page=1`
- **THEN** vê a visão Encerrados na segunda página

#### Scenario: Visão inválida na URL
- **WHEN** o usuário abre `/objetivos` com uma visão ou status desconhecido na URL
- **THEN** vê a visão Ativos

#### Scenario: Paginação
- **GIVEN** mais objetivos ativos do que cabem numa página
- **WHEN** o usuário avança para a próxima página
- **THEN** vê a página seguinte e a URL reflete a página atual

### Requirement: Estados de carregando, vazio e erro
A página MUST mostrar placeholder de carregamento enquanto busca, estado vazio com orientação quando a visão não tem objetivos e alerta de erro quando a busca falha, no mesmo padrão das demais listas. O estado vazio de Ativos MUST oferecer criar o primeiro objetivo; o de Encerrados MUST oferecer voltar aos ativos.

#### Scenario: Carregando
- **WHEN** a lista está sendo buscada
- **THEN** a página mostra o placeholder de carregamento e nenhum card

#### Scenario: Nenhum objetivo ativo
- **GIVEN** o usuário sem objetivos ativos
- **WHEN** abre `/objetivos`
- **THEN** vê mensagem de lista vazia com exemplo de objetivo e ação "Criar primeiro objetivo"

#### Scenario: Visão arquivada vazia
- **GIVEN** o usuário sem objetivos concluídos nem arquivados
- **WHEN** seleciona a visão Encerrados
- **THEN** vê "Nenhum objetivo encerrado" e a ação "Ver ativos"

#### Scenario: Falha ao carregar
- **WHEN** a busca da lista falha (erro de servidor, timeout ou resposta inválida)
- **THEN** a página mostra "Não foi possível carregar os objetivos." em alerta
- **AND** não mostra cards nem estado vazio

### Requirement: Card de objetivo
Cada objetivo MUST aparecer como card com título, descrição truncada (quando houver) e o progresso do objetivo: a barra de progresso quando houver progresso, ou o convite "Vincule tarefas, hábitos ou projetos para medir o progresso." quando não houver, sem barra e sem 0%. O título MUST ser um link para a página de detalhe do objetivo. Objetivo ativo MUST mostrar o prazo em data local (ou nada, se não tiver prazo); encerrado MUST mostrar as datas que tiver, "Concluído em" e "Arquivado em", e não o prazo; arquivado MUST ficar visualmente atenuado, inclusive a barra.

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
- **WHEN** a visão Encerrados é renderizada
- **THEN** o card mostra que foi concluído em 12/11/2026 e não mostra prazo

#### Scenario: Arquivado direto de ativo mostra data de arquivamento
- **GIVEN** um objetivo arquivado em 15/11/2026 sem nunca ter sido concluído
- **WHEN** a visão Encerrados é renderizada
- **THEN** o card mostra "Arquivado em" 15/11/2026 e não mostra data de conclusão

#### Scenario: Concluído e depois arquivado mostra as duas datas
- **GIVEN** um objetivo concluído em 05/11/2026 e arquivado em 20/11/2026
- **WHEN** a visão Encerrados é renderizada
- **THEN** o card mostra "Concluído em" 05/11/2026 e "Arquivado em" 20/11/2026

#### Scenario: Card com progresso
- **GIVEN** um objetivo ativo com progresso de 40%, 120 de 300 minutos
- **WHEN** a lista é renderizada
- **THEN** o card mostra uma barra de progresso em 40% e o texto "2h de 5h concluídas"

#### Scenario: Progresso ainda indisponível
- **GIVEN** um objetivo cujo progresso vem nulo da API
- **WHEN** a lista é renderizada
- **THEN** o card mostra "Vincule tarefas, hábitos ou projetos para medir o progresso."
- **AND** não há barra de progresso nem indicação de 0%

#### Scenario: Concluído mantém o progresso congelado
- **GIVEN** um objetivo concluído com progresso de 60%
- **WHEN** a visão Encerrados é renderizada
- **THEN** o card mostra a barra em 60%

#### Scenario: Título abre o detalhe
- **GIVEN** a visão Encerrados na página 2
- **WHEN** o usuário aciona o título de um card
- **THEN** navega para `/objetivos/:id` daquele objetivo
- **AND** o link "Voltar a Objetivos" do detalhe leva de volta à visão Encerrados, página 2

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
- **WHEN** a visão Encerrados é renderizada
- **THEN** o card não tem destaque de vencido

### Requirement: Ações por status no card
O card MUST oferecer apenas as ações válidas para o status: Ativo → Editar, Concluir, Arquivar; Concluído → Editar, Arquivar, Reabrir; Arquivado → Editar, Reabrir. Enquanto uma ação de qualquer card está em andamento, os botões de ação de todos os cards MUST ficar desabilitados.

#### Scenario: Ações de um ativo
- **WHEN** a visão Ativos é renderizada
- **THEN** cada card oferece Editar, Concluir e Arquivar, e não oferece Reabrir

#### Scenario: Ações de um arquivado
- **GIVEN** a visão Encerrados com um objetivo concluído e um arquivado
- **WHEN** a lista é renderizada
- **THEN** o card do arquivado oferece Editar e Reabrir, e não oferece Concluir nem Arquivar
- **AND** o card do concluído oferece Editar, Arquivar e Reabrir

#### Scenario: Clique duplo durante a ação
- **GIVEN** o usuário confirmou Arquivar num card
- **WHEN** clica de novo antes da resposta
- **THEN** apenas uma requisição de arquivar é enviada

#### Scenario: Ação em andamento bloqueia os outros cards
- **GIVEN** uma ação de Reabrir em andamento num card
- **WHEN** a lista é renderizada
- **THEN** os botões de ação dos demais cards ficam desabilitados

### Requirement: Concluir e arquivar com confirmação
Concluir e Arquivar MUST pedir confirmação com o título do objetivo antes de chamar a API. Cancelar MUST NOT enviar requisição nem alterar a lista. Após sucesso, um toast MUST confirmar ("Objetivo concluído" / "Objetivo arquivado") e a lista MUST ser recarregada: o objetivo que deixa de pertencer à visão sai dela, e o que continua nela (arquivar um concluído em Encerrados) aparece com o novo status e as novas datas.

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
- **GIVEN** um objetivo concluído em 05/11/2026 na visão Encerrados
- **WHEN** o usuário aciona Arquivar e confirma
- **THEN** o objetivo continua em Encerrados, na mesma posição, com status Arquivado e as datas de conclusão e de arquivamento
- **AND** aparece o toast "Objetivo arquivado"

### Requirement: Reabrir sem confirmação
Reabrir MUST chamar a API direto, sem diálogo. Após sucesso, o objetivo MUST sair da visão atual e um toast MUST confirmar "Objetivo reaberto".

#### Scenario: Reabrir arquivado
- **GIVEN** um objetivo arquivado na visão Encerrados
- **WHEN** o usuário aciona Reabrir
- **THEN** o objetivo sai da visão Encerrados
- **AND** aparece o toast "Objetivo reaberto"

### Requirement: Criar objetivo no drawer
`/objetivos/nova` MUST abrir um drawer sobre a lista com título (obrigatório, até 80 caracteres), descrição (opcional, até 500) e prazo (opcional, sem data anterior a hoje em BRT). Dados inválidos MUST ser apontados sem chamar a API. Após sucesso, o drawer MUST fechar na visão Ativos, venha de qual visão vier, de modo que o novo objetivo apareça, e um toast MUST confirmar "Objetivo criado".

#### Scenario: Criar só com título
- **GIVEN** o drawer de novo objetivo aberto
- **WHEN** o usuário informa "Perder 5 kg" e salva
- **THEN** o drawer fecha, o objetivo aparece em Ativos e aparece o toast "Objetivo criado"

#### Scenario: Criar a partir de outra visão
- **GIVEN** o usuário abriu o drawer de novo objetivo a partir da visão Encerrados
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

### Requirement: Descartar alterações ao fechar o drawer
Fechar o drawer (Cancelar, Esc ou fundo) com o formulário alterado MUST pedir confirmação de descarte; sem alterações MUST fechar direto. Ao fechar o drawer de criação sem criar, o usuário MUST voltar à lista na mesma visão e página. Ao fechar o drawer de edição, o usuário MUST voltar ao detalhe do objetivo.

#### Scenario: Fechar com alterações
- **GIVEN** o drawer com o título alterado
- **WHEN** o usuário pressiona Esc
- **THEN** aparece a confirmação de descarte
- **AND** ao escolher continuar editando, o drawer segue aberto com os dados

#### Scenario: Fechar sem alterações volta à visão
- **GIVEN** o usuário abriu o drawer de novo objetivo a partir da visão Encerrados
- **WHEN** fecha sem alterar nada
- **THEN** o drawer fecha sem confirmação e a lista continua na visão Encerrados

#### Scenario: Fechar edição volta ao detalhe
- **GIVEN** o drawer de edição aberto sobre o detalhe de um objetivo
- **WHEN** o usuário fecha sem alterar nada
- **THEN** o drawer fecha sem confirmação e o usuário continua em `/objetivos/:id`

## ADDED Requirements

### Requirement: Evolução no detalhe do objetivo
O detalhe MUST mostrar a seção "Evolução", entre o progresso e os itens vinculados, com um gráfico dos minutos acumulados por dia vindos da API, em degraus, e o total acumulado atual em texto. Quando houver `antesDoHistorico` maior que zero, a seção MUST informar esse total como "antes do histórico" e o gráfico MUST partir dele.

#### Scenario: Objetivo com evolução
- **GIVEN** um objetivo ativo com pontos em 03/11 (30) e 05/11 (75)
- **WHEN** o usuário abre o detalhe
- **THEN** vê a seção "Evolução" entre a barra de progresso e os itens vinculados
- **AND** o gráfico sobe em 03/11 e em 05/11 e o texto indica 1h 15min acumuladas

#### Scenario: Minutos antes do histórico
- **GIVEN** a API devolve `antesDoHistorico` 120 e um ponto em 05/11 com acumulado 150
- **WHEN** o usuário abre o detalhe
- **THEN** a seção informa 2h antes do histórico
- **AND** o gráfico começa em 2h e sobe para 2h 30min em 05/11

#### Scenario: Evolução recarrega após ação no detalhe
- **GIVEN** o detalhe de um objetivo ativo com a evolução carregada
- **WHEN** o usuário conclui, arquiva ou reabre o objetivo pelo detalhe
- **THEN** a seção relê a evolução, porque a janela dos hábitos muda com o encerramento, sem recarregar a página
- **AND** ao concluir, passa a mostrar o marcador de encerramento

### Requirement: Período do gráfico de evolução
O eixo de tempo MUST ir do primeiro ponto até hoje em BRT para objetivo ativo, e até a data mais tarde entre o encerramento e o último ponto para objetivo encerrado. Em objetivo encerrado, o gráfico MUST marcar o dia do encerramento (conclusão, ou arquivamento quando não houver conclusão) com texto acessível.

#### Scenario: Ativo vai até hoje
- **GIVEN** hoje é 24/11/2026 em BRT e o último ponto de um objetivo ativo é 10/11
- **WHEN** o detalhe é renderizado
- **THEN** o eixo termina em 24/11 com a linha horizontal desde 10/11

#### Scenario: Encerrado termina no encerramento
- **GIVEN** um objetivo concluído em 12/11, com último ponto em 10/11, visto em 24/11
- **WHEN** o detalhe é renderizado
- **THEN** o eixo termina em 12/11 e há um marcador "Concluído em 12 nov"

#### Scenario: Ponto depois do encerramento
- **GIVEN** um objetivo concluído em 12/11 com um ponto em 15/11
- **WHEN** o detalhe é renderizado
- **THEN** o eixo termina em 15/11 e o marcador continua em 12/11

#### Scenario: Virada de mês
- **GIVEN** pontos em 30/11 e 01/12
- **WHEN** o detalhe é renderizado
- **THEN** os rótulos de data mostram 30/11 e 01/12 na ordem certa

### Requirement: Estados da seção de evolução
A seção de evolução MUST ter estados próprios, sem afetar o resto do detalhe: placeholder enquanto carrega; alerta "Não foi possível carregar a evolução." com ação "Tentar de novo" quando a busca falha; e, quando não há pontos nem `antesDoHistorico`, a seção MUST NOT aparecer. Com só `antesDoHistorico`, MUST mostrar o texto do total sem gráfico.

#### Scenario: Falha ao carregar a evolução
- **GIVEN** o objetivo e os itens carregam, mas a evolução responde 500
- **WHEN** o usuário abre o detalhe
- **THEN** a seção mostra "Não foi possível carregar a evolução." e "Tentar de novo"
- **AND** o cabeçalho, a barra e os itens continuam visíveis

#### Scenario: Tentar de novo
- **GIVEN** a seção em erro
- **WHEN** o usuário aciona "Tentar de novo" e a API responde 200
- **THEN** o gráfico aparece no lugar do alerta

#### Scenario: Sem nada para mostrar
- **GIVEN** a evolução vem com `pontos` vazio e `antesDoHistorico` 0
- **WHEN** o detalhe é renderizado
- **THEN** não há seção "Evolução"

#### Scenario: Só antes do histórico
- **GIVEN** a evolução vem com `pontos` vazio e `antesDoHistorico` 90
- **WHEN** o detalhe é renderizado
- **THEN** a seção mostra 1h 30min antes do histórico e nenhum gráfico

#### Scenario: Resposta inválida
- **WHEN** a evolução responde HTML do proxy ou JSON malformado
- **THEN** a seção mostra "Não foi possível carregar a evolução."

### Requirement: Gráfico de evolução acessível
O gráfico MUST ser acompanhado de uma alternativa textual para leitor de tela que liste cada dia com os minutos do dia e o acumulado, e MUST NOT depender só de cor para indicar o marcador de encerramento.

#### Scenario: Leitor de tela
- **GIVEN** um objetivo com três pontos de evolução
- **WHEN** o detalhe é lido por leitor de tela
- **THEN** há uma tabela com três linhas, cada uma com data, minutos no dia e acumulado
