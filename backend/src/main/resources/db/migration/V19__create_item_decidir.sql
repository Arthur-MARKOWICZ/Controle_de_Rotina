CREATE TABLE item_decidir (
    id           UUID PRIMARY KEY,
    usuario_id   UUID NOT NULL,
    titulo       VARCHAR(120) NOT NULL,
    descricao    VARCHAR(2000),
    decidir_ate  TIMESTAMPTZ,
    projeto_id   UUID,
    tarefa_id    UUID,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_item_decidir_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

CREATE INDEX idx_item_decidir_usuario_tarefa ON item_decidir (usuario_id, tarefa_id);
CREATE INDEX idx_item_decidir_usuario_decidir_ate ON item_decidir (usuario_id, decidir_ate);

CREATE TABLE decidir_tag (
    item_decidir_id UUID NOT NULL,
    tag_id          UUID NOT NULL,
    CONSTRAINT pk_decidir_tag PRIMARY KEY (item_decidir_id, tag_id),
    CONSTRAINT fk_decidir_tag_item FOREIGN KEY (item_decidir_id) REFERENCES item_decidir (id) ON DELETE CASCADE,
    CONSTRAINT fk_decidir_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
);

CREATE INDEX idx_decidir_tag_tag ON decidir_tag (tag_id);
