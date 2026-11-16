# Tasks

Regra de testes do projeto: cada unidade com lógica tem ≥ 7 cenários (1 feliz + 3 anomalias + 3 erros), com `ref: Date` fixo em BRT nos limites (meia-noite, virada de semana/ano). Front: `cd frontend/frontend-web && npm run test:coverage` e `npm run build` (o `tsc -b` faz typecheck dos testes; mocks com `vi.mocked(...)`, nunca `.mock` no export tipado). Sem mudança de backend nem de proxy.

## 1. Datas civis em BRT

- [x] 1.1 Extrair de `tarefa/prazoAtalhos.ts` para `tarefa/dataCivilBrt.ts` os helpers `partesCivisBrt`, `adicionarDiasCivil`, `weekdayIsoBrt` e `fimDoDiaBrtParaDatetimeLocal`, e adicionar `proximoDiaSemana(y, m, d, wdIso)` (estritamente posterior) e `fimDoDiaBrtIso(y, m, d)` (instante ISO de 23:59 BRT). Fazer `atalhosPrazo` usar `proximoDiaSemana(..., 1)`. Verificar com `prazoAtalhos.test.ts` verde sem alteração e `dataCivilBrt.test.ts` (≥7): sex→sex+7, qua→sex mesma semana, dom 23:59 BRT → seg seguinte, seg 00:00 BRT (03:00 UTC) → seg+7, virada de mês, virada de ano (31/12 → qui de janeiro), wdIso fora de 1..7 lança `RangeError`

## 2. Parser de linguagem curta

- [x] 2.1 Criar `tarefa/quickAdd/interpretarQuickAdd.ts` com os tipos `TokenQuickAdd`, `ResultadoQuickAdd` e a função pura `interpretarQuickAdd(texto, { ref, tagsCatalogo, rejeitados })` cobrindo tokenização com índice, normalização (minúsculas, sem acento), escape `\`, duração (com erro para < 15 ou não múltiplo de 15), prazo (`hoje`, `amanhã`, dias da semana em todas as formas, `dd/mm` com rollover de ano e data inexistente literal), prioridade, tags (casamento com o catálogo, deduplicação, pendentes), "último vence" e título remontado. Verificar com `interpretarQuickAdd.test.ts` com ≥ 20 casos, incluindo todos os cenários do spec `ui-tarefa-quick-add` sobre parsing: frase completa; `sexologia2h` literal; só tokens → título vazio; `1h30`/`1h30min`/`90m`; `20min` com erro; `0h` com erro; `hoje`/`amanhã`/`amanha`; `sab`/`sáb`/`sábado`/`sexta-feira`; mesmo dia → +7; virada dom→seg em BRT; `10/01` no ano seguinte; `31/02` literal; `!alta`/`!média`; `!agora` literal; `1h 2h` → 2h com `1h` no título; tags repetidas com caixa diferente; `\2h` e `\#1` literais; token em `rejeitados` literal; texto vazio/só espaços; espaços múltiplos normalizados

## 3. Componente de chips

- [x] 3.1 Criar `tarefa/quickAdd/QuickAddChips.tsx` que exibe os tokens do resultado (duração formatada, prazo como `dia dd/mm`, prioridade com `PRIORIDADE_LABEL`, tags com `TagChip`), com botão de remover acessível em cada chip, chip de erro com a mensagem de duração e chip pendente "criar #nome?" com ação de confirmar/desfazer. Verificar com `QuickAddChips.test.tsx` (≥7): renderiza os quatro tipos, remover chama `onRemover` com o token, confirmar tag pendente chama `onConfirmarTag`, chip de erro tem `role="alert"` com a mensagem exata, lista vazia não renderiza nada, nome de botão acessível por chip ("Remover duração 2h"), tag pendente confirmada mostra estado confirmado e pode ser desfeita

## 4. Quick add

- [x] 4.1 Ligar o parser e os chips no `QuickAddTarefaDialog`: carregar `listarTags` ao abrir, rodar o parser a cada mudança com `ref = new Date()` injetável por prop para teste, manter o `Set` de rejeitados e as tags confirmadas no estado (limpos ao abrir), bloquear o submit com duração em erro ou título vazio, criar as tags confirmadas com `TAG_COR_PADRAO` antes de `criarTarefa` e enviar `titulo`, `duracaoMinutos`, `prazo`, `prioridade` e `tagIds`. Verificar em `QuickAddTarefaDialog.test.tsx` (somando aos testes atuais, ≥7 novos): frase completa envia todos os campos; remover chip devolve o token ao título e não reinterpreta ao continuar digitando; tag existente vai em `tagIds` sem `criarTag`; tag pendente não confirmada não chama `criarTag` e não vai no body; tag confirmada chama `criarTag` e depois `criarTarefa` com o id novo; `listarTags` falhando deixa as tags pendentes e o resto funciona; `20min` mostra erro e não chama `criarTarefa`; `criarTag` falhando mostra a mensagem do servidor e não chama `criarTarefa`; `criarTarefa` 400 mostra a mensagem e mantém texto e chips; reabrir o diálogo limpa rejeitados
- [x] 4.2 Estender `TarefaDrawerState` com `duracaoMinutos?`, `prazo?`, `prioridade?` e `tags?`, usar esses valores no snapshot inicial do `TarefaDrawer` em modo criar e fazer "Mais detalhes" passar o resultado interpretado (só tags existentes). Verificar em `QuickAddTarefaDialog.test.tsx` que o `state` navegado tem os valores e não tem a tag pendente, e em `TarefaDrawer.test.tsx` que o formulário abre pré-preenchido (título, duração, prazo convertido para datetime-local, prioridade, tag) e que sem esses campos continua igual ao atual

## 5. Inline add

- [x] 5.1 Ligar o parser e os chips no `InlineAddTarefa`: catálogo de tags carregado uma vez ao montar, token de prioridade/duração atualiza o select, remover chip volta o select ao padrão, mexer no select depois de um token rejeita o token (volta ao título), duração válida fora da lista vira opção extra no select, Esc limpa texto, chips e rejeitados; envio com os mesmos campos e regras de tag do quick add. Verificar em `InlineAddTarefa.test.tsx` (somando aos atuais, ≥7 novos): `!alta` muda o select e envia `ALTA`; `2h` muda o select de duração e remover volta a 30 min; `5h` aparece no select e envia 300; mudar o select após `!alta` remove o chip e o título mantém `!alta`; tag pendente sem confirmação não é criada; `20min` bloqueia o envio com a mensagem; `criarTarefa` 500 mantém texto e chips e mostra a mensagem genérica; após criar com sucesso, o campo, os chips e os rejeitados são limpos
- [ ] 5.2 Atualizar o placeholder dos dois campos com um exemplo da sintaxe (ex.: "Ex.: Estudar 2h sex !alta #faculdade") e conferir no app rodando (`npm run dev` com backend local): criar pelo quick add e pelo inline add com a frase completa, remover um chip, confirmar uma tag nova e abrir "Mais detalhes". Verificar em desktop e em 375 px que os chips quebram linha sem estouro horizontal

## 6. Cobertura, docs e fechamento

- [x] 6.1 Rodar `cd frontend/frontend-web && npm run test:coverage && npm run build`; se o piso medido subir, elevar `test.coverage.thresholds` em `vite.config.ts` (hoje 86 linhas / 81 branches) arredondando para baixo. Verificar que o gate passa com os limites novos e que o build Docker não quebra no typecheck dos testes
- [x] 6.2 Documentar a sintaxe em `plans/ui/ui-quick-add.md` (tokens, regra de dia da semana, escape, tags pendentes) e marcar a issue #74 em `ordem_execucao/docs.md`. Verificar com o diff dos dois arquivos

## Workflow follow-up

- Registrar o baseline da métrica (% de tarefas com duração e prazo nos últimos 30 dias) antes do deploy.
- Mobile e MCP: abrir issues separadas para portar o parser/dica (fora do escopo da #74).
- Arquivar esta change depois da revisão (`/openspec-archive-change`).
