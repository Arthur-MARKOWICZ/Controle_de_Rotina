CREATE TABLE plano (
    id              UUID PRIMARY KEY,
    usuario_id      UUID NOT NULL,
    data            DATE NOT NULL,
    versao          INTEGER NOT NULL,
    status          VARCHAR(20) NOT NULL,
    tem_conflito    BOOLEAN NOT NULL,
    mensagem        VARCHAR(500),
    gerado_em       TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_plano_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uk_plano_usuario_data_versao UNIQUE (usuario_id, data, versao),
    CONSTRAINT ck_plano_versao CHECK (versao >= 1),
    CONSTRAINT ck_plano_status CHECK (status IN ('RASCUNHO', 'APROVADO'))
);

CREATE INDEX idx_plano_usuario_data ON plano (usuario_id, data);
CREATE INDEX idx_plano_usuario_data_status ON plano (usuario_id, data, status);

CREATE TABLE bloco_plano (
    id               UUID PRIMARY KEY,
    plano_id         UUID NOT NULL,
    tarefa_id        UUID NOT NULL,
    duracao_minutos  INTEGER NOT NULL,
    ordem            INTEGER NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_bloco_plano_plano FOREIGN KEY (plano_id) REFERENCES plano (id),
    CONSTRAINT fk_bloco_plano_tarefa FOREIGN KEY (tarefa_id) REFERENCES tarefa (id),
    CONSTRAINT uk_bloco_plano_plano_tarefa UNIQUE (plano_id, tarefa_id),
    CONSTRAINT ck_bloco_plano_duracao_minutos CHECK (duracao_minutos >= 1),
    CONSTRAINT ck_bloco_plano_ordem CHECK (ordem >= 1)
);

CREATE INDEX idx_bloco_plano_plano ON bloco_plano (plano_id);
CREATE INDEX idx_bloco_plano_plano_ordem ON bloco_plano (plano_id, ordem);
