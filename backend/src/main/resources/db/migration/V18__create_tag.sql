CREATE TABLE tag (
    id               UUID PRIMARY KEY,
    usuario_id       UUID NOT NULL,
    nome             VARCHAR(64) NOT NULL,
    nome_normalizado VARCHAR(64) NOT NULL,
    cor              VARCHAR(7) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_tag_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uq_tag_usuario_nome_normalizado UNIQUE (usuario_id, nome_normalizado),
    CONSTRAINT ck_tag_cor CHECK (cor ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX idx_tag_usuario_nome ON tag (usuario_id, nome);

CREATE TABLE tarefa_tag (
    tarefa_id UUID NOT NULL,
    tag_id    UUID NOT NULL,
    CONSTRAINT pk_tarefa_tag PRIMARY KEY (tarefa_id, tag_id),
    CONSTRAINT fk_tarefa_tag_tarefa FOREIGN KEY (tarefa_id) REFERENCES tarefa (id) ON DELETE CASCADE,
    CONSTRAINT fk_tarefa_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE INDEX idx_tarefa_tag_tag ON tarefa_tag (tag_id);

CREATE TABLE habito_tag (
    habito_id UUID NOT NULL,
    tag_id    UUID NOT NULL,
    CONSTRAINT pk_habito_tag PRIMARY KEY (habito_id, tag_id),
    CONSTRAINT fk_habito_tag_habito FOREIGN KEY (habito_id) REFERENCES habito (id) ON DELETE CASCADE,
    CONSTRAINT fk_habito_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE INDEX idx_habito_tag_tag ON habito_tag (tag_id);

CREATE TABLE projeto_tag (
    projeto_id UUID NOT NULL,
    tag_id    UUID NOT NULL,
    CONSTRAINT pk_projeto_tag PRIMARY KEY (projeto_id, tag_id),
    CONSTRAINT fk_projeto_tag_projeto FOREIGN KEY (projeto_id) REFERENCES projeto (id) ON DELETE CASCADE,
    CONSTRAINT fk_projeto_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE INDEX idx_projeto_tag_tag ON projeto_tag (tag_id);
