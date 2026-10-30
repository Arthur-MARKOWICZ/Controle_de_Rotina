# Feature — Projetos: agrupar tarefas e reservar tempo no template semanal

Issue: [#40](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/40) (núcleo + reserva; **sem** GitHub)

Escopo: CRUD de projeto (`ATIVO` / `ARQUIVADO`); vínculo opcional tarefa → projeto (manter vínculo ao arquivar; desvincular explícito); filtro de tarefas por projeto; **reserva de minutos por projeto no template semanal** (Seg–Dom); motor do planner em duas fases (reserva → pool livre); mudança de reserva gera proposta/rascunho (não rearranja aprovado em silêncio); UI mínima de domínio + proxy `/projetos`.  
Fora deste slice: link GitHub (`github-link.md`); hierarquia épico/story; times / projetos compartilhados; sync com API GitHub; OAuth; reserva por data concreta (só template); soft delete de projeto; cache.

Dependências: `tarefa.md` (+ duração minutos), `capacidade.md`, `planner.md` (+ janela multi-dia, saldo, conflito, replanejamento). Preferível após `calendario/calendario.md` na fila; reserva na UI reaproveita a grade de capacidade.

---

## Conceito

1. **Projeto** agrupa tarefas do mesmo esforço (ex.: “TCC”). Tarefa sem projeto continua válida.
2. **Arquivar** não desvincula tarefas. Novas associações só a projetos `ATIVO`. Desvínculo = `projetoId = null` (API + botão na UI).
3. **Reserva semanal** — por `(projeto, DiaSemana)`, minutos reservados dentro da capacidade do **template** daquele weekday. Não há entity de reserva por `LocalDate`.
4. No dia `D`, o motor resolve capacidade como hoje (`OVERRIDE` > template > vazio). As reservas do weekday de `D` limitam a **fase 1**; se a capacidade resolvida for menor que a soma pedida, a reserva **efetiva** é limitada à capacidade (clamp).
5. **Alocação (regra A):**  
   - Fase 1: para cada projeto com reserva efetiva &gt; 0, alocar por score **só** tarefas elegíveis daquele projeto até esgotar a fatia (ou as tarefas).  
   - Minutos reservados **não usados** voltam ao **pool livre**.  
   - Fase 2: capacidade restante (incl. sobra da reserva) aloca por score **global**; tarefas do projeto ainda podem entrar.
6. Criar/alterar/remover reserva (e reduzir capacidade semanal abaixo da soma das reservas) **não** muta plano `APROVADO`: gera **proposta/rascunho** no espírito de `planner-replanejamento.md`.

---

## Entity / Entities

### Projeto

| Campo       | Tipo                    | Observação                                      |
|-------------|-------------------------|-------------------------------------------------|
| id          | UUID                    | PK                                              |
| usuarioId   | UUID                    | FK; dono (JWT)                                  |
| nome        | String                  | obrigatório; max 80                             |
| descricao   | String                  | opcional; max 500                               |
| status      | enum `StatusProjeto`    | `ATIVO`, `ARQUIVADO`                            |
| createdAt   | Instant                 |                                                 |
| updatedAt   | Instant                 |                                                 |

- Create: status sempre `ATIVO`.
- Sem soft delete; arquivar/reativar via endpoints de ação.
- Nome **não** precisa ser único por usuário.
- Índice: `(usuario_id, status)` para listagens.

### Tarefa (extensão)

| Campo     | Tipo | Observação |
|-----------|------|------------|
| projetoId | UUID | nullable; FK `Projeto`; mesmo `usuarioId` da tarefa |

- Create/PATCH: `projetoId` opcional. `null` ou omitido no create = sem projeto; no PATCH, `projetoId: null` **desvincula**.
- Projeto de outro usuário / inexistente → tratar como recurso inválido (**404** ou validação **400** com Problem Detail — preferir **404** se id “sumiu” no escopo; **400** se malformado).
- Projeto `ARQUIVADO`: **rejeitar** nova associação (**409** ou **400** com mensagem clara). Vínculos já existentes permanecem.
- Soft delete / restore da tarefa **não** altera `projetoId`.
- Response de tarefa inclui `projetoId` e, quando houver, `projetoNome` (enrich na leitura; sem N+1 — join ou batch).

### ReservaProjetoSemanal

| Campo              | Tipo             | Observação |
|--------------------|------------------|------------|
| id                 | UUID             | PK |
| usuarioId          | UUID             | FK; dono |
| projetoId          | UUID             | FK `Projeto` do mesmo usuário |
| diaSemana          | enum `DiaSemana` | `SEGUNDA` … `DOMINGO` (mesmo de capacidade) |
| minutosReservados  | int              | `>= 15`, múltiplo de **15** (alinhado a `tarefa-duracao-minutos.md`) |
| createdAt          | Instant          | |
| updatedAt          | Instant          | |

**Unicidade:** no máximo um registro por `(usuarioId, projetoId, diaSemana)`.

**Invariante de soma:** para um `(usuarioId, diaSemana)`,  
`sum(minutosReservados) <= capacidadeSemanal.horas * 60` do template **ativo** daquele weekday.  
Se não existir template ativo (dia vazio) → soma deve ser 0; criar reserva → **409** (não há capacidade de template para reservar).

Reduzir `horas` do template semanal de forma que `horas * 60 < sum(reservas)` → **409** na escrita de capacidade (mensagem pedindo reduzir reservas antes).

Projeto `ARQUIVADO`: rejeitar **nova** reserva e alteração que aumente minutos; permitir reduzir/remover. Reservas existentes continuam válidas para o motor enquanto o registro existir (tarefas arquivadas ainda podem ser alocadas na fase 1 se elegíveis).

---

## Motor (dia D / janela)

Entradas adicionais: reservas do weekday de cada dia da janela + `projetoId` das elegíveis.

Para cada dia `D` na geração:

1. `capacidadeMinutos` = resolução vigente (`horas * 60` ou 0).
2. `reservasPedido` = reservas do `DiaSemana` de `D` (projetos com `minutosReservados > 0`).
3. `reservadoEfetivoTotal = min(sum(reservasPedido), capacidadeMinutos)`.
4. Distribuir o teto efetivo entre projetos de forma **determinística**: ordem por `minutosReservados` desc, empate `projetoId` asc; cada um recebe `min(pedido, restanteDoTetoEfetivo)`.
5. **Fase 1:** para cada projeto na ordem acima, alocar tarefas elegíveis com `projetoId` igual (score / saldo / teto-piso de bloco vigentes) até a fatia do projeto ou acabar elegíveis.
6. Somar minutos **não consumidos** das fatias → somam ao restante do dia.
7. **Fase 2:** pool livre = `capacidadeMinutos - minutosJáAlocados`; alocação global por score (tarefas de qualquer projeto ou sem projeto, com saldo restante).
8. Regras já existentes (não alocar após prazo, capacidade 0, conflito de prazo, etc.) permanecem.

Override com menos horas que a soma do template: só o clamp do passo 3; **não** bloquear o override.

---

## Back-end

Todos autenticados (JWT). Sem token → **401**. Outro usuário / id inexistente → **404**. Sem cache. Prefixo novo: **`/projetos`** (Vite + Nginx + `API_PROXY.md`).

Pacote sugerido: `projeto` (entity, repo, service, controller, DTOs). Reserva pode viver no mesmo pacote. Extensões em `tarefa` e no motor/capacidade existentes via construtor.

### Projeto

1. **POST /projetos**  
   - Body: `nome`, `descricao?`.  
   - Status gravado: `ATIVO`.  
   - 201 + projeto.

2. **GET /projetos**  
   - Lista do usuário.  
   - Query: `status` (`ATIVO` \| `ARQUIVADO`; default **só `ATIVO`**); `page`/`size` (mesmos defaults de tarefas: 0 / 20 / máx. 100).  
   - Ordenação padrão: `nome` asc.

3. **GET /projetos/{id}**  
   - Inclui arquivado do dono.

4. **PATCH /projetos/{id}**  
   - Parcial: `nome`, `descricao`.  
   - **Não** muda status (usar arquivar/reativar).

5. **POST /projetos/{id}/arquivar**  
   - `ATIVO` → `ARQUIVADO`. Já arquivado → **409**.  
   - Não mexe em `Tarefa.projetoId`.

6. **POST /projetos/{id}/reativar**  
   - `ARQUIVADO` → `ATIVO`. Já ativo → **409**.

### Reserva semanal

7. **POST /projetos/{projetoId}/reservas**  
   - Body: `diaSemana`, `minutosReservados`.  
   - Projeto arquivado / inexistente → 404/409 conforme regras.  
   - Unicidade ou soma &gt; capacidade template → **409**.  
   - 201 + reserva.  
   - **Gatilho:** proposta de replanejamento (dias impactados / janela vigente).

8. **GET /projetos/{projetoId}/reservas**  
   - Lista das 0..7 reservas do projeto.

9. **GET /projetos/reservas**  
   - Lista do usuário; filtros opcionais `diaSemana`, `projetoId`.  
   - Uso: grade de capacidade / explicação do dia.

10. **PATCH /projetos/{projetoId}/reservas/{reservaId}**  
    - Parcial: `minutosReservados` e/ou `diaSemana` (se mudar dia, revalidar unicidade + soma).  
    - Gatilho de proposta.

11. **DELETE /projetos/{projetoId}/reservas/{reservaId}**  
    - Remove o registro (hard delete da reserva — não é soft).  
    - Já inexistente → **404**.  
    - Gatilho de proposta.

### Tarefa (extensão — sem prefixo novo)

12. **POST /tarefas** e **PATCH /tarefas/{id}**  
    - Aceitam `projetoId` opcional; PATCH com `null` desvincula.  
    - Validações de projeto ativo / escopo acima.

13. **GET /tarefas**  
    - Filtro opcional `projetoId` (UUID).  
    - Response: `projetoId`, `projetoNome` quando houver.

14. **Capacidade semanal PATCH/POST**  
    - Ao reduzir `horas`, validar invariante das reservas → **409** se estourar.

15. **Motor + gatilho**  
    - Fase 1/2 conforme Conceito; reserva create/update/delete dispara proposta como mudança de capacidade.

### Response / DTO

- Projeto: campos da entity (sem vazamento de outros usuários).  
- Reserva: `id`, `projetoId`, `diaSemana`, `minutosReservados`, timestamps.  
- Opcional na capacidade resolvida / home: enrich depois na UI via `GET /projetos/reservas` (não obrigatório alterar `GET /capacidades/resolvida` neste slice).

---

## Front-end

Fluxo mínimo neste slice de negócio (detalhe visual em `plans/ui/ui-projetos.md`):

1. CRUD/lista de projetos + arquivar/reativar.
2. Formulário/lista de tarefas: select de projeto; botão **Desvincular**; filtro por projeto.
3. Grade de capacidade (Seg–Dom): por dia, editar reservas por projeto (minutos) com soma vs horas do template; erro 409 legível.
4. Home/agenda/revisão: chip/nome do projeto no card do bloco quando houver; texto curto explicando fatia reservada vs livre no dia (dados de reservas + capacidade).
5. Ao salvar reserva: se API indicar proposta gerada (ou padrão já usado em capacidade), CTA de revisar/aceitar — **não** auto-aprovar.
6. Proxy: `/projetos` em `vite.config.ts`, `nginx.conf`, `API_PROXY.md` + rebuild/restart conforme doc.

---

## Decisões fechadas (revisão)

1. Um plano de negócio para núcleo + reserva; GitHub em `github-link.md` separado.
2. Status só `ATIVO` / `ARQUIVADO` (sem soft delete de projeto).
3. Arquivar **mantém** `projetoId` nas tarefas; desvínculo explícito (`null` + botão).
4. Reserva **somente** no template semanal (`DiaSemana`), em minutos (mín. 15, múltiplo de 15).
5. Motor regra **A**: fase reservada por projeto → sobra ao pool → score global (projeto pode receber mais).
6. Reserva não usada **volta** ao pool livre; clamp se capacidade resolvida &lt; soma pedida.
7. Mudança de reserva gera **proposta/rascunho**, não rearranjo silencioso do aprovado.
8. Soma das reservas do weekday ≤ `horas` do template; senão 409. Reduzir capacidade abaixo da soma → 409.
9. Prefixo `/projetos` + proxy. Sem cache.
10. Issue #40 (parte núcleo + reserva).

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*/F*) que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Motor: dia 180 min, reserva 60 projeto A → aloca até 60 de A na fase 1; sobra 120 no pool; A elegível ainda pode entrar na fase 2 | B13 | Regra A / aceite |
| T2 | Motor: reserva 60 sem tarefas elegíveis de A → 60 voltam ao pool; outras tarefas usam 180 | B13 | Sobra → pool livre |
| T3 | Motor: capacidade resolvida 60 com reservas pedidas 90 → efetivo 60 (clamp); não estoura o dia | B13 | Override/template menor |
| T4 | Validação: soma reservas &gt; horas template → rejeitada | B9 | Invariante |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T5 | CRUD projeto + arquivar/reativar; 409 ao arquivar de novo | B2–B7 | Aceite CRUD |
| T6 | Associar tarefa a projeto ATIVO; rejeitar vínculo a ARQUIVADO; PATCH `projetoId: null` desvincula; filtro `projetoId` | B8 | Aceite vínculo |
| T7 | POST reserva ok; segunda no mesmo `(projeto, dia)` → 409; soma &gt; capacidade → 409 | B9–B10 | Aceite reserva |
| T8 | Reduzir horas do template abaixo da soma das reservas → 409 | B14 | Invariante cruzada |
| T9 | Create/update/delete reserva com plano aprovado no weekday → gera proposta/rascunho; aprovado intacto | B15 | Replanejamento |
| T10 | Sem token → 401; projeto de outro usuário → 404 | B3 | Auth/escopo |

### Front (mínimo neste slice / detalhe em UI)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T11 | Proxy/path `/projetos` registrado (checklist ou teste de config se o repo já cobrir) | F1 | Evitar `Unexpected token '<'` |

Harness front: se ainda limitado, T11 pode ser verificação manual + diff em `API_PROXY.md`; comportamentos de UI finos ficam em `ui-projetos.md`.

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Modelo** | Entities `Projeto`, `ReservaProjetoSemanal`, enum `StatusProjeto`; coluna `tarefa.projeto_id`; Flyway; repositories; **sem** endpoints |
| **B2** | **POST /projetos** | Create `ATIVO`; validações nome/descrição; **T5** (create), **T10** parcial |
| **B3** | **GET /projetos** | Lista + filtro status + paginação; **T10** |
| **B4** | **GET /projetos/{id}** | Detalhe inclusive arquivado |
| **B5** | **PATCH /projetos/{id}** | nome/descricao |
| **B6** | **POST …/arquivar** | **T5** |
| **B7** | **POST …/reativar** | **T5** |
| **B8** | **Vínculo tarefa** | Extensão POST/PATCH/GET `/tarefas` (`projetoId`, filtro, `projetoNome`); **T6** |
| **B9** | **POST …/reservas** | Create + invariante soma/unicidade; **T4**, **T7** |
| **B10** | **GET reservas** | `GET /projetos/{id}/reservas` **e** `GET /projetos/reservas` (duas leituras: preferir B10 = lista do projeto; B10b abaixo) |
| **B10b** | **GET /projetos/reservas** | Lista consolidada com filtros (1 endpoint) |
| **B11** | **PATCH …/reservas/{id}** | Update + revalidação |
| **B12** | **DELETE …/reservas/{id}** | Hard delete |
| **B13** | **Motor fases 1–2** | Integração no gerador; **T1–T3** |
| **B14** | **Capacidade × reservas** | 409 ao reduzir horas; **T8** |
| **B15** | **Gatilho proposta** | Em create/update/delete reserva; **T9** |

> **B10 / B10b:** dois GETs novos = duas atividades (regra “1 endpoint novo por B*”).

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Proxy + client API** | `/projetos` em Vite/Nginx/`API_PROXY.md`; client mínimo; **T11** |
| **F2** | **Lista/form projeto** | CRUD + arquivar/reativar (pode ser tela simples até `ui-projetos.md`) |
| **F3** | **Tarefa ↔ projeto** | Select, desvincular, filtro na lista |
| **F4** | **Reservas na capacidade** | Edição Seg–Dom por projeto; feedback 409 |
| **F5** | **Projeto no plano do dia** | Chip no bloco + texto reserva vs livre; CTA proposta se houver |

### Ordem

```text
B1 → B2 → B3 → B4 → B5 → B6 → B7 → B8
              ↘
               B9 → B10 → B10b → B11 → B12 → B14 → B15
                                              ↘
                                               B13
F1 após B2 (prefixo existe)
F2 após B7
F3 após B8
F4 após B12 + B14
F5 após B13 + B15
```
)
