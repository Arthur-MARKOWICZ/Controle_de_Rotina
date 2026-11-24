-- Instante em que a tarefa passou a FINALIZADA; NULL enquanto não está finalizada.
-- Tarefas já finalizadas ganham a data do evento de XP de finalização quando ele existe
-- (só existe quando a finalização veio de uma execução). As demais ficam sem data.
ALTER TABLE tarefa
    ADD COLUMN finalizada_em TIMESTAMPTZ;

UPDATE tarefa t
   SET finalizada_em = x.created_at
  FROM xp_evento x
 WHERE x.tipo = 'TAREFA_FINALIZADA'
   AND x.origem_id = t.id
   AND t.status = 'FINALIZADA';
