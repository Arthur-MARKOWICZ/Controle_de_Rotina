# Design

## Context

See proposal.md — Why. `Tarefa.projetoId` já agrupa; não há grafo de espera. Planner (`PlannerGeracaoService`) filtra status elegível + pais com filhos + saldo > 0. Ajuste (`PlanoAjusteService.adicionarBlocoTarefa`) e sessão (`TarefaSlotAvulsoService.carregarTarefaElegivel`) recusam pai com filhos via `TarefaNaoElegivelException`. Projeto só tem `ATIVO` / `ARQUIVADO`. Soft delete de tarefa e arquivar projeto já têm hooks (Decidir zera `projetoId`). Flyway em `V20`. Sem cache. Prefixo `/tarefas` e `/projetos` já no proxy.

## Goals / Non-Goals

**Goals:**

- Uma tabela polimórfica de espera + um service consultado por planner, ajuste, sessão e PATCH de status.
- Nested resources nos controllers já existentes (sem prefixo HTTP novo).
- Resolução derivada na leitura (não persistir `resolvida`).
- Enrich em lote nas listagens (padrão tags).

**Non-Goals:**

- Status `CONCLUIDO` em projeto; FK SQL para os dois tipos; polimorfismo JPA (`InheritanceType`); endpoint de busca por título; tools MCP.

## Decisions

### 1. Tabela `espera` polimórfica, sem FK rígida

```text
espera(
  id UUID PK,
  usuario_id UUID NOT NULL,
  origem_tipo VARCHAR NOT NULL,   -- TAREFA | PROJETO
  origem_id UUID NOT NULL,
  alvo_tipo VARCHAR NOT NULL,     -- TAREFA | PROJETO
  alvo_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
)
  UNIQUE (usuario_id, origem_tipo, origem_id, alvo_tipo, alvo_id)
  INDEX (usuario_id, origem_tipo, origem_id)
  INDEX (usuario_id, alvo_tipo, alvo_id)
```

- Migration `V21__create_espera.sql`.
- Entity `Espera` no package `com.controlerotina.pendencia`.
- Donos e existência de origem/alvo validados no service (mesmo espírito de `projetoId` no item Decidir).
- Alternativa rejeitada: duas tabelas (`espera_tarefa`, `espera_projeto`) — quatro combinações viram quatro joins.
- Alternativa rejeitada: coluna `depende_de_tarefa_id` só em `tarefa` — não cobre projeto como origem nem alvo projeto.

### 2. API aninhada nos prefixos existentes

| Método | Path | Comportamento |
|--------|------|----------------|
| POST | `/tarefas/{id}/esperas` | body `{ alvoTipo, alvoId }`; 201 + espera |
| GET | `/tarefas/{id}/esperas` | lista da origem (opcional se o GET da tarefa já enriquecer) |
| DELETE | `/tarefas/{id}/esperas/{esperaId}` | 204; 404 se não for da origem / outro usuário |
| POST | `/projetos/{id}/esperas` | igual, origem = projeto `ATIVO` |
| DELETE | `/projetos/{id}/esperas/{esperaId}` | 204 |

- Controllers atuais ganham os métodos; regra no `EsperaService` (injeção por construtor).
- GET `/tarefas` e GET `/tarefas/{id}` / GET `/projetos/{id}` passam a incluir `esperas: EsperaResumo[]` e `bloqueada: boolean`.
- `EsperaResumo`: `id`, `alvoTipo`, `alvoId`, `alvoTitulo`, `resolvida`.
- Alternativa rejeitada: `/pendencias` — exigiria proxy Vite/Nginx/`API_PROXY.md`.

Ciclo, auto-espera, duplicata, alvo inválido → Problem Detail 400/404/409 no padrão vigente.

### 3. `bloqueada` e `resolvida` são derivados

Não persistir flag. `EsperaService` (ou query dedicada) calcula:

- Alvo tarefa resolvido ⇔ status `FINALIZADA` e `deletedAt == null`.
- Alvo projeto resolvido ⇔ existe tarefa não deletada do projeto **e** nenhuma delas em `PENDENTE` / `EM_PROGRESSO` / `ATRASADA`.
- Origem tarefa bloqueada ⇔ alguma espera irresolvida **ou** pai bloqueado (um nível) **ou** `projetoId` cujo projeto é origem de espera irresolvida.
- Origem projeto bloqueada ⇔ alguma espera irresolvida daquele projeto.

Alternativa rejeitada: status `CONCLUIDO` em projeto neste corte — muda CRUD, listagens e reservas; a issue pede a relação, não o ciclo de vida do projeto.

Alternativa rejeitada: projeto vazio como resolvido — espera de um projeto recém-criado liberaria na hora.

### 4. Grafo pequeno: ciclo na escrita

Ao criar, carregar as esperas do usuário (ou o componente ligado à origem) e recusar se `alvo` já alcança `origem` (BFS). Volume por usuário cabe em memória; sem cache.

Duplicata = unique + 409. Auto-espera = 400 antes do grafo.

### 5. Hooks de elegibilidade

Um método `assertNaoBloqueada(tarefa)` (ou `boolean`) usado em:

- `PlannerGeracaoService` — após o filtro de pais com filhos, `continue` se bloqueada.
- `PlanoAjusteService.adicionarBlocoTarefa` — junto de `garantirElegivel`.
- `TarefaSlotAvulsoService.carregarTarefaElegivel` — `TarefaNaoElegivelException` (ou Problem Detail específico de espera; preferir mensagem clara, mesmo tipo 409/400).
- `TarefaService` no PATCH `FINALIZADA`.

Soft delete de tarefa: apagar esperas com `origem` ou `alvo` = essa tarefa (índice alvo). Arquivar projeto: apagar esperas com origem ou alvo = esse projeto. Restore **não** recria.

Job de atraso **não** ignora tarefa bloqueada (prazo continua valendo).

### 6. Enrich sem N+1

Na página de tarefas / get único: `IN` dos ids da página como origem `TAREFA`; batch de títulos dos alvos (tarefas + projetos). Mesmo padrão de `parentTitulo` / tags. `TarefaResponse` ganha os dois campos no fim do record; overloads `from(...)` existentes passam lista vazia / `bloqueada=false` por default e o service preenche.

### 7. UI

- Drawer de editar (`TarefaDrawer`): seção abaixo das subtarefas; picker reusa `GET /tarefas` e `GET /projetos` (sem endpoint novo de busca).
- Hub (`ProjetoFormPage`): seção equivalente; arquivado só lê.
- `TarefaCard`: badge “Espera {título}” ou “Espera N itens”; `bloqueada` do payload esconde Iniciar/Concluir (além de `temFilhos`).
- Home: se o bloco ainda existir e `bloqueada`, esconder sessão e mostrar a espera (payload do plano precisa do flag — enrich no DTO do bloco/tarefa embutida, batch no `PlanoConsultaService` / caminho que já busca tarefas do plano).

Copy: **Espera** / **Bloqueada por**. Não usar “pendente” na UI desta feature.

## Risks / Trade-offs

- [Plano `APROVADO` já contém a tarefa quando a espera nasce] → Mitigar: sessão e UI recusam; geração seguinte omite; não mutar aprovado em silêncio (alinhado a reserva/replanejamento).
- [Projeto “pronto” é regra derivada, fácil de achar opaca] → Mitigar: `resolvida` + empty copy no hub (“o alvo ainda tem tarefas em aberto” / “projeto sem tarefas ainda bloqueia”).
- [Grafo + herança pai/projeto na geração] → Mitigar: um service; IT de geração com os três casos (própria, pai, projeto).
- [TarefaResponse com mais um overload] → Mitigar: defaults vazios nos `from` atuais; só o mapper da listagem preenche.

## Migration Plan

1. Deploy `V21` + API + hooks (campos novos; clientes antigos ignoram JSON extra).
2. Deploy front (drawer, hub, cards). Sem rebuild de proxy.
3. Rollback: reverter front; API pode permanecer; down manual da `espera` se necessário.

## Open Questions

- Nenhum que altere specs; teto de esperas por origem e visual exato do picker (combobox vs lista curta) ficam na implementação.
