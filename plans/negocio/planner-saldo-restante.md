# Feature — Planejar pelo saldo restante da tarefa

Issue: [#21](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/21)

Escopo: o motor de alocação usa **saldo** (estimativa − minutos de blocos com execução `CONCLUIDO`), não a duração cheia a cada geração.  
Fora deste slice: janela multi-dia (#22), teto de bloco (#24), XP (#28).

Dependências: `planner.md`, `execucao.md`.

---

## Conceito

1. `saldoMinutos = duracaoEstimadaMinutos − soma(duracaoMinutos dos BlocoPlano da tarefa com Execucao CONCLUIDO)`.
2. Enquanto #24 não migrar a API, `duracaoEstimadaMinutos = duracaoHoras * 60` (como hoje no motor).
3. Saldo 0 → tarefa fora da geração (já coberta ou equivalente a finalizada para o motor).
4. `ABORTADO` não reduz saldo. Bloco só no plano (sem `CONCLUIDO`) não conta como feito.

---

## Entities

Sem entity nova. Leitura via `ExecucaoRepository` (já soma concluídos) + duração da tarefa.

---

## Back-end

### Motor / geração

- Antes de alocar: calcular saldo por tarefa elegível.
- Alocar `saldo` (não `duracaoHoras * 60` cheio).
- Saldo ≤ 0: excluir da fila de elegíveis da geração.

### Front-end

- Nenhuma mudança obrigatória (comportamento do plano melhora transparentemente).
- Opcional depois: mostrar “restante” na UI — fora deste slice.

---

## Decisões fechadas (revisão)

1. Saldo = estimativa − minutos de execuções `CONCLUIDO` da tarefa.
2. Abortado não reduz; bloco não concluído não reduz.
3. Saldo 0 → inelegível na geração.
4. Issue #21; primeiro slice do épico #20.

---

## Testes automatizados

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Tarefa 3h com 60 min concluídos → aloca 120 min | B1 | Aceite |
| T2 | Saldo 0 → não elegível | B1 | Aceite |
| T3 | Execução abortada não reduz saldo | B1 | Aceite |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T4 | Gerar plano após concluir bloco parcial: novo rascunho usa saldo | B2 | Ponta a ponta |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Saldo no motor** | Calcular saldo; filtrar elegíveis; **T1–T3** |
| **B2** | **Integrar geração** | `PlannerGeracaoService` / IT; **T4** |

### Ordem

```text
B1 → B2
```
