CREATE TABLE projeto (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL,
    nome        VARCHAR(80) NOT NULL,
    descricao   VARCHAR(500),
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_projeto_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_projeto_status CHECK (status IN ('ATIVO', 'ARQUIVADO'))
);

CREATE INDEX idx_projeto_usuario_status ON projeto (usuario_id, status);

CREATE TABLE reserva_projeto_semanal (
    id                   UUID PRIMARY KEY,
    usuario_id           UUID NOT NULL,
    projeto_id           UUID NOT NULL,
    dia_semana           VARCHAR(20) NOT NULL,
    minutos_reservados   INTEGER NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_reserva_projeto_semanal_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_reserva_projeto_semanal_projeto FOREIGN KEY (projeto_id) REFERENCES projeto (id),
    CONSTRAINT uq_reserva_projeto_semanal_usuario_projeto_dia UNIQUE (usuario_id, projeto_id, dia_semana),
    CONSTRAINT ck_reserva_projeto_semanal_dia CHECK (dia_semana IN (
        'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO', 'DOMINGO'
    )),
    CONSTRAINT ck_reserva_projeto_semanal_minutos CHECK (
        minutos_reservados >= 15 AND minutos_reservados % 15 = 0
    )
);

CREATE INDEX idx_reserva_projeto_semanal_usuario_dia
    ON reserva_projeto_semanal (usuario_id, dia_semana);

ALTER TABLE tarefa
    ADD COLUMN projeto_id UUID,
    ADD CONSTRAINT fk_tarefa_projeto FOREIGN KEY (projeto_id) REFERENCES projeto (id);

CREATE INDEX idx_tarefa_projeto ON tarefa (projeto_id);
