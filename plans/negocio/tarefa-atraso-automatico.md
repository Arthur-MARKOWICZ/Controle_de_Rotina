# Feature — Marcar atraso quando o prazo vence

Issue: [#25](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/25)

Escopo: no job diário (fuso BRT), `PENDENTE` ou `EM_PROGRESSO` com prazo anterior ao **início do dia** → `ATRASADA`; não tocar `FINALIZADA`/`CANCELADA`/deletadas; prazo futuro não fica `ATRASADA` via PATCH (regra já existente). Tarefa atrasada continua elegível a plano/execução.  
Fora deste slice: resiliência/paginação do job (#19) — reutilizar o job de atraso já separado se #19 feito; sem prazo (#27).

Dependências: `tarefa.md`; `job-geracao-atraso.md` (#19) se já existir o job dedicado.

---

## Conceito

1. Critério temporal: prazo &lt; início do dia corrente em `America/Sao_Paulo` (`PeriodoBRT`).
2. Status elegíveis à transição automática: `PENDENTE`, `EM_PROGRESSO`.
3. Score V1 já soma +40 para `ATRASADA` — este slice faz o status acompanhar a realidade do prazo.
4. Manter `AtrasadaComPrazoFuturoException` no PATCH.
5. Se o job a cada 30 min ainda reverter `ATRASADA`→`PENDENTE` quando prazo ≥ agora: alinhar para **não** desfazer atraso só porque o relógio passou minutos — a regra deste slice é **diária por início do dia**. Remover ou ajustar a reversão automática por “prazo no futuro” no job periódico: reversão só quando o **usuário** move o prazo (PATCH), não pelo scheduler.  
   **Decisão fechada:** job diário aplica atraso; job de 30 min **não** rebaixa `ATRASADA`→`PENDENTE` automaticamente (PATCH continua fazendo isso se o prazo for para o futuro).

---

## Back-end

- `TarefaAtrasoJob` (ou geração diária acoplada): uma vez por dia no cron alinhado (pode ser o mesmo horário 06:00 ou job dedicado diário) marca atrasos.
- Remover do ciclo de 30 min a lógica conflitante, ou reduzir o job de 30 min ao que ainda fizer sentido — preferência: **só job diário** para atraso por prazo vencido.
- Elegibilidade planner/execução: `ATRASADA` já entra — sem mudança.

### Front-end

- Lista/home já mostram status atrasada; sem feature nova obrigatória.

---

## Decisões fechadas (revisão)

1. Job diário BRT: `PENDENTE`/`EM_PROGRESSO` + prazo antes do início do dia → `ATRASADA`.
2. Não altera finalizada/cancelada/deletada.
3. Scheduler não reverte atraso; PATCH com prazo futuro sim.
4. Issue #25.

---

## Testes automatizados

### Job / unitário

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `PENDENTE` prazo ontem → `ATRASADA` sem ação manual | B1 | Aceite |
| T2 | `FINALIZADA` / `CANCELADA` não mudam | B1 | Aceite |
| T3 | Prazo futuro não é marcado atrasado pelo job; PATCH atrasada+prazo futuro continua rejeitado ou rebaixa conforme regra PATCH | B1 | Aceite |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Regra + job diário** | Transição; alinhar/remover job 30 min conflitante; **T1–T3** |

### Ordem

```text
B1
```
