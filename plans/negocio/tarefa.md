# Feature — Tarefa (segundo slice)

Escopo: CRUD de tarefas do usuário autenticado — entrada do produto para o planner.  
Fora deste slice: quebra em blocos, planner, capacidade, aprovação de plano, execução, XP, recorrência, notificações.

---

## Entity

| Campo       | Tipo                          | Observação                                              |
|-------------|-------------------------------|---------------------------------------------------------|
| id          | UUID                          | PK                                                      |
| usuarioId   | UUID                          | FK; dono da tarefa (sempre o autenticado)               |
| titulo      | String                        | obrigatório; max 120                                    |
| descricao   | String                        | obrigatório; max 2000                                   |
| prioridade  | enum `Prioridade`             | obrigatório: `ALTA`, `MEDIA`, `BAIXA`                   |
| prazo       | Instant                       | obrigatório; rejeitar se no passado (create/update)     |
| status      | enum `StatusTarefa`           | ver regras abaixo                                       |
| duracaoHoras| int                           | obrigatório; unidade horas; mínimo 1; sem máximo        |
| createdAt   | Instant                       |                                                         |
| updatedAt   | Instant                       |                                                         |
| deletedAt   | Instant                       | null = ativa; preenchido = soft delete                  |

### Status (`StatusTarefa`)

Valores: `PENDENTE`, `EM_PROGRESSO`, `FINALIZADA`, `ATRASADA`, `CANCELADA`.

- Create: status **sempre** `PENDENTE` (ignorar/rejeitar status enviado no body).
- Qualquer status pode virar qualquer outro via `PATCH` (incluindo `ATRASADA`), com a restrição abaixo.
- `ATRASADA` com prazo ainda no futuro: **rejeitar** com mensagem de erro clara.
- Se status é `ATRASADA` e o `PATCH` move o prazo para o futuro: gravar status como `PENDENTE` automaticamente.
- `EM_PROGRESSO`, `FINALIZADA` e `CANCELADA` **não** são alterados pelo job de atraso.

### Job de atraso (a cada 30 min)

1. `PENDENTE` + `prazo < agora` → `ATRASADA`
2. `ATRASADA` + `prazo >= agora` → `PENDENTE`

---

## Back-end

Todos os endpoints autenticados. Escopo sempre pelo usuário do JWT (sem acesso a tarefa de outro → 404).

### Endpoints

1. **POST /tarefas**  
   - Body: `titulo`, `descricao`, `prioridade`, `prazo`, `duracaoHoras`.  
   - Validações: tamanhos, prioridade válida, `duracaoHoras >= 1`, `prazo` **não no passado**.  
   - Status gravado: `PENDENTE`.  
   - Resposta: 201 + tarefa.

2. **GET /tarefas**  
   - Lista paginada do usuário.  
   - Paginação: `page` (default 0), `size` (default **20**, máximo **100**).  
   - Ordenação padrão: `prazo` asc (mais próximo primeiro).  
   - `sort` permitido: `prazo`, `duracao`, `prioridade` + direção `asc`/`desc`.  
   - Filtros (combináveis):
     - `status` — **vários** valores
     - `prioridade` — **vários** valores
     - `prazoDe` / `prazoAte`
     - `duracaoDe` / `duracaoAte`
     - `criadoDe` / `criadoAte`
     - `atualizadoDe` / `atualizadoAte`
     - `incluirDeletadas` / `somenteDeletadas` (default: só ativas)
   - Soft-deletadas **fora** da listagem padrão.

3. **GET /tarefas/{id}**  
   - Devolve a tarefa do usuário, **inclusive** se soft-deletada (response indica deletada).  
   - Outro usuário / id inexistente: 404.

4. **PATCH /tarefas/{id}**  
   - Atualização parcial dos campos editáveis.  
   - Regras de status/prazo conforme entity.  
   - Não “restaura” por si só (`deletedAt` inalterado aqui).

5. **DELETE /tarefas/{id}**  
   - Soft delete: preenche `deletedAt`.  
   - Já deletada ou inexistente / outro usuário: **404**.

6. **POST /tarefas/{id}/restore**  
   - Limpa `deletedAt`.  
   - Só tarefas do usuário.

### Response (campos relevantes)

Incluir `deletedAt` e flag booleana `deletada` (`deletedAt != null`).

### Job

Scheduler a cada **30 minutos** com as duas regras de `ATRASADA` ↔ `PENDENTE` acima. Apenas tarefas **não** soft-deletadas.

---

## Front-end

1. **Lista de tarefas** — tabela/lista com barra de filtros (status e prioridade multi-valor; intervalos de prazo, duração, criado, atualizado); paginação; ordenação pelos campos permitidos; indicação visual de status (incl. atrasada).

2. **Criar / editar na mesma rota** — formulário único (ex.: `/tarefas/nova` e `/tarefas/:id`); campos: título, descrição, prioridade, prazo, duração (horas inteiras). Status no create não é escolhido (sempre pendente); no edit, select de status com as regras de erro do back (ex.: atrasada com prazo futuro).

3. **Excluir / restaurar** — exclusão soft na UI; quando a API devolver tarefa deletada (detalhe ou filtro), ação de restaurar.

Rotas autenticadas; reutilizar sessão/refresh do slice de usuário.

---

## Decisões fechadas (revisão)

1. Campo canônico: `titulo` (não `nome`); `descricao` obrigatória.
2. Limites: título 120; descrição 2000.
3. `prazo` e auditoria (`createdAt` / `updatedAt` / `deletedAt`) como `Instant`.
4. `prazo` obrigatório; rejeitar no passado em create/update.
5. Prioridade obrigatória: `ALTA`, `MEDIA`, `BAIXA`.
6. Duração em **horas inteiras**, mínimo 1, sem máximo (`duracaoHoras`).
7. Status persistido inclui `ATRASADA`; create sempre `PENDENTE`.
8. Transições: qualquer status → qualquer status via `PATCH`, exceto `ATRASADA` com prazo futuro (erro explicativo).
9. Prazo futuro em tarefa `ATRASADA` (via `PATCH` de prazo) → status vira `PENDENTE`.
10. Job 30 min: `PENDENTE`→`ATRASADA` (prazo passou); `ATRASADA`→`PENDENTE` (prazo no futuro); não mexe em `EM_PROGRESSO` / `FINALIZADA` / `CANCELADA`.
11. Soft delete + restore; listagem padrão sem deletadas; `GET` por id devolve deletada marcada.
12. API sob `/tarefas`, só do autenticado; update via `PATCH`.
13. Paginação default 20 / max 100; sort default `prazo` asc; sort liberado: `prazo`, `duracao`, `prioridade`.
14. Filtros com intervalos `de`/`ate`; `status` e `prioridade` multi-valor.
15. Fora: planner, blocos, capacidade, XP, recorrência.
16. Este é o **segundo slice** (depois de usuário).

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Modelo + create/list** | Entidade, Flyway, enums, `POST /tarefas`, `GET /tarefas` com paginação/filtros/sort básicos, escopo por usuário, testes |
| **B2** | **Get / patch / soft delete / restore** | `GET /{id}`, `PATCH` com regras de status/prazo, `DELETE` soft, `POST /{id}/restore`, resposta com `deletada` |
| **B3** | **Job de atraso + testes** | Scheduler 30 min (`PENDENTE`↔`ATRASADA`), garantir que não altera outros status; testes do slice |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Lista + filtros** | Tela de listagem, filtros, paginação, ordenação, chamada autenticada à API |
| **F2** | **Formulário criar/editar** | Mesma rota/fluxo para nova e edição; validações alinhadas ao back |
| **F3** | **Excluir / restaurar + status** | Soft delete e restore na UI; tratamento de erros de status/prazo |

### Ordem

```text
B1 → B2 → B3
      ↘
       F1 → F2 → F3
```
