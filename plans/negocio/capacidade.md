# Feature — Capacidade (terceiro slice)

Escopo: disponibilidade semanal reutilizada (Seg–Dom) + override por data concreta; entrada do planner.  
Fora deste slice: planner, blocos, aprovação de plano, execução, XP, recorrência, notificações, calendário completo.

---

## Conceito

1. **Template semanal** — horas disponíveis por `DiaSemana` (SEGUNDA … DOMINGO), reutilizado toda semana.
2. **Override por data** — exceção para um dia específico (ex.: “hoje só 1h”). Quando existir override ativo para a data, ele prevalece sobre o template do dia da semana.
3. Dia sem registro no template = dia vazio (sem capacidade naquele weekday). `horas = 0` também é válido (capacidade explícita zero).

---

## Entities

### CapacidadeSemanal (template)

| Campo       | Tipo                | Observação                                              |
|-------------|---------------------|---------------------------------------------------------|
| id          | UUID                | PK                                                      |
| usuarioId   | UUID                | FK; dono (sempre o autenticado)                         |
| diaSemana   | enum `DiaSemana`    | `SEGUNDA` … `DOMINGO`                                   |
| horas       | int                 | obrigatório; `>= 0`; sem máximo                         |
| ativo       | boolean             | `true` = vigente; `false` = soft delete                 |
| createdAt   | Instant             |                                                         |
| updatedAt   | Instant             |                                                         |

**Unicidade:** no máximo **um** registro com `ativo = true` por `(usuarioId, diaSemana)`.

### CapacidadeOverride (exceção por data)

| Campo       | Tipo                | Observação                                              |
|-------------|---------------------|---------------------------------------------------------|
| id          | UUID                | PK                                                      |
| usuarioId   | UUID                | FK; dono (sempre o autenticado)                         |
| data        | LocalDate           | dia concreto (ex.: 2026-10-08)                          |
| horas       | int                 | obrigatório; `>= 0`; sem máximo                         |
| ativo       | boolean             | `true` = vigente; `false` = soft delete                 |
| createdAt   | Instant             |                                                         |
| updatedAt   | Instant             |                                                         |

**Unicidade:** no máximo **um** registro com `ativo = true` por `(usuarioId, data)`.

### Resolução para o planner (contrato deste slice)

Para uma data `D`:

1. Se existe override ativo com `data = D` → usar `horas` do override.
2. Senão, se existe template ativo com `diaSemana` de `D` → usar `horas` do template.
3. Senão → dia sem capacidade (equivalente a vazio; o planner trata depois).

Este slice **expõe** a resolução (endpoint de consulta); a distribuição de tarefas fica no `planner.md`.

---

## Soft delete e restore

- Exclusão: `ativo = false` (não remove a linha).
- Restore: `ativo = true`.
- Se ao restaurar já existir outro registro **ativo** no mesmo `(usuario, diaSemana)` ou `(usuario, data)` → **rejeitar** com erro claro (conflito de unicidade).
- Listagens padrão: só `ativo = true`.
- Filtros: `incluirDeletadas` / `somenteDeletadas` (mesmo espírito de tarefas).

---

## Back-end

Todos os endpoints autenticados. Escopo sempre pelo usuário do JWT (outro usuário / id inexistente → **404**).

### Capacidade semanal

1. **POST /capacidades/semanal**  
   - Body (um dia): `diaSemana`, `horas`.  
   - Ou lote: lista de `{ diaSemana, horas }`.  
   - Validações: enum válido, `horas >= 0`, sem duplicar `diaSemana` no mesmo lote.  
   - Conflito com template já ativo no mesmo dia → **409** (ou mensagem clara).  
   - Resposta: 201 + registro(s).

2. **GET /capacidades/semanal**  
   - Lista do usuário.  
   - Default: só `ativo = true`.  
   - Filtros: `incluirDeletadas`, `somenteDeletadas`.  
   - Ordenação sugerida: ordem do enum (Seg → Dom).

3. **GET /capacidades/semanal/{id}**  
   - Inclusive inativo (response indica `ativo`).

4. **PATCH /capacidades/semanal/{id}**  
   - Atualização parcial: `horas` e/ou `diaSemana`.  
   - Se mudar `diaSemana` para um que já tem ativo → conflito.  
   - Não restaura por si só (`ativo` inalterado aqui).

5. **DELETE /capacidades/semanal/{id}**  
   - Soft delete: `ativo = false`.  
   - Já inativo / inexistente / outro usuário → **404**.

6. **POST /capacidades/semanal/{id}/restore**  
   - `ativo = true`, com regra de unicidade acima.

### Capacidade override (por data)

7. **POST /capacidades/overrides**  
   - Body (um): `data`, `horas`.  
   - Ou lote: lista de `{ data, horas }`.  
   - Validações: `horas >= 0`, datas válidas, sem duplicar `data` no lote.  
   - Conflito com override já ativo na mesma data → **409**.

8. **GET /capacidades/overrides**  
   - Lista do usuário; default só ativos.  
   - Filtros: `incluirDeletadas`, `somenteDeletadas`; opcional `dataDe` / `dataAte`.

9. **GET /capacidades/overrides/{id}**  
   - Inclusive inativo.

10. **PATCH /capacidades/overrides/{id}**  
    - Parcial: `data` e/ou `horas`; conflito se `data` colidir com outro ativo.

11. **DELETE /capacidades/overrides/{id}**  
    - Soft delete (`ativo = false`); já inativo → **404**.

12. **POST /capacidades/overrides/{id}/restore**  
    - Com regra de unicidade por data.

### Consulta resolvida (para UI e futuro planner)

13. **GET /capacidades/resolvida**  
    - Query: `dataDe`, `dataAte` (obrigatórios; intervalo limitado, ex. máx. 31 dias — a definir na implementação se quiser travar abuso).  
    - Para cada data no intervalo: `horas` resolvidas + origem (`SEMANAL` | `OVERRIDE` | `VAZIO`).  
    - Só considera registros `ativo = true`.

### Response

Incluir `ativo` em todos os recursos. Em listagens/detalhe de deletados, `ativo = false` basta (não há `deletedAt` neste slice).

---

## Front-end

1. **Grade da semana** — uma tela principal com Seg–Dom: horas do template por dia; criar/editar inline ou por célula; dia sem valor = vazio; permitir `0`. Ações de excluir (soft) e, com filtro/visão de inativos, restaurar.

2. **Overrides** — na mesma experiência ou seção da grade: destacar / editar exceções por data (“hoje só Xh”); listar overrides próximos; excluir/restaurar com os mesmos filtros.

3. **Consulta resolvida** — a grade pode mostrar, para a semana corrente (ou selecionada), as horas efetivas (template vs override) via `GET /capacidades/resolvida`.

Rotas autenticadas; reutilizar sessão/refresh do slice de usuário.

---

## Decisões fechadas (revisão)

1. Template semanal reutilizado toda semana (`DiaSemana` Seg–Dom); dia pode ficar sem registro (vazio).
2. Override por `LocalDate` **entra neste slice** e prevalece sobre o template.
3. Unicidade: um ativo por `(usuario, diaSemana)` e um ativo por `(usuario, data)`.
4. Soft delete via campo `ativo` (não `deletedAt`); restore obrigatório neste slice.
5. `horas` em **int**, `>= 0`, sem máximo; `0` permitido.
6. Create unitário **ou** em lote (semanal e override).
7. API autenticada, escopo JWT; conflito de unicidade no create/restore → erro claro (409).
8. Filtros `incluirDeletadas` / `somenteDeletadas` nas listagens.
9. Front: **grade da semana** (+ overrides), não telas CRUD separadas por operação.
10. Endpoint de capacidade **resolvida** por intervalo de datas (contrato para o planner).
11. Fora: motor do planner, aprovação, execução, XP.
12. Este é o **terceiro slice** (depois de usuário e tarefa).

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Template semanal** | Entidade, Flyway, enum, unicidade parcial/ativo, `POST` (unitário+lote), `GET` lista com filtros de deletadas, testes |
| **B2** | **CRUD semanal completo** | `GET/{id}`, `PATCH`, `DELETE` soft, `POST .../restore` com conflito, testes |
| **B3** | **Overrides + resolvida** | Entidade override, mesmos endpoints, `GET /capacidades/resolvida`, testes do slice |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Grade semanal** | Tela Seg–Dom, create/edit de horas, chamada autenticada |
| **F2** | **Soft delete / restore + filtros** | Excluir, restaurar, `incluirDeletadas` / `somenteDeletadas` |
| **F3** | **Overrides + resolvida na grade** | Exceções por data; exibir horas efetivas da semana |

### Ordem

```text
B1 → B2 → B3
      ↘
       F1 → F2 → F3
```
