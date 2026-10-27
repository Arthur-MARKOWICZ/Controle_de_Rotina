ALTER TABLE usuario
    ADD COLUMN xp_total INT NOT NULL DEFAULT 0,
    ADD COLUMN nivel INT NOT NULL DEFAULT 1;

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_xp_total CHECK (xp_total >= 0),
    ADD CONSTRAINT ck_usuario_nivel CHECK (nivel >= 1);

CREATE TABLE xp_evento (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL,
    tipo        VARCHAR(30) NOT NULL,
    origem_id   UUID NOT NULL,
    quantidade  INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_xp_evento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uk_xp_evento_tipo_origem UNIQUE (tipo, origem_id),
    CONSTRAINT ck_xp_evento_tipo CHECK (tipo IN ('BLOCO_CONCLUIDO', 'TAREFA_FINALIZADA')),
    CONSTRAINT ck_xp_evento_quantidade CHECK (quantidade > 0)
);

CREATE INDEX idx_xp_evento_usuario_created ON xp_evento (usuario_id, created_at);

CREATE INDEX idx_execucao_usuario_status_concluido
    ON execucao (usuario_id, status, concluido_em);
