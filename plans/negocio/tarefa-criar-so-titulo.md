# Feature — Criar tarefa só com título (defaults no create)

Escopo: `POST /tarefas` aceita body mínimo com **apenas `titulo`**; preencher `prioridade`, `duracaoMinutos`, `prazo` e `descricao` com defaults quando omitidos/null.  
Fora deste slice: UI inline / quick-add (plano `ui-criar-tarefa-titulo.md`); mudança de score/motor além de aceitar os defaults; PATCH continua exigindo campos explícitos quando enviados.  
Dependências: `tarefa.md`, `tarefa-duracao-minutos.md`, `tarefa-sem-prazo.md`. Épico UI: `ui-tarefas-primeiro-epico.md`.

---

## Conceito

TickTick (e apps de lista) deixam capturar a intenção em um campo. O domínio ainda precisa de duração/prioridade para o planner — por isso o create **mínimo** aplica defaults editáveis depois, em vez de inventar um segundo tipo de tarefa.

---

## Entity / API

Sem migration. Mudança só de contrato/validação do create.

### Defaults no create (quando o campo vier omitido ou `null`)

| Campo | Default | Observação |
|-------|---------|------------|
| `titulo` | — | continua `@NotBlank`, max 120 |
| `descricao` | `""` | já existe |
| `prioridade` | `MEDIA` | se omitida/`null` |
| `prazo` | `null` | já opcional |
| `duracaoMinutos` | `30` | mínimo válido (múltiplo de 15); se omitida/`null` |
| `projetoId` | `null` | inalterado |

Se o cliente **enviar** `prioridade` ou `duracaoMinutos`, valem as validações atuais (`@NotNull` efetivo após default, `@DuracaoMinutosValida`, enum).  
Valores inválidos enviados (ex. `duracaoMinutos: 10`) → **400**, não substituir pelo default.

### `CriarTarefaRequest`

- `prioridade` e `duracaoMinutos` deixam de ser `@NotNull` no record; o service aplica default **antes** de persistir/validar domínio.
- Documentar no OpenAPI/resumo do controller se houver.

### PATCH

Sem mudança: omitir campo no update continua “não altera”; não aplicar defaults de create no update.

---

## Back-end

1. **Service create** — após bind do request: se `prioridade == null` → `MEDIA`; se `duracaoMinutos == null` → `30`; depois valida duração/prioridade e segue fluxo atual (prazo passado, projeto, etc.).
2. **IT** — `POST` só com `{"titulo":"…"}` → 201 e body com `MEDIA`, `30`, `prazo: null`.  
   `POST` com `duracaoMinutos: 10` → 400.  
   `POST` com prioridade/duração explícitas → respeita valores.
3. Sem prefixo novo de proxy.

---

## Front-end

Nenhuma tela neste plano (só contrato). O slice UI consome o create mínimo.

---

## Decisões fechadas (revisão)

1. Defaults: `MEDIA`, `30` min, `prazo` null, `descricao` "".
2. Default só no **create** quando omitido/null; valor inválido enviado → 400.
3. Sem migration; sem endpoint novo.
4. UI fora deste arquivo.

---

## Testes automatizados

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `POST /tarefas` só com `titulo` → 201; prioridade `MEDIA`, `duracaoMinutos` 30, `prazo` null | B1 | Aceite do épico |
| T2 | `POST` com `titulo` + `duracaoMinutos: 60` + `prioridade: ALTA` → 201 com esses valores | B1 | Defaults não sobrescrevem envio |
| T3 | `POST` com `duracaoMinutos: 10` → 400 | B1 | Validação permanece |
| T4 | Sem `titulo` → 400 | B1 | Regressão |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Defaults no create + ITs** | Request sem `@NotNull` em prioridade/duração; service aplica defaults; **T1–T4** |

### Ordem

```text
B1
```
