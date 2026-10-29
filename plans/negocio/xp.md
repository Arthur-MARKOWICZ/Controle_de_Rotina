# Feature — XP (sexto slice)

Escopo: gamificação leve — conceder XP ao concluir bloco e bônus ao finalizar tarefa; ledger imutável; saldo/nível no usuário; feedback na UI (+N XP) e progresso no header/`/me`.  
Fora deste slice: histórico agregado dia/semana, streaks, ranking, moedas/inventário/RPG, estorno de XP, crédito via `PATCH` manual de status da tarefa, IA.

Dependências: `execucao.md` (execução `CONCLUIDO` e regra que marca tarefa `FINALIZADA`).

---

## Conceito

1. XP é **efeito colateral** de `POST /execucoes/{id}/concluir` (mesma transação). Não há endpoint “dar XP”.
2. Cada concessão vira uma linha no **ledger** (`XpEvento`). Unicidade por origem evita crédito duplicado.
3. Saldo `xpTotal` e `nivel` no `Usuario` são **desnormalizados** e atualizados junto com o ledger (não é cache: coluna de progresso).
4. Sem estorno no MVP: reabrir tarefa via `PATCH` ou qualquer outro fluxo **não** remove XP já creditado.
5. Agregados “XP hoje / esta semana” ficam em `historico.md`.

---

## Entities

### XpEvento (ledger)

| Campo      | Tipo                 | Observação                                              |
|------------|----------------------|---------------------------------------------------------|
| id         | UUID                 | PK                                                      |
| usuarioId  | UUID                 | FK; dono (JWT)                                          |
| tipo       | enum `TipoXpEvento`  | ver abaixo                                              |
| origemId   | UUID                 | id da origem (execução ou tarefa)                       |
| quantidade | int                  | `> 0`; XP deste evento                                  |
| createdAt  | Instant              |                                                         |

**Unicidade:** `(tipo, origemId)` único — um evento por origem.

### TipoXpEvento

| Valor               | origemId     | Quando |
|---------------------|--------------|--------|
| `BLOCO_CONCLUIDO`   | `execucaoId` | ao marcar execução `CONCLUIDO` |
| `TAREFA_FINALIZADA` | `tarefaId`   | quando, **no fluxo de concluir execução**, a tarefa passa a `FINALIZADA` pela primeira vez (idempotente via unicidade) |

### Usuario (campos novos)

| Campo   | Tipo | Observação                          |
|---------|------|-------------------------------------|
| xpTotal | int  | `>= 0`; default `0`                |
| nivel   | int  | `>= 1`; default `1`                |

Sem soft delete de evento. Ledger só cresce.

---

## Regras de negócio

### Pesos de prioridade

| Prioridade | Peso |
|------------|------|
| `BAIXA`    | 1.0  |
| `MEDIA`    | 1.5  |
| `ALTA`     | 2.0  |

Peso lido da **tarefa** do bloco no momento da concessão.

### XP do bloco (`BLOCO_CONCLUIDO`)

```text
XP = round(bloco.duracaoMinutos × peso(tarefa.prioridade))
```

- `round` = arredondamento half-up para inteiro (`Math.round` em double).
- Se o cálculo der `0` (não esperado com duração ≥ 1 e peso ≥ 1): **não** grava evento e não altera saldo.
- Se já existir evento `BLOCO_CONCLUIDO` para aquela `execucaoId` → **não** credita de novo (idempotente).

### Bônus de tarefa (`TAREFA_FINALIZADA`)

Só no fluxo de `concluir` execução, **depois** de aplicar a regra de status da tarefa (ver `execucao.md`):

- Se a tarefa **acabou de ficar** `FINALIZADA` neste concluir (ou já está `FINALIZADA` e ainda não há evento — defesa): tentar conceder bônus.
- Fórmula:

```text
bonus = round(tarefa.duracaoHoras × 60 × peso(tarefa.prioridade) × 0.1)
```

- Interpretar: **10%** do XP “teórico” da duração total da tarefa × prioridade.
- Unicidade `(TAREFA_FINALIZADA, tarefaId)` → no máximo **um** bônus por tarefa, para sempre.
- `PATCH /tarefas` que coloca `FINALIZADA` **não** concede bônus (nem bloco).
- Reabrir tarefa e finalizar de novo: **sem** segundo bônus.

### Curva de nível

Nível começa em **1**. XP necessário para subir do nível `n` para `n+1`:

```text
xpParaSubir(n) = 10 × n
```

Exemplos: 1→2 precisa **10**; 2→3 precisa **20**; 3→4 precisa **30**; …

Dado `xpTotal` (acumulado lifetime):

1. `nivel = 1`, `restante = xpTotal`
2. Enquanto `restante >= xpParaSubir(nivel)`: `restante -= xpParaSubir(nivel)`, `nivel += 1`
3. Resultado: `nivel`, `xpNoNivel = restante`, `xpParaProximoNivel = xpParaSubir(nivel)`

Ao creditar: somar quantidades novas em `xpTotal`, recalcular `nivel` com a curva, persistir ambos.

Pode subir **mais de um nível** num único request (ex.: bônus grande).

### Estorno (decisão MVP)

**Não há estorno.** Abortar não devolve XP (só se conclui). `PATCH` de status, soft delete de tarefa, etc. não mexem no ledger nem no saldo.

### Ordem no `concluir`

Na mesma transação do concluir:

1. Marcar execução `CONCLUIDO` + regras de status da tarefa (`execucao.md`).
2. Conceder `BLOCO_CONCLUIDO` (se ainda não existir).
3. Se a tarefa está `FINALIZADA` e ainda não há `TAREFA_FINALIZADA` → conceder bônus.
4. Atualizar `xpTotal` / `nivel` do usuário.
5. Resposta HTTP inclui quanto foi ganho **neste** request + progresso atual.

---

## Back-end

Todos autenticados; escopo JWT. Sem prefixo novo obrigatório: progresso em `/me`; crédito embutido em `/execucoes/{id}/concluir`.

Pacote de domínio: `xp` (entity, repository, service/motor). Integração chamada a partir do serviço de execução (sem Field Injection; construtor).

### Contratos

1. **POST /execucoes/{id}/concluir** (já existe em `execucao.md`) — **enriquecer** resposta  
   - Além da execução (e status da tarefa, se já houver):  
     ```json
     {
       "xp": {
         "ganhoNesteRequest": 132,
         "eventos": [
           { "tipo": "BLOCO_CONCLUIDO", "quantidade": 120 },
           { "tipo": "TAREFA_FINALIZADA", "quantidade": 12 }
         ],
         "progresso": {
           "xpTotal": 820,
           "nivel": 12,
           "xpNoNivel": 820,
           "xpParaProximoNivel": 1000
         }
       }
     }
     ```
   - `ganhoNesteRequest` = soma das quantidades **efetivamente inseridas** neste request (0 se reprocessamento idempotente).
   - `eventos` só os novos deste request (lista vazia se nada novo).
   - Idempotência: segundo concluir em `CONCLUIDO` continua **409** (regra de execução); o ledger não depende de retry HTTP bem-sucedido no mesmo estado.

2. **GET /me** — **enriquecer** `UsuarioResponse`  
   - Incluir: `xpTotal`, `nivel`, `xpNoNivel`, `xpParaProximoNivel` (mesma forma de `progresso` acima).  
   - Cadastro/login: usuário nasce com `xpTotal = 0`, `nivel = 1`.

Sem job/scheduler. Sem endpoint de listagem de eventos neste slice (histórico depois).

---

## Front-end

1. **Feedback ao concluir** — após `POST .../concluir`, se `xp.ganhoNesteRequest > 0`, mostrar feedback curto (`+N XP`); se houve bônus de tarefa, pode somar no mesmo toast ou indicar os dois eventos (sem pixel-perfect).
2. **Header / sessão** — exibir nível + barra (`xpNoNivel` / `xpParaProximoNivel`) a partir de `GET /me` (e atualizar localmente com `xp.progresso` da resposta do concluir, para não depender só de refetch).
3. **Proxy** — sem prefixo novo; nada a registrar em Vite/Nginx/`API_PROXY.md` por causa deste slice (só se no futuro existir `/xp`).

Rotas autenticadas; reutilizar home de execução e sessão existentes.

---

## Decisões fechadas (revisão)

1. Crédito em **dois momentos**: bloco concluído + bônus quando a tarefa fica `FINALIZADA` no fluxo de execução.
2. Fórmula do bloco: `round(duracaoMinutos × peso)`; pesos BAIXA=1.0, MEDIA=1.5, ALTA=2.0.
3. Bônus de tarefa: `round(duracaoHoras × 60 × peso × 0.1)`; no máximo uma vez por `tarefaId`.
4. `PATCH` (ou outro fluxo) que marca `FINALIZADA` **não** concede XP.
5. Ledger `XpEvento` imutável + unicidade `(tipo, origemId)`; saldo `xpTotal`/`nivel` no `Usuario`.
6. Curva: `xpParaSubir(n) = 10 × n`; nível inicial 1; pode subir vários níveis num request.
7. Sem estorno de XP no MVP.
8. UI: toast `+N XP` no concluir + progresso no header via `/me` (e patch local pós-concluir).
9. Sem prefixo `/xp` neste slice; domínio em pacote `xp`.
10. Este é o **sexto slice**.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*/F*) que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | XP bloco: BAIXA/MEDIA/ALTA com `round` (ex.: 60×1.5 → 90; 45×1.5 → 68) | B2 | Fórmula do produto |
| T2 | Bônus tarefa: 10% de `duracaoHoras×60×peso` com `round` | B2 | Segunda fonte de XP |
| T3 | Curva: `xpParaSubir(n)=10n`; dado `xpTotal`, `nivel`/`xpNoNivel`/`xpParaProximoNivel` corretos (incl. subir 2+ níveis de uma vez) | B2 | Progressão |
| T4 | Concessão: grava ledger + soma saldo; segunda chamada mesma origem **não** duplica | B3 | Idempotência |
| T5 | Fluxo concluir: bloco sempre tenta `BLOCO_CONCLUIDO`; bônus só se tarefa `FINALIZADA` e sem evento prévio | B3 | Amarração execução ↔ XP |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T6 | `POST .../concluir`: 200 com `xp.ganhoNesteRequest` e `eventos`; usuário com saldo/nível atualizados | B4 | Contrato do feedback |
| T7 | Concluir que finaliza tarefa: dois eventos (bloco + bônus) no mesmo response; segundo bloco de outra tarefa já finalizada não recria bônus | B4 | Unicidade do bônus |
| T8 | `GET /me`: retorna `xpTotal`, `nivel`, `xpNoNivel`, `xpParaProximoNivel`; usuário novo → 0 / nível 1 | B5 | Header / progresso |
| T9 | Sem token → 401 nos endpoints tocados; execução de outro usuário → 404 (padrão execução) | B4 | Auth/escopo |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T10 | Após concluir com `ganhoNesteRequest > 0`, UI mostra feedback `+N XP` | F1 | Motivação imediata |
| T11 | Header reflete nível/barra a partir de `/me` (ou progresso da resposta) | F2 | Progressão visível |

Se o harness front não cobrir UI: T10–T11 checklist manual até existir; **T1–T9 obrigatórios** no back.

---

## Atividades

Regra: motor puro sem HTTP; integração no concluir sem endpoint novo de crédito; enriquecer `/me` em atividade própria. Entregar os T* na atividade indicada.

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Entity + Flyway** | `XpEvento`, enum `TipoXpEvento`, unicidade `(tipo, origemId)`; colunas `xp_total` / `nivel` em `usuario` (default 0 / 1); repositories; **sem** endpoints |
| **B2** | **Motor** | Cálculo XP bloco, bônus tarefa, curva de nível (`xpParaSubir` + resolução de progresso); **T1–T3** (unitário puro) |
| **B3** | **Serviço de concessão** | Inserir eventos idempotentes + atualizar `xpTotal`/`nivel`; orquestração bloco ± bônus a partir de dados da execução/tarefa; **T4–T5** |
| **B4** | **Integrar em concluir** | Chamar concessão na transação de `POST /execucoes/{id}/concluir`; enriquecer response com `xp`; **T6, T7, T9** |
| **B5** | **GET /me + progresso** | Estender `UsuarioResponse` / `buscarMe` com campos de XP; usuário novo coerente; **T8** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Toast ao concluir** | Ler `xp` da resposta do concluir; feedback `+N XP`; **T10** |
| **F2** | **Header com nível/barra** | Consumir progresso de `/me` (atualizar após concluir); **T11** |

### Ordem

```text
B1 → B2 → B3 → B4 → B5
              ↘
               F1 → F2
```

`F1` após **B4**. `F2` após **B5** (ou B4 se usar só o `progresso` da resposta do concluir + `/me` já estendido).
