# Proposal

## Why

Desde o épico "tarefas primeiro" dá para criar tarefa só com título, e duração, prazo e prioridade — o que alimenta o motor de planejamento — muitas vezes nunca são preenchidos (issue #74, OPP-017). Interpretar uma linguagem curta no próprio campo de título faz mais tarefas entrarem com o que o planner precisa, sem esforço extra e sem abrir o formulário completo.

## What Changes

- **Parser puro no front** que lê o texto digitado e extrai tokens de duração (`2h`, `30min`, `1h30`), prazo (`hoje`, `amanhã`, dia da semana, `dd/mm`), prioridade (`!alta`, `!media`, `!baixa`) e tags (`#nome`); o restante vira o título.
- **Dia da semana = sempre a próxima ocorrência**: o próprio dia de hoje resolve para +7 dias (`hoje` é o token para o dia corrente). `dd/mm` já passado resolve para o próximo ano.
- **Prazo no fim do dia civil em BRT (23:59)**, igual aos atalhos de prazo existentes. Hora no token (`sex 14h`) fica fora.
- **Pré-visualização em chips** no `QuickAddTarefaDialog` e no `InlineAddTarefa`: nada é enviado sem aparecer como chip. Remover um chip devolve o texto do token ao título e o token não é reinterpretado; `\token` deixa o texto literal.
- **Tag inexistente** aparece como chip "criar #nome?" e só é criada no catálogo se o usuário confirmar; sem confirmar, a tarefa é criada sem ela.
- **Duração fora da regra do servidor** (< 15 ou não múltipla de 15) aparece como chip com erro e bloqueia o envio até ser removida.
- **Inline add**: um token reconhecido atualiza o select correspondente (prioridade/duração), que segue como fonte visível do valor.

Fora do escopo: LLM ou interpretação livre; hora no prazo; atalhos numéricos de prioridade (`!1`); port para o app mobile e dica no MCP (issues separadas); mudanças no backend.

## Capabilities

### New Capabilities
- `ui-tarefa-quick-add`: criação rápida de tarefa por linguagem curta — gramática dos tokens, resolução de datas, pré-visualização em chips, escape e confirmação de tags novas no quick add e no inline add.

### Modified Capabilities
<!-- Nenhuma: o vínculo de tags e o create de tarefa não mudam de regra; ui-tag continua valendo para o TagPicker. -->

## Impact

- **Frontend**: novo módulo puro de parsing em `src/tarefa/` (reaproveitando os helpers de data civil BRT de `prazoAtalhos.ts`), novo componente de chips, `QuickAddTarefaDialog.tsx` e `InlineAddTarefa.tsx` passam a enviar `duracaoMinutos`, `prazo`, `prioridade` e `tagIds`; uso de `listarTags`/`criarTag` existentes.
- **Backend / API**: nenhuma mudança; `POST /tarefas` já aceita todos os campos.
- **Proxy**: nenhum prefixo novo.
- **Métrica**: % de tarefas criadas com duração e prazo preenchidos (baseline na tabela `tarefa` antes do deploy).
