CREATE TABLE habito (
    id                      UUID PRIMARY KEY,
    usuario_id              UUID NOT NULL,
    titulo                  VARCHAR(120) NOT NULL,
    descricao               VARCHAR(2000),
    duracao_minutos         INTEGER NOT NULL,
    horario_preferencial    TIME,
    status                  VARCHAR(20) NOT NULL,
    streak_atual            INTEGER NOT NULL DEFAULT 0,
    melhor_streak           INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT fk_habito_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_habito_status CHECK (status IN ('ATIVO', 'PAUSADO')),
    CONSTRAINT ck_habito_duracao CHECK (duracao_minutos >= 15 AND duracao_minutos % 15 = 0),
    CONSTRAINT ck_habito_streak_atual CHECK (streak_atual >= 0),
    CONSTRAINT ck_habito_melhor_streak CHECK (melhor_streak >= 0)
);

CREATE INDEX idx_habito_usuario_status ON habito (usuario_id, status);
CREATE INDEX idx_habito_usuario_deleted ON habito (usuario_id, deleted_at);

CREATE TABLE habito_dia_semana (
    habito_id   UUID NOT NULL,
    dia_semana  VARCHAR(20) NOT NULL,
    CONSTRAINT fk_habito_dia_semana_habito FOREIGN KEY (habito_id) REFERENCES habito (id) ON DELETE CASCADE,
    CONSTRAINT pk_habito_dia_semana PRIMARY KEY (habito_id, dia_semana),
    CONSTRAINT ck_habito_dia_semana_dia CHECK (dia_semana IN (
        'SEGUNDA', 'TERCA', 'QUARTA', 'QUINTA', 'SEXTA', 'SABADO', 'DOMINGO'
    ))
);

ALTER TABLE bloco_plano
    ALTER COLUMN tarefa_id DROP NOT NULL;

ALTER TABLE bloco_plano
    ADD COLUMN habito_id UUID;

ALTER TABLE bloco_plano
    ADD CONSTRAINT fk_bloco_plano_habito FOREIGN KEY (habito_id) REFERENCES habito (id);

ALTER TABLE bloco_plano
    ADD CONSTRAINT ck_bloco_plano_origem CHECK (
        (tarefa_id IS NOT NULL AND habito_id IS NULL)
        OR (tarefa_id IS NULL AND habito_id IS NOT NULL)
    );

-- Um hábito no máximo uma vez por plano (tarefas já podem repetir bloco desde V10).
CREATE UNIQUE INDEX uk_bloco_plano_plano_habito
    ON bloco_plano (plano_id, habito_id)
    WHERE habito_id IS NOT NULL;
