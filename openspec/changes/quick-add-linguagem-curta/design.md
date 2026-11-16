# Design

## Context

Veja `proposal.md` (Why) e `specs/ui-tarefa-quick-add/spec.md` para as regras.

Estado atual no front (`frontend/frontend-web/src/tarefa/`):

- `QuickAddTarefaDialog` envia só `{ titulo }`; "Mais detalhes" navega para `/tarefas/nova` com `TarefaDrawerState = { titulo }`.
- `InlineAddTarefa` (usado em `TarefasPage`) envia `titulo`, `prioridade` (select, padrão `MEDIA`) e `duracaoMinutos` (select de 15 a 240, padrão 30).
- `prazoAtalhos.ts` já tem a aritmética de data civil em BRT (`partesCivisBrt`, `adicionarDiasCivil`, `weekdayIsoBrt`, `proximaSegunda`, `fimDoDiaBrtParaDatetimeLocal`), mas só exporta o último e `atalhosPrazo`.
- `CriarTarefaRequest` já aceita `prazo` (ISO), `duracaoMinutos`, `prioridade` e `tagIds`; o servidor valida duração ≥ 15 e múltipla de 15 (`DuracaoMinutosValida`) e título ≤ 120.
- Tags: `listarTags()` / `criarTag({ nome, cor })` em `tag/api/tagApi.ts`, `TAG_COR_PADRAO`, `TagChip` para exibir.

```
 input (texto) ----> interpretarQuickAdd(texto, { ref, tags, rejeitados })
                             |
                             v
           { titulo, duracao?, prazo?, prioridade?, tags[], erros[] }
                             |
              +--------------+---------------+
              v                              v
     QuickAddChips (exibe / remove /    selects do inline add
     confirma tag nova)                 (prioridade, duração)
              |                              |
              +--------------+---------------+
                             v
          [criarTag p/ confirmadas] --> criarTarefa(body)
```

## Goals / Non-Goals

**Goals:**
- Parser como função pura, determinística por `ref: Date`, testável sem DOM.
- Um único componente de chips reutilizado pelos dois pontos de entrada.
- Nenhuma mudança de backend ou de proxy.

**Non-Goals:**
- Realçar os tokens dentro do próprio input (overlay/contenteditable); os chips abaixo bastam.
- Autocompletar tags enquanto digita `#`.
- Persistir os tokens rejeitados entre criações.

## Decisions

### 1. Parser puro em `tarefa/quickAdd/interpretarQuickAdd.ts`
Assinatura: `interpretarQuickAdd(texto, { ref, tagsCatalogo, rejeitados }) → ResultadoQuickAdd`. Tokeniza por espaços em branco preservando a posição de cada token (índice do token na lista), classifica cada um por regras em ordem (escape → duração → prazo → prioridade → tag) e devolve o título remontado com os tokens não consumidos. Cada token consumido carrega `{ tipo, textoOriginal, indice }` para o chip poder devolvê-lo.
*Alternativa*: regex única sobre a string inteira — descartada porque complica "o último vence" e devolver o texto original ao título.

### 2. Datas reaproveitam `prazoAtalhos.ts`
Extrair os helpers civis BRT de `prazoAtalhos.ts` para `tarefa/dataCivilBrt.ts` (exportados) e generalizar `proximaSegunda` para `proximoDiaSemana(y, m, d, wdIso)` com a regra "estritamente posterior". `atalhosPrazo` passa a usar `proximoDiaSemana(..., 1)`, mantendo seus testes verdes. Na implementação, `weekdayIsoBrt` passou a calcular o dia da semana com `Date.UTC(y, m - 1, d).getUTCDay()` em vez de `Intl` — a entrada já é a data civil BRT, então o resultado é o mesmo, sem depender do fuso do navegador. O parser devolve o prazo como instante ISO de 23:59 BRT (não como datetime-local), já no formato de `CriarTarefaRequest.prazo`.
*Alternativa*: `Date` local do navegador — descartada; o resto do app usa dia civil BRT e o teste de virada de meia-noite exige isso.

### 3. Normalização para comparar tokens
Tokens fixos (dias, prioridades, `hoje`/`amanhã`) são comparados em minúsculas e sem acento (`normalize('NFD')` sem marcas combinantes). Tags são comparadas **só em minúsculas** (`chaveTag`), porque a unicidade de nome no servidor diferencia acentos: casar `#ciencia` com `Ciência` vincularia a tag errada. Tabela de dias: `seg|segunda|segunda-feira`, `ter|terca|terca-feira`, `qua|quarta|quarta-feira`, `qui|quinta|quinta-feira`, `sex|sexta|sexta-feira`, `sab|sabado`, `dom|domingo`. Duração: `^(\d+)h(\d+)(min|m)?$`, `^(\d+)h$`, `^(\d+)(min|m)$`. `dd/mm` com 1–2 dígitos cada e validação de dia existente (via `Date.UTC` round-trip).

### 4. Duração inválida vira chip com erro, não token ignorado
Um `20min` é claramente intenção de duração; ignorá-lo deixaria o token no título sem explicação. O chip mostra a mesma mensagem do formulário ("A duração deve ser no mínimo 15 minutos e múltiplo de 15.") e o submit é bloqueado. O usuário remove o chip (texto volta ao título) ou corrige o texto. Limite superior não é imposto além do que o servidor já faz.

### 5. Tokens rejeitados ficam no componente, identificados pelo texto
Remover um chip adiciona `textoOriginal` normalizado a um `Set` de rejeitados no estado do componente; o parser trata tokens desse conjunto como texto comum. Identificar pelo texto (e não pela posição) sobrevive a edições antes do token. Efeito colateral aceito: se o mesmo texto aparecer outra vez na mesma criação, também fica literal. O conjunto só é limpo ao criar com sucesso, ao fechar o quick add ou ao limpar o campo (Esc no inline add).
*Alternativa*: rejeitar por índice — quebra quando o usuário insere palavras antes do token.

### 6. Escape `\`
Token que começa com `\` é emitido no título sem a barra e nunca é classificado. `\\` não tem tratamento especial.

### 7. Tags: catálogo carregado uma vez por abertura
O quick add carrega `listarTags()` ao abrir; o inline add carrega ao montar o próprio `InlineAddTarefa` (uma vez por montagem da lista), via o mesmo hook `useQuickAdd`, o que evita passar o catálogo por props a partir da `TarefasPage`. Casamento case/acento-insensitive pelo nome. Tag sem par vira chip pendente com botão "criar"; no submit, as confirmadas são criadas em sequência com `TAG_COR_PADRAO` e os ids entram em `tagIds`. Falha em `criarTag` aborta o submit antes de `criarTarefa` (spec: tarefa não é criada). Se `listarTags` falhar, todos os `#nome` ficam pendentes (o 409 de nome duplicado é o pior caso e é exibido como erro).
*Alternativa*: criar a tag no clique de confirmar — descartada porque cria tag no catálogo mesmo se o usuário desistir da tarefa.

### 8. Inline add: chip manda no select
Quando o parser devolve prioridade/duração, o componente faz `setPrioridade`/`setDuracaoMinutos`; ao remover o chip, volta ao padrão. Se o usuário mexer no select depois, o select vence e o chip correspondente some (o token volta ao título como rejeitado). Uma duração válida mas fora da lista do select (ex.: `5h` = 300) é adicionada como opção extra temporária para o select poder exibi-la.

### 9. "Mais detalhes" leva os valores
Estender `TarefaDrawerState` com `duracaoMinutos?`, `prazo?` (ISO), `prioridade?` e `tags?` (`TagResumo[]`, para o picker exibir os nomes sem recarregar); `TarefaDrawer` usa-os no snapshot inicial em modo criar. Tags pendentes não vão.

### 10. Limite do título sem contar os tokens
O campo aceita 200 caracteres (`TEXTO_MAXIMO`) e o limite de 120 do servidor é checado no título restante antes do envio (`ERRO_TITULO_LONGO`, mesma mensagem do formulário completo). Sem isso, um título perto de 120 caracteres não caberia junto com os tokens.

## Risks / Trade-offs

- [Título legítimo com palavra igual a token, ex.: "Reunião de sexta"] → Chip visível + remover devolve ao título; `\sexta` para quem conhece.
- [Fuso do navegador diferente de BRT] → Parser resolve em BRT e devolve instante ISO; exibição do chip usa data civil BRT.
- [Criação de várias tags antes da tarefa deixa tags órfãs se `criarTarefa` falhar] → Aceito: tag criada é inofensiva e reutilizável; a mensagem de erro aparece e o reenvio a encontra no catálogo.
- [Parser roda a cada tecla] → Texto ≤ 120 caracteres; custo desprezível, sem debounce.

## Migration Plan

Só front. Deploy normal da imagem do frontend; rollback = imagem anterior. Antes do deploy, registrar o baseline da métrica (% de tarefas com `duracao_minutos` e `prazo` preenchidos criadas nos últimos 30 dias) para comparar depois.

## Open Questions

- Texto de ajuda/placeholder (ex.: "Ex.: Estudar 2h sex !alta #faculdade") e se vale um link "como funciona" — ajuste de copy, não muda specs.
