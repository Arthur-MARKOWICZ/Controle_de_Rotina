package com.controlerotina.usuario;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Popula, direto no banco, uma linha em cada domínio do usuário (inclusive tabelas de junção,
 * bloco de plano com execução e registros removidos logicamente). Usado pelos ITs de exclusão
 * de conta e de export, que precisam de "dados em todos os domínios".
 */
public final class DadosUsuarioSeeder {

    private final JdbcTemplate jdbc;

    public DadosUsuarioSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cria só a linha de `usuario` (senha qualquer) e devolve o id. */
    public UUID criarUsuario(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO usuario (id, email, senha_hash, nome, created_at, updated_at)
                VALUES (?, ?, 'hash', 'Usuario Seed', now(), now())
                """, id, email);
        return id;
    }

    /** Ids criados por {@link #popularTudo(UUID)}, por nome lógico. */
    public record Ids(Map<String, UUID> porNome) {
        public UUID get(String nome) {
            return porNome.get(nome);
        }
    }

    public Ids popularTudo(UUID usuarioId) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        Timestamp agora = Timestamp.from(Instant.now());

        UUID projeto = novo(ids, "projeto");
        jdbc.update("""
                INSERT INTO projeto (id, usuario_id, nome, status, created_at, updated_at)
                VALUES (?, ?, 'Projeto', 'ATIVO', ?, ?)
                """, projeto, usuarioId, agora, agora);
        UUID projetoArquivado = novo(ids, "projetoArquivado");
        jdbc.update("""
                INSERT INTO projeto (id, usuario_id, nome, status, created_at, updated_at)
                VALUES (?, ?, 'Projeto velho', 'ARQUIVADO', ?, ?)
                """, projetoArquivado, usuarioId, agora, agora);
        jdbc.update("""
                INSERT INTO reserva_projeto_semanal
                    (id, usuario_id, projeto_id, dia_semana, minutos_reservados, created_at, updated_at)
                VALUES (?, ?, ?, 'SEGUNDA', 30, ?, ?)
                """, novo(ids, "reserva"), usuarioId, projeto, agora, agora);

        UUID tarefa = novo(ids, "tarefa");
        inserirTarefa(tarefa, usuarioId, "Tarefa", projeto, null, null, agora);
        UUID subtarefa = novo(ids, "subtarefa");
        inserirTarefa(subtarefa, usuarioId, "Subtarefa", null, tarefa, null, agora);
        UUID tarefaApagada = novo(ids, "tarefaApagada");
        inserirTarefa(tarefaApagada, usuarioId, "Apagada", null, null, agora, agora);

        UUID habito = novo(ids, "habito");
        jdbc.update("""
                INSERT INTO habito (id, usuario_id, titulo, duracao_minutos, status, created_at, updated_at)
                VALUES (?, ?, 'Habito', 30, 'ATIVO', ?, ?)
                """, habito, usuarioId, agora, agora);
        jdbc.update("INSERT INTO habito_dia_semana (habito_id, dia_semana) VALUES (?, 'SEGUNDA')", habito);
        UUID habitoApagado = novo(ids, "habitoApagado");
        jdbc.update("""
                INSERT INTO habito (id, usuario_id, titulo, duracao_minutos, status, created_at, updated_at, deleted_at)
                VALUES (?, ?, 'Habito apagado', 15, 'PAUSADO', ?, ?, ?)
                """, habitoApagado, usuarioId, agora, agora, agora);

        UUID tag = novo(ids, "tag");
        jdbc.update("""
                INSERT INTO tag (id, usuario_id, nome, nome_normalizado, cor, created_at, updated_at)
                VALUES (?, ?, 'estudo', ?, '#123ABC', ?, ?)
                """, tag, usuarioId, "estudo-" + tag, agora, agora);

        UUID itemDecidir = novo(ids, "itemDecidir");
        jdbc.update("""
                INSERT INTO item_decidir (id, usuario_id, titulo, projeto_id, created_at, updated_at)
                VALUES (?, ?, 'Decidir', ?, ?, ?)
                """, itemDecidir, usuarioId, projeto, agora, agora);

        jdbc.update("INSERT INTO tarefa_tag (tarefa_id, tag_id) VALUES (?, ?)", tarefa, tag);
        jdbc.update("INSERT INTO habito_tag (habito_id, tag_id) VALUES (?, ?)", habito, tag);
        jdbc.update("INSERT INTO projeto_tag (projeto_id, tag_id) VALUES (?, ?)", projeto, tag);
        jdbc.update("INSERT INTO decidir_tag (item_decidir_id, tag_id) VALUES (?, ?)", itemDecidir, tag);

        jdbc.update("""
                INSERT INTO espera (id, usuario_id, origem_tipo, origem_id, alvo_tipo, alvo_id, created_at)
                VALUES (?, ?, 'TAREFA', ?, 'PROJETO', ?, ?)
                """, novo(ids, "espera"), usuarioId, tarefa, projetoArquivado, agora);

        UUID objetivo = novo(ids, "objetivo");
        jdbc.update("""
                INSERT INTO objetivo (id, usuario_id, titulo, status, created_at, updated_at)
                VALUES (?, ?, 'Objetivo', 'ATIVO', ?, ?)
                """, objetivo, usuarioId, agora, agora);
        UUID objetivoArquivado = novo(ids, "objetivoArquivado");
        jdbc.update("""
                INSERT INTO objetivo (id, usuario_id, titulo, status, arquivado_em, created_at, updated_at)
                VALUES (?, ?, 'Objetivo velho', 'ARQUIVADO', ?, ?, ?)
                """, objetivoArquivado, usuarioId, agora, agora, agora);
        jdbc.update("INSERT INTO objetivo_tarefa (objetivo_id, tarefa_id) VALUES (?, ?)", objetivo, tarefa);
        jdbc.update("INSERT INTO objetivo_habito (objetivo_id, habito_id) VALUES (?, ?)", objetivo, habito);
        jdbc.update("INSERT INTO objetivo_projeto (objetivo_id, projeto_id) VALUES (?, ?)", objetivo, projeto);

        jdbc.update("""
                INSERT INTO capacidade_semanal (id, usuario_id, dia_semana, horas, ativo, created_at, updated_at)
                VALUES (?, ?, 'SEGUNDA', 8, false, ?, ?)
                """, novo(ids, "capacidadeSemanal"), usuarioId, agora, agora);
        jdbc.update("""
                INSERT INTO capacidade_override (id, usuario_id, data, horas, ativo, created_at, updated_at)
                VALUES (?, ?, ?, 4, true, ?, ?)
                """, novo(ids, "capacidadeOverride"), usuarioId, LocalDate.of(2026, 12, 1), agora, agora);

        UUID plano = novo(ids, "plano");
        jdbc.update("""
                INSERT INTO plano (id, usuario_id, data, versao, status, tem_conflito, gerado_em, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'APROVADO', false, ?, ?, ?)
                """, plano, usuarioId, LocalDate.of(2026, 11, 24), agora, agora, agora);
        UUID blocoTarefa = novo(ids, "blocoTarefa");
        jdbc.update("""
                INSERT INTO bloco_plano (id, plano_id, tarefa_id, duracao_minutos, ordem, created_at, updated_at)
                VALUES (?, ?, ?, 60, 1, ?, ?)
                """, blocoTarefa, plano, tarefa, agora, agora);
        UUID blocoHabito = novo(ids, "blocoHabito");
        jdbc.update("""
                INSERT INTO bloco_plano (id, plano_id, habito_id, duracao_minutos, ordem, created_at, updated_at)
                VALUES (?, ?, ?, 30, 2, ?, ?)
                """, blocoHabito, plano, habito, agora, agora);
        jdbc.update("""
                INSERT INTO execucao (id, usuario_id, bloco_plano_id, status, iniciado_em, concluido_em, created_at, updated_at)
                VALUES (?, ?, ?, 'CONCLUIDO', ?, ?, ?, ?)
                """, novo(ids, "execucao"), usuarioId, blocoTarefa, agora, agora, agora, agora);
        jdbc.update("""
                INSERT INTO xp_evento (id, usuario_id, tipo, origem_id, quantidade, created_at)
                VALUES (?, ?, 'BLOCO_CONCLUIDO', ?, 10, ?)
                """, novo(ids, "xpEvento"), usuarioId, blocoTarefa, agora);

        jdbc.update("""
                INSERT INTO refresh_token (id, usuario_id, token_hash, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, novo(ids, "refreshToken"), usuarioId,
                (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64),
                Timestamp.from(Instant.now().plusSeconds(3600)), agora);

        return new Ids(ids);
    }

    /** Contagem de linhas de cada tabela de negócio (todas menos o histórico do Flyway). */
    public Map<String, Long> contagemPorTabela() {
        List<String> tabelas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                   AND table_name <> 'flyway_schema_history'
                 ORDER BY table_name
                """, String.class);
        Map<String, Long> contagem = new LinkedHashMap<>();
        for (String tabela : tabelas) {
            contagem.put(tabela, jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class));
        }
        return contagem;
    }

    private void inserirTarefa(
            UUID id, UUID usuarioId, String titulo, UUID projetoId, UUID parentId, Timestamp deletedAt, Timestamp agora
    ) {
        jdbc.update("""
                INSERT INTO tarefa (id, usuario_id, titulo, descricao, prioridade, status, duracao_minutos,
                                    projeto_id, parent_id, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, '', 'MEDIA', 'PENDENTE', 60, ?, ?, ?, ?, ?)
                """, id, usuarioId, titulo, projetoId, parentId, agora, agora, deletedAt);
    }

    private static UUID novo(Map<String, UUID> ids, String nome) {
        UUID id = UUID.randomUUID();
        ids.put(nome, id);
        return id;
    }
}
