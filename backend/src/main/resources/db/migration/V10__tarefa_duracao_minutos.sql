ALTER TABLE tarefa ADD COLUMN duracao_minutos INTEGER;

UPDATE tarefa SET duracao_minutos = duracao_horas * 60;

ALTER TABLE tarefa
    ALTER COLUMN duracao_minutos SET NOT NULL;

ALTER TABLE tarefa DROP CONSTRAINT ck_tarefa_duracao_horas;

ALTER TABLE tarefa DROP COLUMN duracao_horas;

ALTER TABLE tarefa
    ADD CONSTRAINT ck_tarefa_duracao_minutos CHECK (
        duracao_minutos >= 15 AND duracao_minutos % 15 = 0
    );

-- Permite vários blocos da mesma tarefa no mesmo plano (quebra ≤ 90 min).
ALTER TABLE bloco_plano DROP CONSTRAINT uk_bloco_plano_plano_tarefa;
