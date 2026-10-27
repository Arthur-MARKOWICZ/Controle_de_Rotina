CREATE TABLE execucao (
    id               UUID PRIMARY KEY,
    usuario_id       UUID NOT NULL,
    bloco_plano_id   UUID NOT NULL,
    status           VARCHAR(20) NOT NULL,
    iniciado_em      TIMESTAMPTZ,
    concluido_em     TIMESTAMPTZ,
    abortado_em      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_execucao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_execucao_bloco_plano FOREIGN KEY (bloco_plano_id) REFERENCES bloco_plano (id),
    CONSTRAINT uk_execucao_bloco_plano UNIQUE (bloco_plano_id),
    CONSTRAINT ck_execucao_status CHECK (status IN (
        'NAO_INICIADO',
        'EM_ANDAMENTO',
        'CONCLUIDO',
        'ABORTADO'
    ))
);

CREATE INDEX idx_execucao_usuario ON execucao (usuario_id);
CREATE INDEX idx_execucao_usuario_status ON execucao (usuario_id, status);
