CREATE TABLE capacidade_semanal (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL,
    dia_semana  VARCHAR(20) NOT NULL,
    horas       INTEGER NOT NULL,
    ativo       BOOLEAN NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_capacidade_semanal_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_capacidade_semanal_horas CHECK (horas >= 0),
    CONSTRAINT ck_capacidade_semanal_dia CHECK (dia_semana IN (
        'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO', 'DOMINGO'
    ))
);

CREATE UNIQUE INDEX uq_capacidade_semanal_usuario_dia_ativo
    ON capacidade_semanal (usuario_id, dia_semana)
    WHERE ativo = true;

CREATE INDEX idx_capacidade_semanal_usuario
    ON capacidade_semanal (usuario_id);
