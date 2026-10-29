# Feature — Permitir tarefa sem prazo

Issue: [#27](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/27)

Escopo: `prazo` opcional; sem prazo → urgência 0 no score; só entra na capacidade que sobrou após tarefas com prazo; não gera conflito de prazo; nunca vira `ATRASADA` no job. Duração e prioridade obrigatórias.  
Fora deste slice: recorrência/hábitos (visão §8).

Dependências: `tarefa.md`, motor com janela (`planner-janela-multidia.md` #22); atraso automático (#25) se já existir.

---

## Conceito

1. `prazo` nullable no banco e na API.
2. Score: urgência de prazo = 0 quando `prazo == null`.
3. Alocação: duas fases (ou fila ordenada) — primeiro tarefas **com** prazo (por score); depois sem prazo no restante da capacidade.
4. Conflito “não cabe até o prazo” (#23): não se aplica a tarefa sem prazo.
5. Job de atraso: ignora `prazo == null`.

---

## Entity / API

| Campo | Tipo | Observação |
|-------|------|------------|
| prazo | Instant nullable | opcional no create/update |

- Create/update: omitir ou `null` válido; se presente, regras de passado/atrasada continuam.
- Response: `prazo` pode ser null.
- Flyway: dropar `NOT NULL` de `prazo`.

### Front-end

- Quick-add e formulário: prazo opcional (checkbox “sem prazo” ou campo vazio).
- Lista: grupo/ordenação com sem prazo no fim ou seção própria — mínimo: não quebrar sort nulls.

---

## Decisões fechadas (revisão)

1. Prazo opcional; duração e prioridade obrigatórias.
2. Urgência 0; aloca depois das com prazo; sem conflito de prazo; sem `ATRASADA` automática.
3. Issue #27.

---

## Testes automatizados

### API / motor

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Criar tarefa sem prazo → 201 | B2 | Aceite |
| T2 | Capacidade apertada: com prazo ocupa antes da sem prazo | B3 | Aceite |
| T3 | Sem prazo não aparece como estourada/atrasada (job + conflito) | B3 | Aceite |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T4 | Form permite salvar sem prazo | F1 | Fluxo visão |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Flyway + entity** | `prazo` nullable |
| **B2** | **API create/update** | Validação; **T1** |
| **B3** | **Score + motor + atraso** | Urgência 0; ordem alocação; job ignora; **T2–T3** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Prazo opcional na UI** | Quick-add + form; **T4** |

### Ordem

```text
B1 → B2 → B3 → F1
```
