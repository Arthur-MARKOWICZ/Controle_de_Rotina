ALTER TABLE plano
    DROP CONSTRAINT ck_plano_status;

ALTER TABLE plano
    ADD CONSTRAINT ck_plano_status CHECK (status IN ('RASCUNHO', 'APROVADO', 'DESCARTADO'));
