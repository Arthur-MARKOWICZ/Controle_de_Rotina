# Feature — Correções pós-revisão UI Hábitos (Home, conflito, form)

Issue: [#43](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/43) (correção UI)

Escopo: corrigir bugs e não conformidades de UI confirmados em `revisao_modulo_habitos.md` (Home/rascunho, conflito, drawer, lista).  
Fora deste slice: motor/`itensNaoAlocados`/streak no back (ver `habitos-correcoes.md`); calendário sem plano; redesign da shell.

Dependências: `ui-habitos.md`, `habitos-correcoes.md` (B4 snapshot conflito, B6 `diasSemana` no resumo). Código UI de hábitos já no repo.

Origem: achados **3, 4, 10, 11, 12, 13, 14, 15, 16, 17** de `revisao_modulo_habitos.md` (confirmados / parcialmente confirmados em 2026-10-31).

---

## Conceito

1. CTA de conflito remove o **bloco** de menor prioridade (tarefa **ou** hábito), nunca no-op quando só há hábitos.
2. Copy do rascunho: `Recorrente · [dias]` com dias reais do hábito.
3. Na execução aprovada, streak do hábito é **visível** e atualiza após concluir quando a API devolve enrich.
4. Adicionar omitido que mantém/gera conflito → **toast** explícito (além do banner).
5. Dirty-check do drawer estável; create preserva `search` da lista; validação com âncora nos campos.
6. Testes da Home cobrem wiring HABITO (não só `BlocoCard` isolado).

---

## Front-end

### Conflito — remover menor prioridade

- `escolherBlocoMenorPrioridade`: considerar blocos com `tarefa` **ou** `habito`.
  - Tarefa: peso por `prioridade` (BAIXA &lt; MEDIA &lt; ALTA); empate → maior `ordem`.
  - Hábito: tratar como prioridade efetiva **MEDIA** (alinhado ao XP do hábito); empate com tarefa MEDIA → preferir remover o de maior `ordem` (ou hábito depois de tarefa no empate de peso — documentar no código em uma linha).
- Label do `ConflitoBanner`: **“Remover bloco de menor prioridade”** (não “tarefa”).
- `handleRemoverMenorPrioridade`: se houver blocos, sempre remove um.

### Rascunho — copy de recorrência

- `HomePage`: `motivoRecorrencia` = `Recorrente · ${rotuloDiasSemana(dias)}` a partir de `bloco.habito.diasSemana` (API após B6 de `habitos-correcoes.md`).
- Remover placeholder `'Recorrente · dias do hábito'`.

### Execução — streak na Home

- `BlocoCard` (origem HABITO): exibir `streakAtual` quando informado (lista aprovada e, se fizer sentido, rascunho).
- Ao concluir: merge otimista / resposta da API atualiza `habitoResumo.streakAtual` no item (não só XP).
- Origem do card aprovado: preferir campo de origem do bloco/`habitoId` quando existir; fallback `habitoResumo` (endurece achado 13).

### Adicionar omitido + conflito

- Após `adicionarHabitoAoPlano` / add tarefa omitida: se `atualizado.temConflito`, `toast` de conflito (copy alinhada a `ui-habitos.md` §5).

### Drawer / form

- Dirty-check: comparar dias com **conjunto ordenado canônico** (ex. ordem `DIAS_SEMANA_ORDEM`), não `JSON.stringify` cru da ordem do array.
- Create: `navigate(\`/habitos/${id}${location.search}\`)` (ou `search: location.search`) para preservar filtro da lista.
- Validação: passar erros por campo a `HabitoCampos`; `aria-invalid` + mensagem local em dias/duração/título; alert pode permanecer como resumo do primeiro erro.

### Exclusão — Desfazer (paridade)

- Toast de exclusão na `HabitosPage` com ação **Desfazer** → `restaurarHabito` (mesmo espírito de `TarefasPage`). Restore na visão Excluídos permanece.

### Testes

- Casos na `HomePage` (ou harness da home): bloco HABITO com copy de dias; banner só-hábitos remove um bloco ao CTA.

---

## Decisões fechadas (revisão)

1. Label e escolha do CTA falam em **bloco**, não só tarefa.
2. Hábito no desempate de “menor prioridade” = peso MEDIA.
3. Streak na Home faz parte do aceite de `ui-habitos.md` §6 (não só módulo `/habitos`).
4. Desfazer no toast de exclusão entra neste slice (paridade com tarefas; achado 16).
5. Achado 13 (origem frágil) → endurecimento leve no wiring; não é redesign do contrato de execução.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Rascunho com bloco HABITO mostra `Recorrente ·` + dias reais (não placeholder) | F1 | Achado 4 |
| T2 | Banner com só hábitos: CTA remove um bloco (não no-op) | F2 | Achado 3 |
| T3 | `escolherBlocoMenorPrioridade` escolhe hábito quando não há tarefa | F2 | Unidade do CTA |
| T4 | Dirty-check: toggle ida-e-volta do mesmo dia não marca alterado | F4 | Achado 12 |
| T5 | Create preserva query `status`/`somenteDeletadas` na URL | F4 | Achado 15 |
| T6 | Após add omitido com `temConflito`, toast de conflito | F3 | Achado 11 |

Harness: Testing Library no padrão `HomePage.test` / `HabitosPage.test`.

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Copy recorrência + streak no card + origem** | `HomePage` / `BlocoCard`; dias via resumo; streak; origem defensiva; **T1** |
| **F2** | **CTA conflito bloco** | `justificativa.ts`, banner label, handler; **T2**, **T3** |
| **F3** | **Toast add omitido com conflito** | `usePlanoDoDia`; **T6** |
| **F4** | **Drawer: dirty, search, validação, Desfazer exclusão** | `HabitoDrawer`, `HabitoCampos`, `HabitosPage`; **T4**, **T5** |

### Ordem

```text
(habitos-correcoes B6, B4) → F1 → F2 → F3 → F4
```

F2/F3 podem começar em paralelo a F1 se mocks de API já tiverem `diasSemana` / `temConflito`.
)
