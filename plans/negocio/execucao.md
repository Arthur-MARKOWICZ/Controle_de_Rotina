# Feature — Execução (quinto slice)

Escopo: iniciar, acompanhar (tempo restante no front) e concluir/abortar blocos do **plano aprovado**; refletir progresso na tarefa (`EM_PROGRESSO` / `FINALIZADA`).  
Fora deste slice: XP (fórmula e concessão), histórico agregado do dia/semana, pause/resume, replanejamento pós-execução, multi-dia de distribuição, notificações, IA.

Dependências: `planner.md` (plano `APROVADO` + `BlocoPlano`).

---

## Conceito

1. Só se executa bloco de plano com `status = APROVADO` do usuário (versão usada = **corrente** do dia: maior `versao` para `(usuarioId, data)` — e essa corrente deve estar `APROVADO`; se a corrente for `RASCUNHO`, não há execução neste slice).
2. Cada `BlocoPlano` tem no máximo **uma** `Execucao` (1:1). Criação **lazy** no primeiro `iniciar`.
3. Vários blocos podem estar `EM_ANDAMENTO` ao mesmo tempo (sem limite de 1).
4. Timer: o back grava `iniciadoEm`; o front calcula restante com `duracaoMinutos` do bloco. Sem pause/resume e sem tick/job no servidor.
5. Concluir marca o bloco; atualiza status da **tarefa** conforme regras abaixo. Abortar permite reiniciar o mesmo registro depois.
6. XP **não** é concedido aqui — só a conclusão do bloco (o slice `xp.md` consome esse evento depois).

---

## Entity

### Execucao

| Campo         | Tipo                    | Observação                                              |
|---------------|-------------------------|---------------------------------------------------------|
| id            | UUID                    | PK                                                      |
| usuarioId     | UUID                    | FK; dono (JWT)                                          |
| blocoPlanoId  | UUID                    | FK; **único** (1 execução por bloco)                    |
| status        | enum `StatusExecucao`   | ver abaixo                                              |
| iniciadoEm    | Instant (nullable)      | preenchido/atualizado em todo `iniciar`                 |
| concluidoEm   | Instant (nullable)      | só quando `CONCLUIDO`                                   |
| abortadoEm    | Instant (nullable)      | último abort; limpo ao reiniciar                        |
| createdAt     | Instant                 |                                                         |
| updatedAt     | Instant                 |                                                         |

**Unicidade:** `(blocoPlanoId)` único.

### Status (`StatusExecucao`)

Valores: `NAO_INICIADO`, `EM_ANDAMENTO`, `CONCLUIDO`, `ABORTADO`.

- Persistido na prática após o primeiro `iniciar`: fluxo feliz `EM_ANDAMENTO` → `CONCLUIDO`.
- `NAO_INICIADO` pode aparecer só se no futuro houver materialização na aprovação; neste MVP a ausência de linha = ainda não iniciado (resposta de listagem usa status efetivo `NAO_INICIADO`).
- `ABORTADO` → novo `iniciar` no mesmo registro → `EM_ANDAMENTO` (reinício).
- `CONCLUIDO` é terminal para o bloco (não reinicia).

Sem soft delete.

---

## Regras de negócio

### Pré-condições comuns

- Bloco e plano do **mesmo** usuário do JWT; senão **404**.
- Plano do bloco: `status = APROVADO` e deve ser a **versão corrente** daquela `data` (maior `versao`). Caso contrário → **409** (ex.: rascunho mais novo, ou plano não aprovado).
- Sem token → **401**.

### Iniciar

- Se não existe `Execucao` para o `blocoPlanoId` → cria com `EM_ANDAMENTO`, `iniciadoEm = agora`.
- Se existe `EM_ANDAMENTO` → **409** (já em andamento).
- Se existe `CONCLUIDO` → **409**.
- Se existe `ABORTADO` → `EM_ANDAMENTO`, `iniciadoEm = agora`, `abortadoEm = null`, `concluidoEm` permanece null.
- **Tarefa:** se status ∈ {`PENDENTE`, `ATRASADA`} → passa a `EM_PROGRESSO`. Não altera `FINALIZADA` / `CANCELADA` / já `EM_PROGRESSO`.

### Abortar

- Só se `status = EM_ANDAMENTO` → `ABORTADO`, `abortadoEm = agora`.
- Outros status → **409**.
- Não altera status da tarefa.

### Concluir

- Só se `status = EM_ANDAMENTO` → `CONCLUIDO`, `concluidoEm = agora`.
- Outros status → **409**.
- **Tarefa** (após marcar o bloco):
  1. Se ainda `PENDENTE` / `ATRASADA` → `EM_PROGRESSO` (mesmo efeito do iniciar, por segurança).
  2. Somar `duracaoMinutos` de todos os `BlocoPlano` cuja `Execucao` do usuário está `CONCLUIDO` **e** `tarefaId` = tarefa deste bloco (qualquer data/plano).
  3. Se `soma >= tarefa.duracaoHoras * 60` → tarefa `FINALIZADA`.
  4. Caso contrário permanece `EM_PROGRESSO` (bloco parcial do dia ou tarefa maior que o que já foi concluído).

Não grava “minutos reais trabalhados” neste slice — duração canônica para progresso = `duracaoMinutos` do bloco concluído.

### Listagem / acompanhamento

- Query por `data` (default: **hoje** em `America/Sao_Paulo`).
- Resolve plano corrente da data; se não for `APROVADO` → lista vazia ou payload explícito “sem plano aprovado” (não 404).
- Para cada bloco do plano (ordem): dados mínimos do bloco/tarefa + status efetivo da execução (`NAO_INICIADO` se sem linha) + `iniciadoEm` se houver + `duracaoMinutos` (front calcula restante).

---

## Back-end

Todos autenticados. Escopo JWT; outro usuário / id inexistente → **404**.

Prefixo novo: **`/execucoes`** (registrar no proxy Vite + Nginx + `API_PROXY.md`).

1. **GET /execucoes**  
   - Query: `data` (opcional; default hoje BRT).  
   - Resposta: plano aprovado corrente da data (id/data/versão) + lista de itens `{ bloco, tarefaResumo, execucao? }` ordenados.  
   - Sem plano aprovado corrente: 200 com lista vazia + indicação clara (ex. `planoAprovado: null`).

2. **POST /execucoes/iniciar**  
   - Body: `{ "blocoId": "<uuid>" }`.  
   - Regras de iniciar acima.  
   - 201 se criou; 200 se reiniciou a partir de `ABORTADO` (ou 200 em ambos — **decisão:** 201 na primeira criação, 200 no reinício).

3. **POST /execucoes/{id}/concluir**  
   - Id da `Execucao`.  
   - 200 + execução (+ status atualizado da tarefa no payload ou só execução; front pode refetch tarefa/home).

4. **POST /execucoes/{id}/abortar**  
   - 200 + execução `ABORTADO`.

Sem job/scheduler neste slice.

---

## Front-end

1. **Home pós-aprovação** — se o plano do dia corrente está `APROVADO`, a mesma home entra em **modo execução**: lista de blocos com status, tempo restante (cálculo local a partir de `iniciadoEm` + `duracaoMinutos`), CTAs Iniciar / Concluir / Abortar conforme status.
2. **Iniciar** — chama `POST /execucoes/iniciar`; atualiza UI; inicia/atualiza countdown no cliente.
3. **Concluir / Abortar** — CTAs que chamam os endpoints; após concluir, refletir possível mudança de status da tarefa (refetch lista ou patch local).
4. **Proxy** — incluir `/execucoes` em `vite.config.ts`, `nginx.conf` e `API_PROXY.md`.

Rotas autenticadas; reutilizar sessão e a home do planner (não criar CRUD paralelo de plano).

---

## Decisões fechadas (revisão)

1. Entity **separada** `Execucao` (1:1 com `BlocoPlano`), criação lazy no primeiro iniciar.
2. Status: `NAO_INICIADO` (efetivo sem linha) | `EM_ANDAMENTO` | `CONCLUIDO` | `ABORTADO`. Sem “pular” no MVP.
3. **Vários** `EM_ANDAMENTO` simultâneos permitidos.
4. Timer só com `iniciadoEm` + duração do bloco no front; sem pause/resume; sem job de tick.
5. Progresso da tarefa: `EM_PROGRESSO` no iniciar (se `PENDENTE`/`ATRASADA`); `FINALIZADA` quando soma das durações dos blocos `CONCLUIDO` da tarefa ≥ duração total em minutos.
6. Abortar → `ABORTADO`; reinício via novo `iniciar` no mesmo registro.
7. UI: **mesma home**, modo execução quando o plano corrente do dia está `APROVADO`.
8. Escopo temporal: query `data`, default hoje (`America/Sao_Paulo`); só versão **corrente aprovada**.
9. XP e histórico ficam nos slices seguintes; este slice só persiste conclusão/aborto.
10. Este é o **quinto slice**.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*/F*) que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Iniciar: cria execução; rejeita se já `EM_ANDAMENTO` / `CONCLUIDO`; reinicia a partir de `ABORTADO` | B2 | Núcleo do ciclo de vida |
| T2 | Iniciar: tarefa `PENDENTE`/`ATRASADA` → `EM_PROGRESSO`; não altera `FINALIZADA`/`CANCELADA` | B2 | Ligação execução ↔ tarefa |
| T3 | Concluir: só de `EM_ANDAMENTO`; soma durações `CONCLUIDO` da tarefa; `FINALIZADA` só se soma ≥ duração total; parcial → permanece `EM_PROGRESSO` | B2 | Regra explícita do slice |
| T4 | Abortar: só de `EM_ANDAMENTO` → `ABORTADO`; não mexe na tarefa | B2 | Abort sem efeito colateral indevido |
| T5 | Pré-condição: bloco de plano não aprovado / não corrente → conflito (regra de domínio) | B2 | Evita executar rascunho |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T6 | `GET /execucoes?data=`: itens do aprovado corrente; sem aprovado → 200 vazio; sem token → 401 | B3 | Contrato da home |
| T7 | `POST /execucoes/iniciar`: 201 primeira vez; 200 reinício; bloco outro usuário / inexistente → 404; já em andamento → 409 | B4 | Escopo JWT + ciclo iniciar |
| T8 | `POST /execucoes/{id}/concluir`: 200 + `CONCLUIDO`; status tarefa conforme T3; id outro usuário → 404 | B5 | Porta de entrada do XP depois |
| T9 | `POST /execucoes/{id}/abortar`: 200 + `ABORTADO`; concluir/abortar em status inválido → 409 | B6 | Contrato de abort |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T10 | Home em plano `APROVADO` mostra CTAs de execução (iniciar) e não os de ajuste/aprovar do rascunho | F1 | Modo execução vs rascunho |
| T11 | Countdown / restante derivado de `iniciadoEm` + duração (comportamento visível) | F2 | Acompanhamento sem timer no back |

Se o harness front não cobrir UI: T10–T11 como checklist manual até existir; **T1–T9 obrigatórios** no back.

---

## Atividades

Regra: **no máximo um endpoint novo por atividade** de back. Motor de regras sem HTTP em B2. Entregar os T* na atividade indicada.

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Entity + Flyway** | `Execucao`, enum `StatusExecucao`, migration, unicidade `blocoPlanoId`, repository; **sem** endpoints |
| **B2** | **Regras de domínio** | Iniciar / abortar / concluir + atualização de `StatusTarefa` + validação plano aprovado corrente; **T1–T5** (service/IT sem API HTTP) |
| **B3** | **GET /execucoes** | Query `data` (default hoje BRT); montagem bloco + execução efetiva; auth; **T6** |
| **B4** | **POST /execucoes/iniciar** | Body `blocoId`; create/reinício; **T7** |
| **B5** | **POST /execucoes/{id}/concluir** | Conclusão + regra de tarefa; **T8** |
| **B6** | **POST /execucoes/{id}/abortar** | Abort; **T9** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Proxy + home modo execução (leitura)** | Registrar `/execucoes` em Vite/Nginx/`API_PROXY.md`; home com plano `APROVADO` lista blocos/status via `GET /execucoes`; **T10** |
| **F2** | **Iniciar + countdown** | CTA iniciar + cálculo de restante no cliente; **T11** |
| **F3** | **Concluir** | CTA → `POST .../concluir`; refetch/atualização de status |
| **F4** | **Abortar** | CTA → `POST .../abortar`; UI volta a permitir iniciar |

### Ordem

```text
B1 → B2 → B3 → B4 → B5 → B6
              ↘
               F1 → F2 → F3 → F4
```

`F1` após **B3**. `F2` após **B4**. `F3`/`F4` após **B5**/**B6**.
