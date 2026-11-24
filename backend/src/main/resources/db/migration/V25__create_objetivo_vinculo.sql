CREATE TABLE objetivo_tarefa (
    objetivo_id UUID NOT NULL,
    tarefa_id   UUID NOT NULL,
    CONSTRAINT pk_objetivo_tarefa PRIMARY KEY (objetivo_id, tarefa_id),
    CONSTRAINT fk_objetivo_tarefa_objetivo FOREIGN KEY (objetivo_id) REFERENCES objetivo (id) ON DELETE CASCADE,
    CONSTRAINT fk_objetivo_tarefa_tarefa FOREIGN KEY (tarefa_id) REFERENCES tarefa (id) ON DELETE CASCADE
);

CREATE INDEX idx_objetivo_tarefa_tarefa ON objetivo_tarefa (tarefa_id);

CREATE TABLE objetivo_habito (
    objetivo_id UUID NOT NULL,
    habito_id   UUID NOT NULL,
    CONSTRAINT pk_objetivo_habito PRIMARY KEY (objetivo_id, habito_id),
    CONSTRAINT fk_objetivo_habito_objetivo FOREIGN KEY (objetivo_id) REFERENCES objetivo (id) ON DELETE CASCADE,
    CONSTRAINT fk_objetivo_habito_habito FOREIGN KEY (habito_id) REFERENCES habito (id) ON DELETE CASCADE
);

CREATE INDEX idx_objetivo_habito_habito ON objetivo_habito (habito_id);

CREATE TABLE objetivo_projeto (
    objetivo_id UUID NOT NULL,
    projeto_id  UUID NOT NULL,
    CONSTRAINT pk_objetivo_projeto PRIMARY KEY (objetivo_id, projeto_id),
    CONSTRAINT fk_objetivo_projeto_objetivo FOREIGN KEY (objetivo_id) REFERENCES objetivo (id) ON DELETE CASCADE,
    CONSTRAINT fk_objetivo_projeto_projeto FOREIGN KEY (projeto_id) REFERENCES projeto (id) ON DELETE CASCADE
);

CREATE INDEX idx_objetivo_projeto_projeto ON objetivo_projeto (projeto_id);
