# Feature — Home “Plano de hoje” como agenda (UI/UX · issue #5)

Escopo: redesenhar a Home como agenda do dia — barra de capacidade, cards de bloco unificados, bloco ativo em destaque, saudação/data, empty state; refatorar componentes sem mudar regras de planner/execução.  
Fora deste slice: ritual completo de revisão do rascunho (`ui-revisao-rascunho.md`); quick add (`ui-quick-add.md`); ajuste rápido de capacidade (`ui-capacidade-visual.md`); feedback de XP no concluir (`ui-xp-feedback.md`).

Dependências: `planner.md`, `execucao.md`, `ui-layout-nav.md`, `ui-design-system.md`, `ui-estados-feedback.md`. Issue: #5 · pai #1.

---

## Entities

Nenhuma nova.

---

## Back-end

Sem endpoints novos. Consome contratos já existentes de plano do dia, capacidade resolvida e execuções.

---

## Front-end

1. **Saudação** — data por extenso em pt-BR (ex.: “Terça, 27 de outubro”).
2. **`CapacidadeBar`** — preenchimento usado/disponível; estado de conflito/sobrecarga com token `--danger`; texto acessível equivalente (não só cor).
3. **`BlocoCard`** — um componente para rascunho e aprovado: título, duração, chip de prioridade (tokens + label PT), status com ícone/texto (sem enum cru `ALTA` / `EM_PROGRESSO`).
4. **`BlocoAtivo`** — se houver execução em andamento: destaque no topo (“Agora: … · tempo restante”), barra de progresso, ações Concluir/Abortar (Abortar via dialog de `ui-estados-feedback.md`).
5. **Empty state** — explica o fluxo “Gere o plano → revise → aprove → execute” com CTA adequado.
6. **Refator** — extrair de `HomePage` (~580 linhas / muitos `useState`) para os componentes acima + formulário de adicionar tarefa existente; **sem** mudar regras de negócio.
7. **Loading** — skeleton de lista de blocos.

---

## Decisões fechadas (revisão)

1. Labels de prioridade/status sempre em português na UI; valor de API continua no enum.
2. Um card para rascunho e aprovado (diferenças só nas ações disponíveis).
3. Sem mudança de API; só apresentação.
4. Testes de fluxo da Home (gerar/aprovar/remover/adicionar/iniciar/concluir/abortar) permanecem a rede de segurança.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Capacidade renderiza barra + texto acessível com usados/disponíveis | F1 | Critério da issue |
| T2 | Prioridade/status exibem label PT (não enum cru) | F2 | UX / consistência |
| T3 | Bloco em andamento aparece em destaque com tempo restante | F3 | Núcleo da agenda |
| T4 | Suites existentes de `HomePage` (gerar/aprovar/remover/adicionar/iniciar/concluir/abortar) continuam verdes | F4 | Regressão |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **CapacidadeBar + saudação** | Barra + data por extenso; **T1** |
| **F2** | **BlocoCard unificado** | Chip prioridade/status PT; uso em rascunho e aprovado; **T2** |
| **F3** | **BlocoAtivo** | Destaque, progresso, ações; **T3** |
| **F4** | **Empty state + limpeza HomePage** | Empty; skeletons; reduzir estado local; **T4** |

### Ordem

```text
F1 → F2 → F3 → F4
```
