# Feature — Distribuir a tarefa pelos próximos dias

Issue: [#22](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/22)

Escopo: geração de proposta em **janela** (hoje → prazo mais distante das elegíveis, teto 14 dias); uma tarefa vira blocos em dias distintos; capacidade por dia; sem alocar após o prazo nem em capacidade 0.  
Fora deste slice: mensagem de conflito “não cabe até o prazo” (#23); teto 90 min (#24); aceite de replanejamento (#26). Ainda sujeito a aprovação — não gravar janela como `APROVADO` sozinho.

Dependências: `planner-saldo-restante.md` (#21), `capacidade.md`.

---

## Conceito

1. Uma **proposta** gera um `Plano` (rascunho) por dia da janela, mesma “rodada” de geração (versões independentes por `(usuario, data)`).
2. Alocação consome o **saldo** da tarefa ao longo dos dias; soma dos blocos na janela ≤ saldo.
3. Não alocar em dia com capacidade 0; não alocar depois do prazo da tarefa.
4. Minutos que não cabem até o prazo **não** são alocados depois do prazo (somem em silêncio só até #23 transformar isso em conflito explícito — neste slice: não alocar além do prazo).

---

## Entities

Reutilizar `Plano` / `BlocoPlano`. Sem entity nova obrigatória.  
Opcional interno: correlacionar planos da mesma proposta (só se necessário para UI/aprovação multi-dia; se não houver campo, a UI lista por intervalo de datas). **Decisão deste slice:** não criar tabela de “Proposta” ainda — correlacionar por `geradoEm` próximo / geração na mesma chamada. Se a aprovação multi-dia exigir id comum, introduzir `propostaId` (UUID) em `Plano` na mesma B1.

**Decisão fechada:** adicionar `propostaId` (UUID, nullable em planos antigos) em `Plano` para agrupar dias da mesma geração.

---

## Back-end

### Geração

- Janela: `hoje` … `min(hoje+13, max(prazo das elegíveis em dias BRT))` — teto **14 dias** (hoje + 13).
- Por dia: capacidade resolvida; alocar por score (Score V1 vigente); respeitar saldo global da tarefa na proposta.
- Endpoint atual de gerar (e job): passam a gerar a janela, não só um dia.
- Job 06:00: para cada usuário, se **hoje** já tem `APROVADO`, skip (regra atual do dia); não auto-aprovar a janela.

### Consulta

- Estender ou documentar como a home/lista obtém planos do intervalo (GET por data continua; front pode buscar N dias). Sem prefixo novo se paths `/planos` já existirem.

### Front-end

- Home/revisão: permitir ver rascunhos dos próximos dias da proposta (mínimo: navegação por data já existente + indicação de proposta).
- Ajuste fino de UI multi-dia fica alinhado a `ui-home-agenda` / `ui-revisao-rascunho` — neste slice: não quebrar aprovação do dia; se `propostaId` existir, aprovar pode ser por dia (MVP atual) até #26.

---

## Decisões fechadas (revisão)

1. Janela até prazo mais longe, teto 14 dias.
2. Vários blocos / dias; soma ≤ saldo; capacidade 0 sem bloco; nada após o prazo.
3. `propostaId` agrupa planos da geração; status continua `RASCUNHO` até aprovação do usuário.
4. Issue #22.

---

## Testes automatizados

### Unitários

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Tarefa 3h, prazo sexta, 1h/dia → blocos em dias distintos somando 3h | B2 | Aceite |
| T2 | Soma na janela ≤ saldo | B2 | Aceite |
| T3 | Capacidade 0 → sem bloco naquele dia | B2 | Aceite |
| T4 | Não aloca depois do prazo | B2 | Aceite |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T5 | POST gerar cria planos `RASCUNHO` em vários dias com mesmo `propostaId` | B3 | Contrato |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Flyway `propostaId`** | Coluna em `plano`; repository; sem endpoint |
| **B2** | **Motor janela** | Alocação multi-dia + saldo; **T1–T4** |
| **B3** | **Geração HTTP + job** | Usar motor de janela; **T5** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Leitura multi-dia** | Exibir/navegar rascunhos da janela sem regressão na aprovação do dia |

### Ordem

```text
B1 → B2 → B3 → F1
```
