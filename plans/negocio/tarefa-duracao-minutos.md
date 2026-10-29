# Feature — Duração em minutos e teto de bloco

Issue: [#24](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/24)

Escopo: duração estimada da tarefa em **minutos** (mín. 15, múltiplo de 15); bloco máximo 90 min; bloco mínimo 30 min (exceto saldo restante menor); sobra de capacidade &lt; 30 min não vira bloco.  
Fora deste slice: hábitos; mudança de fórmula de XP (#28) além de ler minutos.

Dependências: `tarefa.md`, `planner.md`; idealmente após `planner-janela-multidia.md` (#22) para o motor já multi-dia.

---

## Conceito

1. API e entity: `duracaoMinutos` substitui `duracaoHoras`.
2. Validação: `>= 15` e `% 15 == 0`; 10 min → rejeitado; 90 min → ok.
3. Motor: ao alocar, pedaços de no máx. **90** min; mínimo **30** salvo saldo da tarefa &lt; 30.
4. Capacidade restante &lt; 30: não abre bloco; saldo da tarefa segue para outro dia ou conflito de prazo (#23).

---

## Entity / API

### Tarefa

| Campo | Tipo | Observação |
|-------|------|------------|
| duracaoMinutos | int | obrigatório; ≥ 15; múltiplo de 15 |

Flyway: migrar `duracao_horas` → `duracao_minutos` (`* 60`).  
DTOs request/response: `duracaoMinutos` (remover `duracaoHoras` do contrato público).  
Filtros/sort `duracao*` passam a minutos.

### Motor

- Teto bloco: 90.
- Piso bloco: 30, exceto saldo restante da tarefa &lt; 30 (aí o bloco pode ser 15 ou 20 etc., desde que múltiplo coerente com o saldo já validado).
- Uma tarefa grande no mesmo dia: vários blocos ≤ 90 (quebra), não um bloco de 3h.

---

## Back-end

- CRUD tarefa + todos os consumidores (`MotorAlocacao`, execução/finalização por soma de minutos, XP bônus se ainda usar estimativa, histórico se agregar duração).
- Capacidade permanece em horas na entity de capacidade (converter `* 60` no motor como hoje), salvo decisão prévia em contrário — **não** mudar capacidade neste slice.

## Front-end

- Quick-add, formulário tarefa, lista: campo duração em minutos (step 15).
- Textos “Xh” → minutos ou horas+minutos legíveis.
- Proxy: sem prefixo novo.

---

## Decisões fechadas (revisão)

1. `duracaoMinutos`: mín 15, múltiplo de 15.
2. Bloco máx 90; mín 30 exceto saldo menor; capacidade &lt; 30 não vira bloco.
3. Breaking change de API `duracaoHoras` → `duracaoMinutos` com migration.
4. Issue #24.

---

## Testes automatizados

### Unitários / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | 90 min válido; 10 min rejeitado | B2 | Aceite |
| T2 | Tarefa 3h (180) em dia 4h → não um único bloco de 180; pedaços ≤ 90 | B3 | Aceite |
| T3 | Capacidade restante 15 → sem bloco | B3 | Aceite |
| T4 | Saldo 20 → bloco &lt; mínimo 30 permitido | B3 | Aceite |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T5 | Formulários enviam `duracaoMinutos` | F1 | Contrato |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Flyway + entity** | Migrar coluna; sem endpoint |
| **B2** | **API tarefa** | DTOs/validações; **T1** |
| **B3** | **Motor teto/piso** | Alocação; **T2–T4** |
| **B4** | **Consumidores** | XP/histórico/execução/ITs que ainda falem em horas |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Campos duração** | Quick-add, form, lista; **T5** |

### Ordem

```text
B1 → B2 → B3 → B4 → F1
```
