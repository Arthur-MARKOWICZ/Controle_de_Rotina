# Feature — XP pelo tempo cumprido (sem premiar estimativa inflada)

Issue: [#28](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/28)

Escopo: XP do bloco = peso × minutos **efetivos**; efetivos = min(duração do bloco, tempo entre `iniciadoEm` e `concluidoEm`) com tolerância 80% (credita bloco cheio se decorrido ≥ 80%); abaixo disso credita só o decorrido (segundos → ~0). Bônus de tarefa finalizada usa soma dos minutos efetivos já creditados, não a estimativa crua. Pesos de prioridade inalterados.  
Fora deste slice: dificuldade separada; streaks; estorno.

Dependências: `xp.md`, `execucao.md` (`iniciadoEm`/`concluidoEm`); idealmente após `tarefa-duracao-minutos.md` (#24) se bônus/leituras já estiverem em minutos.

---

## Conceito

```text
decorridoMin = minutos entre iniciadoEm e concluidoEm (floor ou round — half-up em minutos)
se decorridoMin >= 0.8 * bloco.duracaoMinutos:
  efetivos = bloco.duracaoMinutos
senão:
  efetivos = min(bloco.duracaoMinutos, decorridoMin)

XP_bloco = round(efetivos × peso(prioridade))
```

- Se `XP_bloco == 0`: não grava evento (como hoje quando quantidade 0).
- Bônus `TAREFA_FINALIZADA`: `round(somaEfetivosCreditadosDaTarefa × peso × 0.1)` onde a soma é dos minutos efetivos usados nos eventos/bloco concluídos da tarefa (não `duracaoMinutos` estimada da tarefa).

---

## Back-end

### XpMotor

- Nova assinatura: recebe duração do bloco + instante início/fim (ou minutos efetivos já resolvidos).
- Atualizar testes de #18 se fórmulas mudarem.

### Concessão

- Ao concluir: calcular efetivos a partir da `Execucao`; creditar; acumular base do bônus.

### Front-end

- Toast `+N XP` já existe; pode mostrar menos XP — sem mudança obrigatória de layout.
- Opcional: texto “tempo efetivo” — fora do mínimo.

---

## Decisões fechadas (revisão)

1. Efetivos = min(bloco, decorrido) com crédito cheio se ≥ 80%.
2. Bônus sobre soma dos efetivos creditados, não estimativa.
3. Pesos BAIXA/MEDIA/ALTA iguais; sem “dificuldade”.
4. Issue #28; último slice do épico #20.

---

## Testes automatizados

### Unitários

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Bloco 60 min após ≥ 80% → XP cheio | B1 | Aceite |
| T2 | Concluir em poucos segundos → 0 XP | B1 | Aceite |
| T3 | Metade do tempo → ~metade do XP (sem bônus 80%) | B1 | Aceite |
| T4 | Bônus finalização não usa estimativa cheia se efetivo menor | B1 | Aceite |

### Integração

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T5 | `POST .../concluir` credita conforme efetivos no ledger | B2 | Ponta a ponta |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **XpMotor efetivos** | Fórmula + bônus; **T1–T4** |
| **B2** | **Integrar concluir** | Passar timestamps; **T5** |

### Ordem

```text
B1 → B2
```
