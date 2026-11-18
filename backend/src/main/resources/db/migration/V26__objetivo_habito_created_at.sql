-- Instante em que o par objetivo–hábito foi criado. Linhas pré-existentes recebem NOW().
ALTER TABLE objetivo_habito
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
