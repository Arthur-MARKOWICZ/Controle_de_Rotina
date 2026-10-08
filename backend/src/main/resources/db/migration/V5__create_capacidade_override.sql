CREATE TABLE capacidade_override (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL,
    data        DATE NOT NULL,
    horas       INTEGER NOT NULL,
    ativo       BOOLEAN NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_capacidade_override_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_capacidade_override_horas CHECK (horas >= 0)
);

CREATE UNIQUE INDEX uq_capacidade_override_usuario_data_ativo
    ON capacidade_override (usuario_id, data)
    WHERE ativo = true;

CREATE INDEX idx_capacidade_override_usuario
    ON capacidade_override (usuario_id);

CREATE INDEX idx_capacidade_override_usuario_data
    ON capacidade_override (usuario_id, data);
