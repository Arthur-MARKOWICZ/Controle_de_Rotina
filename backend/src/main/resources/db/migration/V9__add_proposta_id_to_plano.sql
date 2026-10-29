ALTER TABLE plano
    ADD COLUMN proposta_id UUID;

CREATE INDEX idx_plano_usuario_proposta ON plano (usuario_id, proposta_id);
