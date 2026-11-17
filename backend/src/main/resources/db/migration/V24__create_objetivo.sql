CREATE TABLE objetivo (
    id            UUID PRIMARY KEY,
    usuario_id    UUID NOT NULL,
    titulo        VARCHAR(80) NOT NULL,
    descricao     VARCHAR(500),
    prazo         DATE,
    status        VARCHAR(20) NOT NULL,
    concluido_em  TIMESTAMPTZ,
    arquivado_em  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_objetivo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_objetivo_status CHECK (status IN ('ATIVO', 'CONCLUIDO', 'ARQUIVADO')),
    CONSTRAINT ck_objetivo_datas CHECK (
        (status = 'ATIVO' AND concluido_em IS NULL AND arquivado_em IS NULL)
        OR (status = 'CONCLUIDO' AND concluido_em IS NOT NULL AND arquivado_em IS NULL)
        OR (status = 'ARQUIVADO' AND arquivado_em IS NOT NULL)
    )
);

CREATE INDEX idx_objetivo_usuario_status ON objetivo (usuario_id, status);
