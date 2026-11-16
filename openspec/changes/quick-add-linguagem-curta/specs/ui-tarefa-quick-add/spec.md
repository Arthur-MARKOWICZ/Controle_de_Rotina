# Spec Delta

## Purpose

Permite criar uma tarefa já com duração, prazo, prioridade e tags digitando uma linguagem curta no campo de título do quick add e do inline add, com pré-visualização obrigatória de tudo o que foi interpretado antes de salvar.

## ADDED Requirements

### Requirement: Tokens reconhecidos e título restante
No quick add e no inline add, o sistema MUST reconhecer como token apenas palavras inteiras separadas por espaço, sem diferenciar maiúsculas e acentos. Os tokens reconhecidos MUST sair do título; o restante, com espaços normalizados, MUST ser o título enviado.

#### Scenario: Frase completa
- **GIVEN** hoje é quarta-feira, 18/11
- **WHEN** o usuário digita `Estudar Spring 2h sex !alta #faculdade`
- **THEN** o título é `Estudar Spring`
- **AND** aparecem chips de duração 2h, prazo sex 20/11, prioridade Alta e tag faculdade

#### Scenario: Token dentro de palavra não é interpretado
- **WHEN** o usuário digita `Revisar sexologia2h`
- **THEN** nenhum chip aparece e o título é o texto inteiro

#### Scenario: Só tokens
- **WHEN** o usuário digita `2h amanhã` e tenta salvar
- **THEN** o sistema mostra "Informe um título." e não envia nada

### Requirement: Limite do título sem contar os tokens
O campo MUST aceitar até 200 caracteres para comportar os tokens, e o limite de 120 caracteres MUST valer só para o título restante. Título restante acima de 120 caracteres MUST bloquear o envio com a mensagem "O título deve ter no máximo 120 caracteres.".

#### Scenario: Título no limite com tokens
- **WHEN** o usuário digita um título de 110 caracteres seguido de `2h sex !alta` e salva
- **THEN** a tarefa é criada com o título de 110 caracteres e os valores dos chips

#### Scenario: Título restante longo demais
- **WHEN** o usuário digita um título de 121 caracteres seguido de `2h` e tenta salvar
- **THEN** o sistema mostra "O título deve ter no máximo 120 caracteres." e não envia nada

### Requirement: Token de duração
O sistema MUST reconhecer durações em horas e minutos (`2h`, `30min`, `30m`, `1h30`, `1h30min`) e convertê-las em minutos. Uma duração menor que 15 ou que não seja múltipla de 15 MUST aparecer como chip com erro e bloquear o envio até ser removida ou corrigida.

#### Scenario: Horas e minutos combinados
- **WHEN** o usuário digita `Ler artigo 1h30`
- **THEN** o chip de duração mostra 1h30 e a tarefa é criada com 90 minutos

#### Scenario: Duração fora da regra
- **WHEN** o usuário digita `Ligar 20min` e tenta salvar
- **THEN** o chip de duração mostra "A duração deve ser no mínimo 15 minutos e múltiplo de 15."
- **AND** nada é enviado

### Requirement: Token de prazo
O sistema MUST reconhecer `hoje`, `amanhã`/`amanha`, dias da semana (abreviado, completo e com `-feira`, com e sem acento) e `dd/mm`. O prazo MUST ser 23:59 do dia civil resolvido em America/Sao_Paulo.

#### Scenario: Hoje e amanhã
- **GIVEN** agora é 18/11 10:00 em BRT
- **WHEN** o usuário digita `Pagar conta amanhã`
- **THEN** o chip de prazo mostra 19/11 e a tarefa é criada com prazo 19/11 23:59 BRT

#### Scenario: Formas do dia da semana
- **WHEN** o usuário digita `sab`, `sáb`, `sábado` ou `sexta-feira`
- **THEN** cada forma é reconhecida como o respectivo dia da semana

### Requirement: Dia da semana resolve para a próxima ocorrência
Um dia da semana MUST resolver para a próxima ocorrência estritamente posterior a hoje (em BRT). Quando o dia digitado é o de hoje, o prazo MUST cair 7 dias depois.

#### Scenario: Dia mais à frente na semana
- **GIVEN** hoje é quarta-feira, 18/11 em BRT
- **WHEN** o usuário digita `sex`
- **THEN** o prazo é sexta-feira, 20/11

#### Scenario: Mesmo dia de hoje
- **GIVEN** hoje é sexta-feira, 20/11 em BRT
- **WHEN** o usuário digita `sex`
- **THEN** o prazo é sexta-feira, 27/11

#### Scenario: Virada de meia-noite em BRT
- **GIVEN** o instante atual é domingo 22/11 23:59 BRT (segunda 02:59 UTC)
- **WHEN** o usuário digita `seg`
- **THEN** o prazo é segunda-feira, 23/11

### Requirement: Data dd/mm
Um token `dd/mm` MUST resolver para essa data no ano corrente se ela for hoje ou futura, e no ano seguinte se já passou. Data inexistente (ex.: `31/02`) MUST NÃO ser interpretada e MUST permanecer no título.

#### Scenario: Data já passada no ano
- **GIVEN** hoje é 18/11/2026
- **WHEN** o usuário digita `Renovar 10/01`
- **THEN** o prazo é 10/01/2027

#### Scenario: Data inexistente
- **WHEN** o usuário digita `Evento 31/02`
- **THEN** nenhum chip de prazo aparece e o título é `Evento 31/02`

### Requirement: Token de prioridade
O sistema MUST reconhecer `!alta`, `!media`/`!média` e `!baixa`. Qualquer outro texto iniciado por `!` MUST permanecer no título.

#### Scenario: Prioridade alta
- **WHEN** o usuário digita `Entregar relatório !alta`
- **THEN** o chip de prioridade mostra Alta e a tarefa é criada com prioridade `ALTA`

#### Scenario: Prioridade desconhecida
- **WHEN** o usuário digita `Urgente !agora`
- **THEN** nenhum chip de prioridade aparece e o título é `Urgente !agora`

### Requirement: Token repetido
Quando o mesmo tipo de token (duração, prazo ou prioridade) aparece mais de uma vez, o último MUST valer e os anteriores MUST permanecer no título. Tags MUST acumular, sem repetir a mesma tag.

#### Scenario: Duas durações
- **WHEN** o usuário digita `Treino 1h 2h`
- **THEN** o chip de duração mostra 2h e o título é `Treino 1h`

#### Scenario: Tags repetidas
- **WHEN** o usuário digita `Ler #estudo #leitura #Estudo`
- **THEN** aparecem exatamente dois chips de tag: estudo e leitura

### Requirement: Pré-visualização obrigatória em chips
Todo valor interpretado MUST aparecer como chip antes do envio, e a tarefa MUST ser criada somente com os valores dos chips visíveis no momento em que o usuário salva.

#### Scenario: Envio com chips
- **WHEN** o usuário salva `Estudar 2h amanhã !alta`
- **THEN** a criação envia título `Estudar`, duração 120, prazo de amanhã 23:59 BRT e prioridade `ALTA`

#### Scenario: Texto sem tokens
- **WHEN** o usuário salva `Comprar pão`
- **THEN** nenhum chip aparece e a criação envia apenas o título (mais os valores dos selects, no inline add)

### Requirement: Remover chip devolve o token ao título
Remover um chip MUST devolver o texto original do token ao título, e esse token MUST NÃO voltar a ser interpretado enquanto o texto continuar sendo editado na mesma criação.

#### Scenario: Remover chip de duração
- **GIVEN** o usuário digitou `Ler cap 2h` e o chip 2h apareceu
- **WHEN** remove o chip e continua digitando ` amanhã`
- **THEN** o título é `Ler cap 2h`, só o chip de prazo aparece, e a tarefa é criada sem duração vinda do texto

#### Scenario: Nova criação volta a interpretar
- **GIVEN** um chip foi removido e a tarefa foi criada
- **WHEN** o usuário digita `2h` numa nova criação
- **THEN** o chip de duração aparece de novo

### Requirement: Escape com barra invertida
Um token precedido de `\` MUST NÃO ser interpretado e MUST aparecer no título sem a barra.

#### Scenario: Escapar duração e tag
- **WHEN** o usuário digita `Ler cap \2h da apostila \#1`
- **THEN** nenhum chip aparece e o título é `Ler cap 2h da apostila #1`

### Requirement: Tag inexistente exige confirmação
Uma tag `#nome` MUST ser casada com o catálogo do usuário sem diferenciar maiúsculas, mas diferenciando acentos (como a unicidade de nome de tag). Uma tag inexistente MUST aparecer como chip pendente "criar #nome?" e MUST ser criada só se o usuário confirmar; sem confirmação, a tarefa MUST ser criada sem essa tag.

#### Scenario: Tag existente
- **GIVEN** o usuário tem a tag `Faculdade`
- **WHEN** digita `Prova #faculdade` e salva
- **THEN** a tarefa é criada vinculada à tag `Faculdade` e nenhuma tag nova é criada

#### Scenario: Acento diferencia a tag
- **GIVEN** o usuário tem a tag `Ciência` e não tem `ciencia`
- **WHEN** digita `Ler #ciencia`
- **THEN** o chip aparece como pendente "criar #ciencia?"

#### Scenario: Tag nova não confirmada
- **GIVEN** o usuário não tem a tag `academia`
- **WHEN** digita `Treino #academia` e salva sem confirmar o chip
- **THEN** a tarefa é criada sem tags e o catálogo continua sem `academia`

#### Scenario: Tag nova confirmada
- **GIVEN** o usuário não tem a tag `academia`
- **WHEN** confirma o chip "criar #academia?" e salva
- **THEN** a tag é criada com a cor padrão e a tarefa é criada vinculada a ela

#### Scenario: Falha ao criar tag
- **GIVEN** o usuário confirmou o chip "criar #academia?"
- **WHEN** a criação da tag falha
- **THEN** a mensagem de erro do servidor é exibida, a tarefa não é criada e o texto digitado é mantido

#### Scenario: Catálogo indisponível
- **WHEN** a lista de tags não pode ser carregada
- **THEN** os tokens `#nome` aparecem como tags pendentes de confirmação e os demais tokens continuam funcionando

### Requirement: Inline add sincroniza os selects
No inline add, um token de prioridade ou de duração reconhecido MUST atualizar o select correspondente, e o valor enviado MUST ser o valor exibido no select. Remover o chip MUST devolver o select ao valor padrão.

#### Scenario: Token muda o select
- **GIVEN** o select de prioridade mostra Média
- **WHEN** o usuário digita `Deploy !alta`
- **THEN** o select de prioridade passa a mostrar Alta e a tarefa é criada com `ALTA`

#### Scenario: Remover chip restaura o padrão
- **GIVEN** o usuário digitou `Deploy 2h` e o select de duração mostra 2h
- **WHEN** remove o chip de duração
- **THEN** o select de duração volta a 30 min

### Requirement: Mais detalhes leva os valores interpretados
Ao abrir "Mais detalhes" a partir do quick add, o formulário completo MUST vir pré-preenchido com o título restante e com a duração, o prazo, a prioridade e as tags existentes dos chips visíveis. Tags pendentes de confirmação MUST NÃO ser levadas.

#### Scenario: Abrir formulário completo
- **GIVEN** o usuário digitou `Estudar 2h amanhã !alta #faculdade #nova` no quick add, com `faculdade` existente
- **WHEN** clica em "Mais detalhes"
- **THEN** o formulário abre com título `Estudar`, 120 minutos, prazo de amanhã 23:59, prioridade Alta e a tag `faculdade`
- **AND** a tag `nova` não é criada nem selecionada

### Requirement: Falha na criação preserva o texto
Se a criação da tarefa falhar, o sistema MUST exibir a mensagem de erro e manter o texto digitado e os chips, sem criar a tarefa.

#### Scenario: Erro do servidor
- **WHEN** o usuário salva e o servidor responde com erro de validação
- **THEN** a mensagem do servidor é exibida e o texto e os chips continuam na tela
