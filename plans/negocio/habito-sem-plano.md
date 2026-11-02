# Feature — Hábito visível e executável sem plano aprovado

Issue: [#43](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/43) (correção / extensão)

Escopo: hábito `ATIVO` do weekday aparece no **calendário** mesmo sem plano `APROVADO`; cumprimento do dia **sem depender** do plano do planner (bloco implícito); fluxos **Iniciar → Concluir / Abortar** e **Concluir direto** (sem timer); streak conta **1× por hábito/dia** mesmo com execução no plano **e** avulsa; XP por cada `Execucao` concluída; extensão de `GET /calendario` com `habitosDoDia`.  
Fora deste slice: redesign do motor/score de hábitos; RRULE; social; pause/resume; sync externo; mudar regras de tarefa no plano; entity `ExecucaoHabito` separada (reusa `Execucao` + `BlocoPlano`).

Dependências: `habitos.md`, `execucao.md`, `xp.md` (+ tempo efetivo / peso MEDIA do hábito), `calendario/calendario.md`. Ideal após F* mínimas de `habitos.md` / `ui-habitos.md` (código de hábito já no repo).

Corrige / sobrescreve nestes pontos:
- `habitos.md` § Execução: “pré-condições de plano `APROVADO`” **não** impedem cumprimento avulso.
- `calendario.md`: “fora: recorrência/hábitos” — hábitos **entram** neste slice via `habitosDoDia` + lente UI.

---

## Conceito

1. Hábito do dia **não precisa** estar no plano do planner para ser feito.
2. Modelo **bloco implícito:** no primeiro `iniciar` ou `concluir-direto`, o back garante um `Plano` com `status = HABITO_AVULSO` do dia + um `BlocoPlano` com `habitoId` (duração = `duracaoMinutos` do hábito) + `Execucao` 1:1 como hoje.
3. Plano `HABITO_AVULSO` **não** é a corrente do planner: job, geração, aprovação, Home/`GET /planos`, lente **Plano** do calendário **ignoram** esse status.
4. Execução do bloco implícito é permitida **só** para origem `HABITO` nesse status (nunca tarefa).
5. Se o hábito **também** estiver no plano `APROVADO` do planner, os dois blocos/execuções **podem coexistir** no mesmo dia.
6. **Streak / cumprimento do dia `D`:** existe **≥ 1** `Execucao` `CONCLUIDO` cujo bloco tem `habitoId = H` e `Plano.data = D` (**qualquer** status de plano: `APROVADO` ou `HABITO_AVULSO`). Segundo cumprimento no mesmo `D` **não** altera streak de novo.
7. **XP:** cada conclusão de `Execucao` distinta credita (idempotência por `execucaoId`); dois cumprimentos no dia = dois créditos possíveis.
8. **Abortar** não conta cumprimento nem XP.
9. Calendário: payload do dia sempre inclui `habitosDoDia` (hábitos `ATIVO`, não deletados, weekday ∈ `diasSemana`); UI da lente em `ui-habito-calendario.md`.

---

## Entity / Entities

### StatusPlano (extensão)

Novo valor: **`HABITO_AVULSO`**.

| Uso | Regra |
|-----|--------|
| Corrente do dia (Home, aprovação, execução de **tarefas**) | Só `RASCUNHO` / `APROVADO` (lista atual **sem** `HABITO_AVULSO`) |
| Job / geração | Não cria, não sobrescreve, não lista como skip de “já aprovado” |
| Execução | Aceita plano `HABITO_AVULSO` **somente** se o bloco tem `habitoId` |
| Calendário lente Plano | Ignora |

### Plano (uso)

| Campo | Observação |
|-------|------------|
| status | `HABITO_AVULSO` |
| data | dia civil BRT do cumprimento |
| versao | `MAX(versao)+1` no dia **ou** reuso do único plano `HABITO_AVULSO` existente daquele `(usuarioId, data)` |
| temConflito | sempre `false` |
| mensagem / propostaId | null |
| geradoEm | agora na criação |

**Invariante de serviço:** no máx. **um** plano `HABITO_AVULSO` por `(usuarioId, data)`.  
**Invariante:** nesse plano, no máx. **um** bloco por `habitoId`.

### BlocoPlano / Execucao

Sem colunas novas. XOR `tarefaId`/`habitoId` e 1:1 `Execucao` inalterados.

### DTO calendário — item hábito do dia

| Campo | Tipo | Observação |
|-------|------|------------|
| id | UUID | habitoId |
| titulo | String | |
| duracaoMinutos | int | |
| horarioPreferencial | LocalTime? | |
| streakAtual | int | |
| cumprido | boolean | ≥ 1 execução `CONCLUIDO` do hábito em `D` (qualquer plano) |
| noPlanoAprovado | boolean | existe bloco do hábito no plano `APROVADO` corrente do dia |
| execucaoAvulsa | objeto? | status efetivo + ids do slot `HABITO_AVULSO` (ver abaixo); null se ainda não materializado |
| execucaoNoPlano | objeto? | idem para bloco no `APROVADO`, se `noPlanoAprovado` |

`execucaoAvulsa` / `execucaoNoPlano`:

| Campo | Tipo |
|-------|------|
| blocoId | UUID |
| execucaoId | UUID? |
| status | `NAO_INICIADO` \| `EM_ANDAMENTO` \| `CONCLUIDO` \| `ABORTADO` |
| iniciadoEm | Instant? |

---

## Materialização (serviço compartilhado)

Entrada: `usuarioId`, `habitoId`, `data` (default hoje BRT).

1. Hábito do usuário, `ATIVO`, `deletedAt == null`; senão **404**.
2. Weekday de `data` ∈ `diasSemana`; senão **409** (`HabitoNaoElegivel`).
3. Buscar plano `HABITO_AVULSO` de `(usuarioId, data)`; se não houver, criar.
4. Buscar bloco com esse `habitoId` no plano; se não houver, criar (`duracaoMinutos` do hábito, `ordem` = próximo).
5. Devolver plano + bloco (e execução existente se houver).

Não altera plano `APROVADO`/`RASCUNHO` do planner.

---

## Back-end

Todos autenticados (JWT). Sem token → **401**. Outro usuário / id inexistente → **404**. Sem cache.  
Sem prefixo HTTP novo (reusa `/habitos`, `/execucoes`, `/calendario`). Proxy já existente.

### Hábitos — execução avulsa

1. **POST /habitos/{id}/iniciar**  
   - Query: `data` (`LocalDate`, opcional; default hoje BRT).  
   - Materializa slot avulso.  
   - Se execução do bloco avulso já `EM_ANDAMENTO` → **409**.  
   - Se já `CONCLUIDO` → **409**.  
   - Se `ABORTADO` ou ausente → inicia (mesmo espírito de `execucao.md`).  
   - 200: `ExecucaoResponse` (enrich `habitoResumo`).

2. **POST /habitos/{id}/concluir-direto**  
   - Query: `data` opcional (default hoje BRT).  
   - Materializa slot avulso.  
   - Se já `CONCLUIDO` no bloco avulso → **409**.  
   - Se `EM_ANDAMENTO` → equivale a concluir normal (marca `CONCLUIDO`, XP, streak).  
   - Se ausente / `ABORTADO` / efetivo `NAO_INICIADO` → cria/atualiza execução **direto** para `CONCLUIDO` (`iniciadoEm` e `concluidoEm` = agora; sem exigir passagem por `EM_ANDAMENTO`).  
   - XP peso **MEDIA (1.5)**; sem `TAREFA_FINALIZADA`; streak idempotente no dia.  
   - 200: mesma shape de concluir execução (incl. ganho XP se o contrato atual já devolver).

3. **GET /habitos/do-dia**  
   - Query: `data` opcional (default hoje BRT).  
   - 200: lista no mesmo shape de `habitosDoDia` do calendário (para Home sem plano).  
   - Ordenação: `horarioPreferencial` asc nulls last, depois `titulo` asc.

### Execução existente (ajuste fino)

4. **POST /execucoes/bloco/{blocoPlanoId}/iniciar** (e concluir/abortar por id)  
   - Continua válido para bloco no `APROVADO` **e** para bloco no `HABITO_AVULSO`.  
   - Pré-condição de plano: `APROVADO` corrente **ou** `HABITO_AVULSO` do dono com bloco `habitoId` preenchido.  
   - Bloco de **tarefa** em plano `HABITO_AVULSO` → não deve existir; se aparecer, **409**.

5. **GET /execucoes?data=**  
   - Continua listando só blocos do plano `APROVADO` corrente (tarefas + hábitos no plano).  
   - Hábitos avulsos **não** misturam nesta lista (Home usa `GET /habitos/do-dia` + execuções do aprovado).

### Calendário (extensão — sem path novo)

6. **GET /calendario**  
   - Cada dia ganha `habitosDoDia: []` (nunca null).  
   - Fonte: hábitos do usuário elegíveis naquele weekday + flags de cumprimento / slots (consultas por intervalo, sem N+1 por dia).  
   - `minutosAlocados` / `sobrecarga` / `plano` **não** incluem blocos de `HABITO_AVULSO` (capacidade do planner inalterada).  
   - Dia sem hábitos → `[]`.

---

## Front-end

Mínimo neste slice (detalhe visual em `plans/ui/ui-habito-calendario.md`):

1. Calendário consome `habitosDoDia`; lente **Hábitos**; painel do dia **sempre** lista hábitos do dia.
2. CTAs no detalhe/card: **Iniciar**, **Concluir** / **Abortar** (se em andamento), **Concluir direto** (se ainda não `CONCLUIDO` no slot avulso — ou copy “Concluir” rápido quando não iniciado).
3. Home: se não houver plano `APROVADO`, ainda assim mostra hábitos de hoje via `GET /habitos/do-dia` com os mesmos CTAs.
4. Toast XP ao concluir / concluir-direto; atualizar streak exibido.

---

## Decisões fechadas (revisão)

1. Modelo **B:** materializa `Plano` `HABITO_AVULSO` + `BlocoPlano` + `Execucao` (sem entity paralela).
2. Calendário: **A+C** — `habitosDoDia` sempre no payload/painel; lente **Hábitos** na UI; lente Plano permanece só planner `APROVADO`.
3. CTAs: **A+C** — timer `Iniciar` → `Concluir`/`Abortar` **e** `Concluir direto` sem timer.
4. Coexistência plano + avulso no mesmo dia (**C**); streak **1×**/dia; XP por execução concluída.
5. `HABITO_AVULSO` fora da corrente do planner e da lente Plano / minutos alocados.
6. `concluir-direto` e `iniciar` avulsos atuam só no slot `HABITO_AVULSO` (não mutam o bloco do plano aprovado).
7. Sem cache; sem prefixo proxy novo.
8. Issue #43.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*/F*) que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Materializar: cria plano `HABITO_AVULSO` + bloco; segunda chamada reusa o mesmo | B2 | Modelo B |
| T2 | Weekday inválido / pausado / deletado → não materializa (409/404) | B2 | Elegibilidade |
| T3 | Streak: 2× `CONCLUIDO` no mesmo D (plano + avulso) → streak +1 uma vez só | B3 | Decisão 4C |
| T4 | `concluir-direto` de NAO_INICIADO → CONCLUIDO + XP MEDIA sem `TAREFA_FINALIZADA` | B3 | Fluxo C |
| T5 | Agregador calendário: weekday com hábito ATIVO entra em `habitosDoDia` sem plano aprovado; `HABITO_AVULSO` não soma `minutosAlocados` | B6 | Aceite calendário |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T6 | `POST /habitos/{id}/iniciar` sem plano aprovado → 200 EM_ANDAMENTO; repetir → 409 | B4 | Executar sem plano |
| T7 | `POST /habitos/{id}/concluir-direto` → 200 CONCLUIDO; segunda → 409 | B5 | Concluir direto |
| T8 | Plano aprovado com bloco do hábito + iniciar avulso → duas execuções possíveis; streak estável no 2º concluir | B4/B5 | Coexistência |
| T9 | `GET /habitos/do-dia` e `GET /calendario`: `habitosDoDia` / lista do dia coerentes; 401 sem token | B7, B8 | Contrato leitura |
| T10 | Iniciar/concluir execução de bloco `HABITO_AVULSO` via `/execucoes` ok; corrente planner inalterada | B4 | Não quebra Home |

### Front (mínimo)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T11 | Calendário lente Hábitos / painel lista hábito sem plano | F1 | Aceite visível |
| T12 | CTA concluir-direto e iniciar chamam endpoints certos | F2 | Aceite CTAs |

Detalhe visual extra: `ui-habito-calendario.md`.

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Flyway + enum** | `StatusPlano.HABITO_AVULSO`; repos de busca por `(usuario, data, status)`; **sem** endpoints |
| **B2** | **Materialização** | Serviço cria/reusa plano+bloco; **T1, T2** |
| **B3** | **Streak + XP no concluir direto / 2ª conclusão** | Ajuste streak idempotente no dia; regra XP; **T3, T4** (sem HTTP novo se couber no service) |
| **B4** | **POST /habitos/{id}/iniciar** | Wire materialização + iniciar; ajuste pré-condição `/execucoes` p/ `HABITO_AVULSO`; **T6, T8** (iniciar), **T10** |
| **B5** | **POST /habitos/{id}/concluir-direto** | **T7, T8** (concluir) |
| **B6** | **CalendarioAgregador + habitosDoDia** | Sem controller; **T5** |
| **B7** | **GET /calendario** (extensão DTO) | Campo `habitosDoDia`; **T9** (calendário) |
| **B8** | **GET /habitos/do-dia** | **T9** (do-dia) |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Calendário: dados + lente + painel** | Consumir `habitosDoDia`; lente Hábitos; hábitos sempre no painel; **T11** — fino em `ui-habito-calendario.md` |
| **F2** | **CTAs avulsos (calendário + Home)** | Iniciar / Abortar / Concluir / Concluir direto; Home via `do-dia` sem aprovado; **T12** |

### Ordem

```text
B1 → B2 → B3 → B4 → B5
              ↘
               B6 → B7
         B2 → B8
                   ↘
                    F1 → F2
```

`F1` após **B7**; `F2` após **B4**/**B5**/**B8**.
