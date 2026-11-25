-- Exclusão de conta (LGPD, issue #75): apagar a linha de `usuario` apaga tudo do usuário.
--
-- Todas as FKs para `usuario (id)` passam a ON DELETE CASCADE, e `bloco_plano -> plano`
-- também (bloco_plano é a única tabela do grafo sem `usuario_id`).
--
-- As FKs internas (bloco_plano -> tarefa/habito, execucao -> bloco_plano, tarefa.parent_id,
-- tarefa.projeto_id, reserva_projeto_semanal -> projeto) continuam NO ACTION, mas passam a
-- DEFERRABLE INITIALLY IMMEDIATE: o Postgres checa NO ACTION dentro de cada passo da cascata,
-- então a exclusão de conta roda `SET CONSTRAINTS ALL DEFERRED` e a checagem vai para o commit,
-- quando as duas pontas já sumiram. Fora dessa transação nada muda: um DELETE isolado de
-- tarefa/hábito/projeto/bloco continua barrado na hora (ex.: PropostaService apagando bloco
-- com execução). CASCADE nessas FKs apagaria execuções em silêncio, por isso não foi usado.
--
-- Convenção: toda tabela nova com `usuario_id` declara ON DELETE CASCADE
-- (o ExclusaoContaIT falha se encontrar FK para `usuario` sem cascata).

ALTER TABLE refresh_token
    DROP CONSTRAINT fk_refresh_token_usuario,
    ADD CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE tarefa
    DROP CONSTRAINT fk_tarefa_usuario,
    ADD CONSTRAINT fk_tarefa_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE capacidade_semanal
    DROP CONSTRAINT fk_capacidade_semanal_usuario,
    ADD CONSTRAINT fk_capacidade_semanal_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE capacidade_override
    DROP CONSTRAINT fk_capacidade_override_usuario,
    ADD CONSTRAINT fk_capacidade_override_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE plano
    DROP CONSTRAINT fk_plano_usuario,
    ADD CONSTRAINT fk_plano_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE bloco_plano
    DROP CONSTRAINT fk_bloco_plano_plano,
    ADD CONSTRAINT fk_bloco_plano_plano FOREIGN KEY (plano_id) REFERENCES plano (id) ON DELETE CASCADE;

ALTER TABLE execucao
    DROP CONSTRAINT fk_execucao_usuario,
    ADD CONSTRAINT fk_execucao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE xp_evento
    DROP CONSTRAINT fk_xp_evento_usuario,
    ADD CONSTRAINT fk_xp_evento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE projeto
    DROP CONSTRAINT fk_projeto_usuario,
    ADD CONSTRAINT fk_projeto_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE reserva_projeto_semanal
    DROP CONSTRAINT fk_reserva_projeto_semanal_usuario,
    ADD CONSTRAINT fk_reserva_projeto_semanal_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE habito
    DROP CONSTRAINT fk_habito_usuario,
    ADD CONSTRAINT fk_habito_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE tag
    DROP CONSTRAINT fk_tag_usuario,
    ADD CONSTRAINT fk_tag_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE item_decidir
    DROP CONSTRAINT fk_item_decidir_usuario,
    ADD CONSTRAINT fk_item_decidir_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE espera
    DROP CONSTRAINT fk_espera_usuario,
    ADD CONSTRAINT fk_espera_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

ALTER TABLE objetivo
    DROP CONSTRAINT fk_objetivo_usuario,
    ADD CONSTRAINT fk_objetivo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

-- FKs internas: mesmo comportamento imediato, mas adiáveis pela exclusão de conta.
ALTER TABLE bloco_plano
    DROP CONSTRAINT fk_bloco_plano_tarefa,
    ADD CONSTRAINT fk_bloco_plano_tarefa FOREIGN KEY (tarefa_id) REFERENCES tarefa (id)
        DEFERRABLE INITIALLY IMMEDIATE,
    DROP CONSTRAINT fk_bloco_plano_habito,
    ADD CONSTRAINT fk_bloco_plano_habito FOREIGN KEY (habito_id) REFERENCES habito (id)
        DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE execucao
    DROP CONSTRAINT fk_execucao_bloco_plano,
    ADD CONSTRAINT fk_execucao_bloco_plano FOREIGN KEY (bloco_plano_id) REFERENCES bloco_plano (id)
        DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE tarefa
    DROP CONSTRAINT fk_tarefa_parent,
    ADD CONSTRAINT fk_tarefa_parent FOREIGN KEY (parent_id) REFERENCES tarefa (id)
        DEFERRABLE INITIALLY IMMEDIATE,
    DROP CONSTRAINT fk_tarefa_projeto,
    ADD CONSTRAINT fk_tarefa_projeto FOREIGN KEY (projeto_id) REFERENCES projeto (id)
        DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE reserva_projeto_semanal
    DROP CONSTRAINT fk_reserva_projeto_semanal_projeto,
    ADD CONSTRAINT fk_reserva_projeto_semanal_projeto FOREIGN KEY (projeto_id) REFERENCES projeto (id)
        DEFERRABLE INITIALLY IMMEDIATE;
