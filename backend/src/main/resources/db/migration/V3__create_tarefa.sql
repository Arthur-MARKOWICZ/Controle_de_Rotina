CREATE TABLE tarefa (
    id              UUID PRIMARY KEY,
    usuario_id      UUID NOT NULL,
    titulo          VARCHAR(120) NOT NULL,
    descricao       VARCHAR(2000) NOT NULL,
    prioridade      VARCHAR(20) NOT NULL,
    prazo           TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL,
    duracao_horas   INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT fk_tarefa_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_tarefa_duracao_horas CHECK (duracao_horas >= 1),
    CONSTRAINT ck_tarefa_prioridade CHECK (prioridade IN ('ALTA', 'MEDIA', 'BAIXA')),
    CONSTRAINT ck_tarefa_status CHECK (status IN ('PENDENTE', 'EM_PROGRESSO', 'FINALIZADA', 'ATRASADA', 'CANCELADA'))
);

CREATE INDEX idx_tarefa_usuario ON tarefa (usuario_id);
CREATE INDEX idx_tarefa_usuario_prazo ON tarefa (usuario_id, prazo);
CREATE INDEX idx_tarefa_usuario_status ON tarefa (usuario_id, status);
