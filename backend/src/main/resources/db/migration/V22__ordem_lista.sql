ALTER TABLE tarefa
    ADD COLUMN ordem INTEGER NOT NULL DEFAULT 0;

ALTER TABLE projeto
    ADD COLUMN ordem INTEGER NOT NULL DEFAULT 0;

ALTER TABLE habito
    ADD COLUMN ordem INTEGER NOT NULL DEFAULT 0;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY usuario_id, parent_id
               ORDER BY prazo ASC NULLS LAST, created_at ASC
           ) - 1 AS nova_ordem
      FROM tarefa
)
UPDATE tarefa t
   SET ordem = ranked.nova_ordem
  FROM ranked
 WHERE t.id = ranked.id;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY usuario_id
               ORDER BY nome ASC, created_at ASC
           ) - 1 AS nova_ordem
      FROM projeto
)
UPDATE projeto p
   SET ordem = ranked.nova_ordem
  FROM ranked
 WHERE p.id = ranked.id;

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY usuario_id
               ORDER BY titulo ASC, created_at ASC
           ) - 1 AS nova_ordem
      FROM habito
)
UPDATE habito h
   SET ordem = ranked.nova_ordem
  FROM ranked
 WHERE h.id = ranked.id;

CREATE INDEX idx_tarefa_usuario_parent_ordem ON tarefa (usuario_id, parent_id, ordem);
CREATE INDEX idx_projeto_usuario_ordem ON projeto (usuario_id, ordem);
CREATE INDEX idx_habito_usuario_ordem ON habito (usuario_id, ordem);
