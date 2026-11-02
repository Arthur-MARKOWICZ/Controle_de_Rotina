# Feature — Hábitos leves integrados ao planner

Issue: [#43](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/43)

Escopo: CRUD de hábito (`ATIVO` / `PAUSADO` + soft delete); dias da semana + duração; horário preferencial opcional (só UI); motor inclui hábitos do weekday na disputa de capacidade com **score fixo 85**; conflito visível com itens não alocados para o usuário escolher o que fica de fora no rascunho; bloco com `habitoId` (origem distinta de tarefa); execução/XP do bloco sem `TAREFA_FINALIZADA`; streak simples; UI mínima + proxy `/habitos`.  
Fora deste slice: rede social / hábitos compartilhados; RRULE completa; árvore/marketplace/recompensas; vínculo hábito→projeto; substituir domínio de `Tarefa`; IA; notificações push de lembrete.

Dependências: `tarefa.md` (+ duração minutos), `capacidade.md`, `planner.md` (+ janela, saldo, conflito, sem prazo, replanejamento), `execucao.md`, `xp.md` (+ tempo efetivo). Não depende de Projetos; hábitos entram só no **pool livre** (após reservas de projeto = fase 1).

---

## Conceito

1. **Hábito** é recorrência leve: título, duração, dias da semana. Não vira tarefa pontual nem app de streak social.
2. No dia `D`, hábitos `ATIVO`, não deletados, cujo `DiaSemana` de `D` está na lista, entram como candidatos do motor junto às tarefas elegíveis do **pool livre**.
3. **Score fixo 85** (abaixo de prazos urgentes típicos; acima de tarefas sem prazo de prioridade média/baixa). Empate com tarefa: **tarefa primeiro**; depois `createdAt` mais antigo.
4. Capacidade insuficiente: hábito pode ficar de fora ou truncado → `temConflito = true` + lista estruturada `itensNaoAlocados` (e/ou blocos truncados). No rascunho o usuário remove blocos (tarefa ou hábito) ou adiciona o que couber — **escolhe o que fica**.
5. `BlocoPlano` tem origem XOR: `tarefaId` **ou** `habitoId`. Execução 1:1 no bloco; concluir hábito **não** altera `Tarefa` nem gera `TAREFA_FINALIZADA`.
6. XP do bloco: mesma fórmula vigente (`xp-tempo-efetivo.md`) com peso de prioridade **MEDIA (1.5)** — hábito não tem prioridade própria.
7. **Streak:** dias consecutivos de cumprimento nos weekdays agendados; campos desnormalizados no hábito + recálculo na conclusão / leitura.

---

## Entity / Entities

### Habito

| Campo                 | Tipo                     | Observação |
|-----------------------|--------------------------|------------|
| id                    | UUID                     | PK |
| usuarioId             | UUID                     | FK; dono (JWT) |
| titulo                | String                   | obrigatório; max 120 |
| descricao             | String                   | opcional; max 2000 |
| duracaoMinutos        | int                      | obrigatório; `>= 15`; múltiplo de **15** (igual tarefa) |
| diasSemana            | set/`DiaSemana`          | obrigatório; **≥ 1** dia; enum igual capacidade (`SEGUNDA`…`DOMINGO`) |
| horarioPreferencial   | LocalTime nullable       | opcional; **só exibição**; motor **não** ordena por ele |
| status                | enum `StatusHabito`      | `ATIVO`, `PAUSADO` |
| streakAtual           | int                      | `>= 0`; default 0 |
| melhorStreak          | int                      | `>= 0`; default 0 |
| createdAt             | Instant                  | |
| updatedAt             | Instant                  | |
| deletedAt             | Instant nullable         | null = ativo no soft-delete |

- Create: status sempre `ATIVO`; streaks 0.
- Pausar/reativar via ações (não via PATCH de status genérico).
- Soft delete + restore (espírito de `tarefa.md`).
- Índice: `(usuario_id, status)` e filtro por `deleted_at`.
- Persistência de `diasSemana`: tabela filha `(habito_id, dia_semana)` **ou** coluna adequada já usada no projeto — preferir tabela filha se não houver padrão de collection element; sem JSON opaco se o time evitar.

### BlocoPlano (extensão)

| Campo    | Tipo | Observação |
|----------|------|------------|
| tarefaId | UUID nullable | FK tarefa; **null** se origem hábito |
| habitoId | UUID nullable | FK hábito; **null** se origem tarefa |

**Invariante:** exatamente um de `tarefaId` / `habitoId` preenchido (CHECK no Flyway + validação no service).  
Um hábito aparece **no máximo um bloco** por versão de plano (não quebra em vários pedaços ≤ 90 como tarefa grande — ver motor).

### Plano (extensão de response)

| Campo | Tipo | Observação |
|-------|------|------------|
| itensNaoAlocados | lista | candidatos elegíveis do dia que **não** receberam bloco (capacidade); ver DTO abaixo |

Não exige nova tabela: montado na geração/ajuste a partir do que restou na fila.

### DTO item não alocado

| Campo | Tipo | Observação |
|-------|------|------------|
| tipo | enum `TAREFA` \| `HABITO` | |
| id | UUID | tarefaId ou habitoId |
| titulo | String | |
| duracaoMinutos | int | pedido completo |
| motivo | enum | v1: só `CAPACIDADE` |

---

## Motor (pool livre / dia D)

Entradas adicionais: hábitos `ATIVO`, `deletedAt == null`, com `DiaSemana` de `D` ∈ `diasSemana`.

1. Manter fase de **reserva de projeto** (se existir) inalterada — hábitos **não** entram na fase 1 (fase 1 = só reservas, não “tarefas com prazo”).
2. No pool livre, candidatos = tarefas ainda elegíveis com saldo (**com ou sem prazo**) + hábitos do dia.
3. Score:
   - Tarefa: Score V1 vigente (incl. sem prazo / urgência).
   - Hábito: **85** + fator duração (mesma tabela de pontos por faixa de minutos do `planner.md`).
4. Ordenar por score desc; empate: origem `TAREFA` antes de `HABITO`; depois `createdAt` asc.
5. Alocação de hábito:
   - Se `duracaoMinutos <= restante` e `restante >= 30` (ou regras de piso vigentes): um bloco com duração total; consome minutos.
   - Se não cabe inteiro e `restante >= 30`: **não** cria bloco parcial de hábito no v1 — hábito vai para `itensNaoAlocados` com `CAPACIDADE` (evita “meio hábito”); `temConflito = true`.
   - Se `restante < 30`: hábito não alocado → `itensNaoAlocados`; se ainda houver candidatos, conflito conforme regras já usadas para sobra &lt; piso.
6. Tarefas continuam podendo truncar/partir conforme planos já feitos; hábitos omitidos ou qualquer truncamento relevante mantêm `temConflito` + mensagem legível.
7. Janela multi-dia: em cada dia `D` da janela, repetir a regra com os hábitos daquele weekday (hábito não tem “saldo” multi-dia — ou cabe no dia ou entra em omitidos daquele dia).

**Adiar no rascunho** = `DELETE` do bloco do hábito (igual tarefa). Não há endpoint “adiar para amanhã” no v1.

---

## Execução e XP

### Execução

- Bloco de hábito **no plano `APROVADO`:** pré-condições de corrente iguais a `execucao.md`.
- **Cumprimento sem plano aprovado** (bloco implícito `HABITO_AVULSO`, concluir direto, calendário): ver `habito-sem-plano.md` — sobrescreve a ideia de “só executa se estiver no aprovado”.
- Bloco com `habitoId`:
  - **Iniciar / abortar / concluir:** sem alterar status de `Tarefa`.
  - Listagem: enrich com `habitoResumo` (`id`, `titulo`, `horarioPreferencial?`, `streakAtual`) em vez de (ou além de) `tarefaResumo` nulo.
- Bloco com `tarefaId`: comportamento atual.

### XP

- Ao concluir execução de bloco de hábito: evento `BLOCO_CONCLUIDO` com fórmula de tempo efetivo vigente.
- Peso = **MEDIA (1.5)**.
- **Não** emitir `TAREFA_FINALIZADA`.
- Idempotência `(tipo, origemId)` inalterada (`origemId` = `execucaoId`).

### Streak

**Cumprimento do dia `D`:** existe pelo menos uma `Execucao` `CONCLUIDO` cujo bloco tem `habitoId` = H e o `Plano.data` = `D` (qualquer versão; na prática a aprovada/executada).

**Atualização** (ao concluir bloco de hábito, na mesma transação):

1. Se já contava cumprimento em `D` → não muda streak (idempotente).
2. Senão: olhar o **último dia agendado** anterior a `D` (weekday ∈ `diasSemana`, timezone `America/Sao_Paulo`):
   - Se cumprido → `streakAtual += 1`.
   - Senão → `streakAtual = 1`.
3. `melhorStreak = max(melhorStreak, streakAtual)`.

**Quebra preguiçosa na leitura (GET hábito / lista):** se o último dia agendado **antes de hoje** não foi cumprido e `streakAtual > 0`, zerar `streakAtual` (persistir no GET é opcional; preferir **atualizar ao detectar** no service de leitura ou num passo antes de devolver — sem job novo no v1).

Streak **não** entra no histórico agregado (`historico.md`) neste slice — só nos campos do hábito / UI.

---

## Back-end

Todos autenticados (JWT). Sem token → **401**. Outro usuário / id inexistente → **404**. Sem cache.  
Prefixo novo: **`/habitos`** (`vite.config.ts` + `nginx.conf` + `API_PROXY.md`).

Pacote sugerido: `habito` (entity, repo, service, controller, DTOs). Extensões em `planner`, `execucao`, `xp` via construtor.

### Hábito

1. **POST /habitos**  
   - Body: `titulo`, `descricao?`, `duracaoMinutos`, `diasSemana[]`, `horarioPreferencial?`.  
   - Status `ATIVO`; 201 + hábito.

2. **GET /habitos**  
   - Default: não deletados.  
   - Query: `status` (`ATIVO`\|`PAUSADO`); `incluirDeletadas` / `somenteDeletadas`; `page`/`size` (0 / 20 / máx. 100).  
   - Ordenação padrão: `titulo` asc.

3. **GET /habitos/{id}**  
   - Inclui soft-deletado do dono (com flag `deletada`).

4. **PATCH /habitos/{id}**  
   - Parcial: `titulo`, `descricao`, `duracaoMinutos`, `diasSemana`, `horarioPreferencial` (null limpa horário).  
   - **Não** altera `status` nem `deletedAt` nem streaks manualmente.

5. **POST /habitos/{id}/pausar**  
   - `ATIVO` → `PAUSADO`. Já pausado → **409**. Soft-deletado → **404**.

6. **POST /habitos/{id}/reativar**  
   - `PAUSADO` → `ATIVO`. Já ativo → **409**.

7. **DELETE /habitos/{id}**  
   - Soft delete (`deletedAt`). Já deletado → **404**.

8. **POST /habitos/{id}/restore**  
   - Limpa `deletedAt`. Status permanece o que era (`ATIVO`/`PAUSADO`).

### Planner (extensão — sem prefixo novo)

9. **Geração / job**  
   - Inclui hábitos do dia conforme motor; preenche `itensNaoAlocados`; proxy já existente `/planos`.

10. **POST /planos/{id}/blocos**  
    - Body passa a aceitar **xor**: `tarefaId` **ou** `habitoId`.  
    - Hábito: deve estar `ATIVO`, não deletado, weekday do plano ∈ dias; ainda sem bloco deste hábito na versão; duração = `duracaoMinutos` do hábito.  
    - Estouro → persiste + `temConflito` (igual add de tarefa).

11. **DELETE bloco / aprovar / GET plano**  
    - Remover bloco de hábito ok no rascunho.  
    - Response de bloco: `origem` (`TAREFA`\|`HABITO`), `tarefaId`/`habitoId`, resumo correspondente (`titulo`, etc.).  
    - Plano inclui `itensNaoAlocados` quando houver.

### Execução / XP

12. Adaptar iniciar/concluir/listar para origem hábito (sem mutar tarefa; XP peso MEDIA; streak).

---

## Front-end

Mínimo neste slice de negócio (detalhe em `plans/ui/ui-habitos.md`):

1. Lista + criar/editar hábito (dias, duração, horário opcional); pausar / reativar; excluir / restaurar.
2. Home / revisão de rascunho: card de bloco de hábito distinto; banner de conflito lista `itensNaoAlocados` + ações remover bloco / adicionar hábito omitido se couber.
3. Execução: iniciar/concluir bloco de hábito; toast XP; exibir streak na lista/detalhe.
4. Proxy `/habitos`.

---

## Decisões fechadas (revisão)

1. Modelo **A:** bloco com `habitoId` (XOR com `tarefaId`); não materializa tarefa do dia.
2. Score fixo **85** + fator duração; empate favorece tarefa.
3. Hábito que não cabe **inteiro** → omitido com conflito visível (`itensNaoAlocados`); usuário ajusta o rascunho.
4. XP do bloco **igual** à fórmula vigente; peso **MEDIA (1.5)**; sem bônus `TAREFA_FINALIZADA`.
5. Streak simples neste slice (`streakAtual` / `melhorStreak`).
6. Status `ATIVO` / `PAUSADO` + soft delete/restore.
7. `horarioPreferencial` opcional, só informativo na UI.
8. Duração em minutos (≥ 15, múltiplo de 15); um bloco por hábito por versão.
9. Sem vínculo a projeto no v1; hábitos só no pool livre.
10. Prefixo `/habitos` + proxy. Sem cache.
11. Issue #43; UI detalhada em `ui-habitos.md`.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*/F*) que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Score hábito = 85 + fator duração; empate com tarefa de score 85 → tarefa primeiro | B2 | Aceite peso no planner |
| T2 | Dia D weekday no hábito `ATIVO` entra; `PAUSADO` / soft-delete / weekday errado não entram | B2 | Elegibilidade |
| T3 | Capacidade justa: hábito cabe → bloco; não cabe inteiro → omitido + `temConflito`, sem bloco parcial | B2 | Conflito visível |
| T4 | Pool: tarefas urgentes (score &gt; 85) antes do hábito; tarefa sem prazo MEDIA depois do hábito | B2 | “Abaixo de prazo urgente, acima de sem prazo” |
| T5 | Streak: cumprimento em D com dia agendado anterior cumprido → +1; anterior faltoso → 1; melhorStreak | B12 | Aceite streak |
| T6 | XP bloco hábito: efetivos × 1.5; sem evento `TAREFA_FINALIZADA` | B12 | Aceite XP |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T7 | POST/GET/PATCH hábito; validações duração/dias; 401 sem token | B3–B5 | CRUD |
| T8 | Pausar / reativar 409 nos casos inválidos | B6–B7 | Status |
| T9 | DELETE soft + restore; listagem padrão sem deletados; GET id deletado ok | B8–B9 | Soft delete |
| T10 | Outro usuário → 404 em todos os `/habitos/{id}` | B4 | Escopo JWT |
| T11 | POST blocos com `habitoId`; xor com `tarefaId`; hábito já no plano → 409 | B11 | Ajuste rascunho |
| T12 | GET plano: blocos com origem HABITO + `itensNaoAlocados` quando gerado sob pressão | B10 | Contrato conflito |
| T13 | Execução concluir bloco hábito: não muda tarefa; credita XP; atualiza streak | B12 | Aceite execução |

### Job / geração

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T14 | Job/gerar do dia inclui hábito do weekday e respeita skip se já `APROVADO` | B10 | Aceite job/geração |

### Front (mínimo neste slice)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T15 | Proxy `/habitos` registrado (checklist / smoke) | F1 | Evita HTML no JSON |

Detalhe visual e testes de UI mais ricos: `ui-habitos.md`.

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Entities + Flyway** | `Habito`, dias da semana, enums; `BlocoPlano.habitoId` nullable + CHECK xor; repos; **sem** endpoints |
| **B2** | **Motor** | Elegibilidade, score 85, alocação all-or-nothing, `itensNaoAlocados`, integração pool livre / janela; **T1–T4** |
| **B3** | **POST /habitos** | Create; validações; **T7** (create) |
| **B4** | **GET /habitos** e **GET /habitos/{id}** | Lista/filtros/paginação; detalhe; **T7** (get), **T10** |
| **B5** | **PATCH /habitos/{id}** | Parcial; **T7** (patch) |
| **B6** | **POST .../pausar** | **T8** |
| **B7** | **POST .../reativar** | **T8** |
| **B8** | **DELETE /habitos/{id}** | Soft delete; **T9** |
| **B9** | **POST .../restore** | **T9** |
| **B10** | **Geração / job / GET plano** | Hábitos na geração; response com origem + omitidos; **T12**, **T14** |
| **B11** | **POST /planos/{id}/blocos** | Aceitar `habitoId`; **T11** |
| **B12** | **Execução + XP + streak** | Concluir sem mutar tarefa; XP MEDIA; streak; **T5**, **T6**, **T13** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Proxy + CRUD mínimo** | `/habitos` em Vite/Nginx/`API_PROXY.md`; lista/form básicos; **T15** |
| **F2** | **Plano / conflito / execução** | Card hábito; omitidos; iniciar/concluir; streak na lista — fino; resto em `ui-habitos.md` |

### Ordem

```text
B1 → B2 → B3 → B4 → B5 → B6 → B7 → B8 → B9
              ↘
               B10 → B11 → B12
                       ↘
                        F1 → F2
```

Front pode começar em **F1** após **B4** (leitura); **F2** após **B11**/**B12**.
)
