ALTER TABLE plano
    ADD COLUMN itens_nao_alocados JSONB NOT NULL DEFAULT '[]'::jsonb;
