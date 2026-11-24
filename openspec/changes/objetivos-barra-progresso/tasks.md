# Tasks

Os comandos rodam a partir de `frontend/frontend-web`. Nos testes, mock sempre com `vi.mocked(...)`. Cada unidade com lógica segue o 1+3+3.

Pré-requisito: a change `objetivos-itens-vinculados-ui` precisa estar arquivada antes do apply (ver `design.md` › Migration Plan).

## 1. Normalização e componente

- [x] 1.1 Criar `src/objetivo/progressoObjetivo.ts` com `normalizarProgresso(progresso)`, conforme a decisão 2 do design. Verificar em `progressoObjetivo.test.ts`:
  - feliz: 40%, 120/300 → `{ pct: 40, texto: "2h de 5h concluídas" }`;
  - anomalias: 0% (0/90 → "0min de 1h 30min concluídas"), 100%, percentual 130 → 100, percentual -5 → 0, percentual 40.6 → 41;
  - "sem progresso" (`null`): `progresso` nulo, `minutosTotais` 0, `minutosTotais` negativo.

  Rodar `npx vitest run src/objetivo/progressoObjetivo`.
- [x] 1.2 Criar `src/objetivo/ObjetivoProgresso.tsx` (props `titulo`, `progresso`, `tamanho`, `semProgresso`) com a marcação da decisão 4. Verificar em `ObjetivoProgresso.test.tsx`:
  - `getByRole('progressbar', { name: /Perder 5 kg/ })` com `aria-valuenow` 40, `aria-valuemin` 0, `aria-valuemax` 100 e `aria-valuetext` "40%, 2h de 5h concluídas";
  - largura do preenchimento em 0%, 100% e com clamp (130 → 100%, -5 → 0%);
  - nulo com `semProgresso` → mostra o nó e `queryByRole('progressbar')` é nulo;
  - nulo sem `semProgresso` → container vazio;
  - `minutosTotais` 0 com objeto presente → igual ao nulo;
  - legenda visível com `aria-hidden`.

## 2. Card na lista

- [x] 2.1 Em `ObjetivoCard.tsx`, trocar o bloco `data-testid="objetivo-progresso"` por `<ObjetivoProgresso tamanho="compacto" semProgresso="Vincule tarefas, hábitos ou projetos para medir o progresso." />`. Em `ObjetivoCard.test.tsx`:
  - substituir o cenário "espaço de progresso não mostra percentual nem barra" pela nova versão de "Progresso ainda indisponível" (progresso nulo → convite, sem `progressbar`);
  - adicionar "Card com progresso" (40%, "2h de 5h concluídas");
  - adicionar "Concluído mantém o progresso congelado" (60% na visão Concluídos);
  - adicionar arquivado com barra dentro de `.objetivo-card--arquivado`.

  Rodar `npx vitest run src/objetivo/ObjetivoCard`.
- [x] 2.2 Em `ObjetivosPage.test.tsx`, adicionar um cenário com dois objetivos na lista, um com progresso e outro nulo: só um `progressbar` aparece, e o nome dele cita o título certo.

## 3. Detalhe do objetivo

- [x] 3.1 Exportar de `ObjetivoItensSecao.tsx` um helper `totalItens(itens)` e usá-lo dentro da própria seção, sem mudar o comportamento. Verificar que `ObjetivoItensSecao.test.tsx` continua verde.
- [x] 3.2 Em `ObjetivoDetalhePage.tsx`, renderizar `<ObjetivoProgresso tamanho="destaque" />` entre o `header` (e o alerta de ação) e `ObjetivoItensSecao`, com `semProgresso` calculado como na decisão 3. Verificar em `ObjetivoDetalhePage.test.tsx`:
  - "Detalhe com progresso": 80%, "2h de 2h 30min concluídas", e a barra vem antes do título da seção de itens;
  - "Itens que não somam tempo": progresso nulo com um hábito → texto "Os itens vinculados ainda não somam tempo…", sem `progressbar`;
  - "Objetivo sem itens não repete o convite": progresso nulo e itens vazios → nenhum texto de progresso, e só um convite de vínculo na página;
  - "Falha nos itens não inventa explicação": progresso nulo e `listarItensObjetivo` rejeitando → nenhum texto de progresso;
  - itens ainda carregando (promise pendente) → nenhum texto de progresso;
  - "Concluir atualiza o progresso": `concluirObjetivo` resolve com 55% → a barra passa de 50 para 55;
  - salvar no drawer de edição relê o objetivo e a barra mostra o novo valor.

  Rodar `npx vitest run src/objetivo/pages/ObjetivoDetalhePage`.

## 4. Estilo

- [ ] 4.1 Em `styles/objetivos.css`, trocar `.objetivo-card__progresso` (tracejado) por `.objetivo-progresso` com as variantes `--compacto` e `--destaque`, `__track`, `__fill`, `__legenda` e `__vazio`, conforme a decisão 5. Incluir `transition: none` em `prefers-reduced-motion` e manter a `min-height` do card. Verificar no navegador (`npm run dev`) com objetivos de 0%, 40%, 100% e nulo, nas três visões e no detalhe, em largura desktop e 375px: sem salto de altura entre cards e legenda quebrando linha sem overflow.

## 5. Integração

- [x] 5.1 Rodar `npm run build` (o `tsc -b` checa os testes) e `npm run test:coverage`. Os dois precisam passar. Se a cobertura medida subir, elevar `test.coverage.thresholds` em `vite.config.ts` para o novo piso (arredondado para baixo).

## Workflow follow-up

- Arquivar `objetivos-itens-vinculados-ui` antes do apply desta change.
- Depois do merge, marcar os critérios da issue #94 e o item "Barra de completude do objetivo" da #65.
- Arquivar esta change depois da revisão.
