# Feature — Jobs de geração diária e atraso resilientes

Issue: [#19](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/19)

Escopo: paginar usuários no job 06:00; advisory lock entre instâncias; log completo de falha por usuário; centralizar fuso em `PeriodoBRT`; mover `@Scheduled` de atraso para componente de job (regra permanece em `TarefaService`).  
Fora deste slice: mudar critério de atraso para “início do dia” (#25); replanejamento com proposta (#26); multi-dia (#22).

Dependências: `planner.md`, `tarefa.md`; idealmente após `concorrencia-escritas.md` (#17) para versão de plano sob corrida.

---

## Conceito

1. Job diário não carrega `findAll()` de usuários — páginas.
2. Duas instâncias da API: só uma executa a rodada (advisory lock Postgres); a outra sai sem gerar de novo.
3. Falha de um usuário: loga exceção completa e segue; não interrompe os demais.
4. Fuso de negócio único: `PeriodoBRT` (job + `ExecucaoService` deixam de declarar zona própria).
5. Agendamento de atraso fora do service de CRUD; efeito atual de `processarAtrasos` mantido até #25 alterar a regra.

---

## Back-end

### PlannerGeracaoDiariaJob

- Buscar usuários paginado.
- No início: `pg_advisory_lock` (ou `try_lock`); se não obteve, return.
- Por usuário: try/catch com `log.error(..., ex)`; chamar `PlannerGeracaoService.gerar` (transação já existente).
- Skip se já há plano `APROVADO` no dia (regra atual).

### Atraso

- Novo job (ex. `TarefaAtrasoJob`) com o cron atual (30 min) chama método de serviço **sem** `@Scheduled` no `TarefaService`.
- Isolamento de falha: não marcar tarefa errada se um batch falhar; logar exceção.

### PeriodoBRT

- Job e `ExecucaoService` usam `PeriodoBRT` como única fonte de zona.

### Front-end

- Nenhuma mudança de UI.

---

## Decisões fechadas (revisão)

1. Paginação de usuários; sem `findAll()`.
2. Advisory lock entre instâncias; sem componente de cache.
3. Log com stack completa; continua nos demais usuários.
4. Atraso: job separado; efeito atual preservado neste slice.
5. `PeriodoBRT` único; issue #19.

---

## Testes automatizados

### Job / integração

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Usuário com plano aprovado é pulado | B1 | Regressão geração |
| T2 | Falha simulada num usuário não impede o seguinte; log/caminho de catch exercitado | B1 | Aceite resiliência |
| T3 | `processarAtrasos` (via job novo): pendente vencida → atrasada; atrasada com prazo futuro → pendente | B2 | Aceite efeito |
| T4 | Duas execuções sobrepostas do job não geram dois rascunhos (lock ou skip) — teste com lock mockado/DB | B3 | Aceite lock |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Job geração paginado + log** | Páginas; catch com ex; **T1–T2** |
| **B2** | **Job atraso separado** | Mover schedule; **T3** |
| **B3** | **Advisory lock + PeriodoBRT** | Lock no job; unificar fuso; **T4** |

### Ordem

```text
B1 → B2 → B3
```
