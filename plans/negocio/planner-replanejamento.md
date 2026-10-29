# Feature — Sugerir replanejamento e deixar o usuário aceitar

Issue: [#26](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/26)

Escopo: gatilhos geram **proposta** (rascunhos novos) sem editar o plano `APROVADO`; usuário aceita (substitui só o não executado; blocos `CONCLUIDO` permanecem) ou descarta (vigente intacto). Job não grava `APROVADO` sozinho.  
Fora deste slice: IA; hábitos; mudança só cosmética da home além dos CTAs aceitar/descartar.

Dependências: `planner-janela-multidia.md` (#22), `execucao.md`; idealmente após `planner-conflito-prazo.md` (#23).

---

## Conceito

1. Gatilhos de proposta (sem mutar aprovado):
   - Capacidade de um dia que tem plano aprovado mudou.
   - O dia terminou com bloco não concluído (detectar no job diário seguinte).
   - Entrou tarefa elegível cujo prazo cai na janela já aprovada.
2. Aceitar: aplica a proposta sobre o vigente — blocos com execução `CONCLUIDO` permanecem; restante segue a proposta.
3. Descartar: apaga/ignora rascunhos da proposta; aprovado vigente igual.
4. Job 06:00: se há aprovado, **pode** gerar proposta (rascunho) quando houver gatilho — não pular eternamente; **não** auto-aprovar. (Ajusta a regra antiga “skip se aprovado” para “skip geração cega; gerar proposta se gatilho”).

---

## Entities

Reutilizar `Plano` + `propostaId`. Status continua `RASCUNHO` / `APROVADO`.  
Pode marcar proposta como `DESCARTADA` via soft-flag ou simplesmente soft-delete/remover rascunhos da proposta — **decisão:** descartar = não promover; rascunhos da proposta ficam fora da “corrente” (ex. status novo `DESCARTADO` **ou** exclusão dos rascunhos). Preferência simples: status `DESCARTADO` em planos da proposta rejeitada.

| StatusPlano | Uso |
|-------------|-----|
| RASCUNHO | proposta pendente |
| APROVADO | vigente |
| DESCARTADO | proposta rejeitada (não é corrente) |

---

## Back-end

### Endpoints

1. **POST /planos/propostas/{propostaId}/aceitar** — autenticação; escopo usuário; preserva blocos concluídos; promove dias da proposta conforme regra; 409 se inválida.
2. **POST /planos/propostas/{propostaId}/descartar** — marca descartada / remove da corrente.
3. Detecção de gatilhos: no job diário + pontos de escrita de capacidade/tarefa (eventos síncronos que enfileiram geração de proposta).

Registrar prefixo se novo path não estiver no proxy — `/planos` provavelmente já existe; se subpath novo, só documentar. Ver `API_PROXY.md` (mesmo prefixo `/planos` → ok).

### Front-end

- Home/revisão: CTA “Aceitar proposta” / “Descartar” quando houver rascunho mais novo que o aprovado (`ui-revisao-rascunho` / `ui-home-agenda`).
- Atividade F* para proxy só se path raiz novo.

---

## Decisões fechadas (revisão)

1. Proposta não edita aprovado até aceitar.
2. Aceitar preserva `CONCLUIDO`; descartar mantém vigente.
3. Job não grava `APROVADO` sozinho; gera proposta sob gatilho.
4. Status `DESCARTADO` para proposta rejeitada.
5. Issue #26.

---

## Testes automatizados

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Reduzir capacidade de dia aprovado → novo rascunho; aprovado intacto | B2 | Aceite |
| T2 | Aceitar preserva blocos com execução `CONCLUIDO` | B3 | Aceite |
| T3 | Descartar deixa vigente igual | B4 | Aceite |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T4 | CTAs aceitar/descartar visíveis com proposta pendente | F1 | Fluxo visão 4.5 |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Status DESCARTADO + modelo** | Flyway/enum se preciso; sem endpoint novo de aceite |
| **B2** | **Gatilhos → gerar proposta** | Capacidade / dia incompleto / tarefa nova; **T1** |
| **B3** | **POST aceitar** | Preserva concluídos; **T2** |
| **B4** | **POST descartar** | **T3** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **CTAs proposta** | Aceitar/descartar + estados; **T4**; checar proxy `/planos` |

### Ordem

```text
B1 → B2 → B3 → B4 → F1
```
