-- Data de encerramento do objetivo: a conclusão quando existe, senão o arquivamento.
-- Gerada pelo banco para ordenar a visão "Encerrados" sem lógica a manter nos serviços.
ALTER TABLE objetivo
    ADD COLUMN encerrado_em TIMESTAMPTZ
    GENERATED ALWAYS AS (COALESCE(concluido_em, arquivado_em)) STORED;

CREATE INDEX idx_objetivo_usuario_encerrado ON objetivo (usuario_id, encerrado_em DESC);
