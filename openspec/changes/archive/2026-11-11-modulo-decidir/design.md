# Design

## Context

See proposal.md — Why. Domínio atual: `tarefa`, `habito`, `projeto`, `tag` (com `TagVinculoService` + tabelas `*_tag`). Soft delete de tarefa em `TarefaService`; projeto usa `ARQUIVADO` via `arquivar`. Create de tarefa já aceita título obrigatório e duração/prazo/prioridade/projeto/tags opcionais (`CriarTarefaRequest`). Front: nav em `AppShell` (primário/secundário/terciário + bottom); prefixo novo de API exige proxy Vite/Nginx (`API_PROXY.md`); SPA+API no mesmo path usa bypass por `Accept` (como `/tags`). Flyway em `V18`. Sem cache.

## Goals / Non-Goals

**Goals:**

- Package `decidir` no padrão controller → service → repository, injeção por construtor.
- Ciclo aberto/promovido modelado por `tarefaId` nullable (sem enum `DESCARTADO`).
- Extender `TagVinculoService` com `decidir_tag`; hard delete de tag limpa a nova tabela.
- Hook no soft delete de tarefa para reabrir item; no arquivar projeto para zerar `projetoId`.
- UI `/decidir` na nav principal + proxy `/decidir`.

**Non-Goals:**

- Planner/execução/XP no item; MCP; snooze; soft delete do item; polimorfismo JPA genérico de tags; issue #59.

## Decisions

### 1. Schema: `item_decidir` + `decidir_tag`

```text
item_decidir(
  id, usuario_id, titulo, descricao,
  decidir_ate,          -- Instant nullable
  projeto_id,           -- UUID nullable, sem FK rígida obrigatória se projeto for soft/arquivo
  tarefa_id,            -- UUID nullable; preenchido após promover
  created_at, updated_at
)
  INDEX (usuario_id, tarefa_id)     -- lista abertos: tarefa_id IS NULL
  INDEX (usuario_id, decidir_ate)

decidir_tag(item_decidir_id, tag_id) PK + FKs
```

- Entity sugerida: `ItemDecidir` no package `com.controlerotina.decidir`.
- Alternativa rejeitada: status enum `ABERTO|PROMOVIDO|DESCARTADO` — descarte é hard delete; promovido = `tarefaId` set.
- Alternativa rejeitada: apagar o item na promoção — perde histórico pedido.
- Migration `V19__create_item_decidir.sql` (ou próximo livre após `V18`).

### 2. API em `/decidir`

| Método | Path | Comportamento |
|--------|------|----------------|
| GET | `/decidir` | lista paginada; default `tarefaId IS NULL`; query `promovidos=true` (ou `status=promovido`) para só linkados; filtro `tagId` repetível + score E→OU; sort por `decidirAte` / `createdAt` |
| POST | `/decidir` | cria (titulo*; descricao?, decidirAte?, projetoId?, tagIds?) |
| GET | `/decidir/{id}` | detalhe (aberto ou promovido) do usuário |
| PATCH | `/decidir/{id}` | só se `tarefaId == null`; presença de `tagIds` substitui conjunto |
| DELETE | `/decidir/{id}` | hard delete (descartar); 404 se outro usuário / inexistente |
| POST | `/decidir/{id}/promover` | cria tarefa + copia tags + herda projeto + seta `tarefaId`; rejeita se já promovido |

Resposta inclui `tags: TagResumo[]`, `tarefaId`, `projetoId`, campos do item. Escopo sempre JWT; id alheio → 404.

Proxy: `/decidir` como SPA+API compartilhado (Accept JSON → API), documentar em `API_PROXY.md`.

### 3. Promover reutiliza create de tarefa

- Service Decidir chama o mesmo caminho de criação de tarefa (método de service / factory interno — **não** HTTP interno).
- Body de promover: campos opcionais alinhados a `CriarTarefaRequest` (duracaoMinutos, prazo, prioridade); título default = título do item (override opcional se útil; default sem override).
- Descrição: se body omitir, usa a do item; tags: copia vínculos do item para a tarefa via `TagVinculoService`; `projetoId`: herda do item se body não enviar outro.
- Transação única: criar tarefa + setar `tarefaId` + (já tags na tarefa).

Alternativa rejeitada: cliente cria tarefa e depois PATCH no item — race e inconsistência.

### 4. Hooks de consistência

- **Soft delete tarefa:** em `TarefaService` (mesmo fluxo que seta `deletedAt`), buscar item(s) com esse `tarefaId` do usuário e zerar `tarefaId` + `updatedAt`. Restore de tarefa **não** religa (sem histórico de link anterior neste corte).
- **Arquivar projeto:** em `ProjetoService.arquivar`, zerar `projetoId` nos itens Decidir daquele projeto (abertos e promovidos). Reativar projeto **não** restaura `projetoId` automaticamente.
- Alternativa rejeitada: job assíncrono — overkill; o evento já está no service.

### 5. Tags

- Novos métodos em `TagVinculoService`: substituir/listar/batch para Decidir; hard delete da tag apaga `decidir_tag`.
- Enrich na listagem Decidir em batch (evitar N+1), mesmo padrão das outras listagens.
- Filtro + `matchScore` na Specification/SQL de Decidir.

### 6. UI

- Rota `/decidir` em `App.tsx`; item **Decidir** em `NAV_SIDEBAR_PRIMARIO` (após Tarefas) e em `NAV_BOTTOM` (após Tarefas). Ícone Phosphor coerente (ex. `Scales` / `Question` — escolher um só).
- Página: inline add (título) + lista de abertos; ações Promover / Descartar (confirm); drawer ou dialog de promover com campos opcionais de tarefa; picker de tags reutilizado; seletor de projeto opcional.
- Toggle/aba “Já decididos” para `promovidos=true` (somente leitura + link para a tarefa se a rota existir).
- `decidirAte` vencido: classe visual de aviso (sem badge de status de domínio).
- Filtro de tags na URL (`tagId`), espelhando listas existentes.
- Atualizar `documentTitle`, testes de `AppShell` (ordem da nav), proxy.

Bottom com 5 destinos + Mais: aceitável no v1; se apertar demais no mobile, mover Capacidade já está em Mais — Decidir permanece primário conforme proposal.

## Risks / Trade-offs

- [Bottom nav mais cheia] → Mitigar: Decidir logo após Tarefas; labels curtos; revisar em slice de shell se houver reclamação.
- [Soft delete tarefa sem hook] → Mitigar: teste de integração obrigatório no soft delete; falha = item órfão.
- [Promover + create tarefa divergem] → Mitigar: um único método de criação no service de tarefa.
- [Colisão SPA `/decidir`] → Mitigar: proxy Accept-based; checklist `API_PROXY.md`.
- [Restore tarefa não religa] → Aceito no v1; documentado nas specs.

## Migration Plan

1. Deploy migration `V19` + API Decidir + hooks tarefa/projeto/tag (campos novos; clientes antigos ignoram).
2. Deploy front com proxy `/decidir` (rebuild imagem frontend).
3. Rollback: reverter front; API pode permanecer; down manual da migration se necessário (dados só de Decidir).

## Open Questions

- Nenhum que altere specs; teto de título/descrição espelha tarefa (120 / 2000) como default de implementação.
