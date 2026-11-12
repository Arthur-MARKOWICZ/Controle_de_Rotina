ALTER TABLE tarefa
    ADD COLUMN parent_id UUID,
    ADD CONSTRAINT fk_tarefa_parent FOREIGN KEY (parent_id) REFERENCES tarefa (id);

CREATE INDEX idx_tarefa_usuario_parent ON tarefa (usuario_id, parent_id);
