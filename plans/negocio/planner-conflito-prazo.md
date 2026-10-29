# Feature — Avisar quando a carga não cabe até o prazo

Issue: [#23](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/23)

Escopo: após alocar por score na janela, marcar conflito quando tarefa com prazo na janela ainda tem saldo não alocado até o prazo; mensagem no `Plano` (`mensagem` / `temConflito`) consumível pela UI de revisão (#6).  
Fora deste slice: replanejamento (#26); mudança visual além de exibir a mensagem já prevista em `ui-revisao-rascunho`.

Dependências: `planner-janela-multidia.md` (#22).

---

## Conceito

1. Depois da alocação: para cada tarefa com prazo dentro da janela, comparar saldo com minutos alocados **até o prazo**.
2. Se sobrou saldo → conflito nomeando a tarefa, minutos de fora e o prazo.
3. Quem coube inteira não marca conflito por essa regra.
4. Maior score ocupa capacidade primeiro; a preterida aparece no conflito.
5. Persistência: `temConflito = true` e texto em `mensagem` (campo já existe) — não só toast no front.

---

## Back-end

### Motor

- Passo pós-alocação na geração da proposta.
- Mensagem agregada ou por plano do dia do prazo (se vários conflitos, concatenar de forma legível no(s) plano(s) relevantes — preferência: planos da proposta com `temConflito` e mensagem listando itens).

### Front-end

- Tela de revisão (#6 / `ui-revisao-rascunho`): garantir exibição de `mensagem` / `temConflito` quando vier da API (se já exibe, só validar).

---

## Decisões fechadas (revisão)

1. Conflito = saldo restante após alocação até o prazo.
2. Mensagem no plano; maior score fica alocado.
3. Issue #23.

---

## Testes automatizados

### Unitários

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | 12h trabalho / 8h até o prazo → conflito com tarefa e minutos fora | B1 | Aceite |
| T2 | Tarefa que cabe inteira → sem conflito por ela | B1 | Aceite |
| T3 | Empate de capacidade: maior score alocada; outra no conflito | B1 | Aceite |

### Integração

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T4 | Plano gerado com `temConflito` e `mensagem` preenchidos | B2 | Contrato UI |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T5 | Revisão mostra mensagem de conflito | F1 | Aceite consumo UI |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Detecção no motor** | Pós-alocação; **T1–T3** |
| **B2** | **Persistir mensagem** | `temConflito`/`mensagem` na geração; **T4** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Exibir conflito** | Revisão de rascunho; **T5** |

### Ordem

```text
B1 → B2 → F1
```
