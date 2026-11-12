CREATE TABLE espera (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL,
    origem_tipo VARCHAR(20) NOT NULL,
    origem_id   UUID NOT NULL,
    alvo_tipo   VARCHAR(20) NOT NULL,
    alvo_id     UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_espera_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uq_espera_usuario_origem_alvo UNIQUE (usuario_id, origem_tipo, origem_id, alvo_tipo, alvo_id),
    CONSTRAINT ck_espera_origem_tipo CHECK (origem_tipo IN ('TAREFA', 'PROJETO')),
    CONSTRAINT ck_espera_alvo_tipo CHECK (alvo_tipo IN ('TAREFA', 'PROJETO'))
);

CREATE INDEX idx_espera_usuario_origem ON espera (usuario_id, origem_tipo, origem_id);
CREATE INDEX idx_espera_usuario_alvo ON espera (usuario_id, alvo_tipo, alvo_id);
