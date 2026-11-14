-- Minutos informados pelo usuário no concluir-direto de hábito (issue #84).
-- NULL quando o tempo vem do timer (iniciado_em → concluido_em) ou em execuções antigas.
ALTER TABLE execucao
    ADD COLUMN minutos_informados INTEGER;

ALTER TABLE execucao
    ADD CONSTRAINT ck_execucao_minutos_informados CHECK (
        minutos_informados IS NULL OR minutos_informados BETWEEN 1 AND 1440
    );
