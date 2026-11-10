# Design

## Context

See proposal.md — Why. Hoje não há entidade de tag: `Tarefa` tem `projetoId` opcional; `Habito` e `Projeto` não têm classificação transversal. Listagens usam Specs/filtros (`TarefaFiltro`, params em controllers) e o front de tarefas agrupa por prazo em `listaGrupos.ts` + filtros na URL (`listaUrl.ts`). Prefixo novo de API exige proxy Vite/Nginx (`API_PROXY.md`). Flyway está em `V17`; padrão de módulo é package por domínio (`tarefa`, `habito`, `projeto`) com controller → service → repository, injeção por construtor, Java 17 / Spring Boot 3. Sem cache.

## Goals / Non-Goals

**Goals:**

- Módulo `tag` coeso (catálogo + serviço de vínculo) reutilizado por tarefa/hábito/projeto.
- Filtro e score de match na API (paginação coerente); UI de tarefas só reordena dentro do grupo se a página já vier ranqueada ou se o score vier no DTO.
- Proxy `/tags` e rota SPA `/tags` sem colisão (Accept JSON → API, como `/projetos`).

**Non-Goals:**

- Polimorfismo JPA genérico (`entity_type`); hierarquia; soft delete de tag; herança projeto→tarefa; MCP/calendário/planner neste corte.

## Decisions

### 1. Schema: `tag` + três tabelas de vínculo

```text
tag(id, usuario_id, nome, cor, created_at, updated_at)
  UNIQUE (usuario_id, lower(nome))  -- via índice unique em nome_normalizado ou citext/lower

tarefa_tag(tarefa_id, tag_id) PK composta + FKs
habito_tag(habito_id, tag_id)
projeto_tag(projeto_id, tag_id)
```

- Alternativa rejeitada: coluna `tags text[]` / JSON — quebra catálogo, cor e hard delete limpo.
- Alternativa rejeitada: uma tabela `tag_vinculo(tipo, entidade_id)` — foge do padrão atual e complica FK/integridade.

Migration `V18__create_tag.sql` (ou próximo número livre).

### 2. API do catálogo em `/tags`

| Método | Path | Comportamento |
|--------|------|----------------|
| GET | `/tags` | lista do usuário (ordenada por nome) |
| POST | `/tags` | cria (nome + cor; cor default `#6B7280` se omitida no create inline) |
| PATCH | `/tags/{id}` | nome e/ou cor |
| DELETE | `/tags/{id}` | hard delete + cascade vínculos |

- Escopo sempre pelo JWT; id de outro usuário → 404.
- Conflito de nome → 409 Problem Detail (padrão do projeto).
- Proxy: adicionar `/tags` como SPA+API compartilhado (mesmo padrão de `/projetos`).

### 3. Vínculo via campos nos requests existentes

- `Criar*` / `Atualizar*` de tarefa, hábito e projeto aceitam `tagIds: UUID[]` (opcional).
- Update: presença de `tagIds` substitui o conjunto (lista vazia remove todas); ausência do campo mantém vínculos (espelha o padrão `projetoIdPresente` em tarefa).
- Alternativa rejeitada: só endpoints `/tarefas/{id}/tags` — mais rotas e pior DX no drawer.

Respostas (`TarefaResponse`, etc.) ganham `tags: TagResumo[]` (`id`, `nome`, `cor`). Enrich em batch na listagem (evitar N+1: carregar vínculos por ids da página).

### 4. Filtro e score na listagem

- Query param `tagId` repetível (ou `tagIds`) nos GET de `/tarefas`, `/habitos`, `/projetos`.
- Predicado: item tem **pelo menos um** dos ids (EXISTS / join).
- Ordenação: chave primária de sort = `matchScore` desc onde score = 2 se contém todos os ids filtrados, 1 se parcial; depois o sort já permitido do recurso (`prazo`, etc.).
- Com 0 ou 1 tag no filtro, `matchScore` é uniforme (não altera ordem relativa além do filtro).
- Paginação: ordenação no SQL/Specification para páginas estáveis.

Alternativa rejeitada: filtrar só no front — quebra paginação e MCP/API consumers.

### 5. UI: página + picker + grupos

- Rota `/tags` + item em `MenuMais` (e sidebar “Mais” / terciário se aplicável).
- Componente picker reutilizável (busca, selecionar, “criar ‘nome’”, link Gerenciar).
- `listaUrl` / filtros de hábito e projeto: serializar `tagId`s.
- `listaGrupos`: ao montar cada grupo, estável-sort por `matchScore` se o response expuser o score **ou** recalcular no client a partir das tags do item + filtro (preferir score no response para uma fonte de verdade; se omitido, client calcula com o mesmo critério E=2 / OU=1).

Cor default no create inline: `#6B7280` (neutro do design system v2; ajustável na gestão).

### 6. Hard delete e confirmação

- Service da tag: em uma transação, delete vínculos das três tabelas + delete da tag.
- UI: `useConfirmDialog` antes do DELETE (padrão já usado em soft deletes).

## Risks / Trade-offs

- [Hard delete diverge do soft delete do app] → Mitigar: confirmação explícita; copy deixa claro que vínculos somem.
- [N+1 em listagens] → Mitigar: batch load de vínculos por página; índices em `*_tag(tag_id)` e `*_tag(entidade_id)`.
- [Colisão SPA `/tags`] → Mitigar: proxy Accept-based como projetos/hábitos; documentar em `API_PROXY.md`.
- [Ordenação E→OU + sort por prazo] → Score primeiro; prazo como desempate — documentar no contrato para não surpreender quem espera só prazo.
- [Update com `tagIds` omitido vs `[]`] → Seguir padrão de presença de campo; testes de contrato cobrem os dois.

## Migration Plan

1. Deploy migration + API (compatível: campos novos opcionais; clientes antigos ignoram `tags`).
2. Deploy front com proxy `/tags` (rebuild imagem frontend).
3. Rollback: reverter front; API pode permanecer (campos ignorados) ou migration down manual se necessário (sem dados críticos além das tags).

## Open Questions

- Nenhum que altere specs/abordagem; teto de tags por item (ex. 20) e paleta fechada vs color picker livre ficam como detalhe de implementação (default: sem teto rígido baixo; cor livre `#RRGGBB` validada).
