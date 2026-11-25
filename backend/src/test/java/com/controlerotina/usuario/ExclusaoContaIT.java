package com.controlerotina.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.controlerotina.usuario.repository.UsuarioRepository;

/**
 * Prova a V29: apagar a linha de `usuario` apaga tudo do usuário e nada de outro usuário,
 * e as FKs internas continuam protegendo deletes isolados.
 */
@SpringBootTest
@Testcontainers
class ExclusaoContaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    DadosUsuarioSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DadosUsuarioSeeder(jdbc);
    }

    @Test
    void deveApagarTodasAsLinhasDoUsuarioSemTocarNasDeOutro() {
        UUID b = seeder.criarUsuario("b-" + UUID.randomUUID() + "@email.com");
        seeder.popularTudo(b);
        Map<String, Long> antes = seeder.contagemPorTabela();

        UUID a = seeder.criarUsuario("a-" + UUID.randomUUID() + "@email.com");
        DadosUsuarioSeeder.Ids idsA = seeder.popularTudo(a);

        int apagados = tx.execute(status -> usuarioRepository.excluirPorId(a));

        assertThat(apagados).isEqualTo(1);
        assertThat(seeder.contagemPorTabela()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloco_plano WHERE plano_id = ?", Long.class,
                idsA.get("plano"))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tarefa WHERE usuario_id = ?", Long.class, a)).isZero();
    }

    @Test
    void deveExcluirUsuarioSemNenhumDado() {
        UUID a = seeder.criarUsuario("vazio-" + UUID.randomUUID() + "@email.com");

        int apagados = tx.execute(status -> usuarioRepository.excluirPorId(a));

        assertThat(apagados).isEqualTo(1);
        assertThat(usuarioRepository.existsById(a)).isFalse();
    }

    @Test
    void devePermitirRecadastrarOEmailDepoisDaExclusao() {
        String email = "reuso-" + UUID.randomUUID() + "@email.com";
        UUID a = seeder.criarUsuario(email);
        seeder.popularTudo(a);

        tx.executeWithoutResult(status -> usuarioRepository.excluirPorId(a));
        UUID novo = seeder.criarUsuario(email);

        assertThat(usuarioRepository.findByEmail(email)).get().extracting(u -> u.getId()).isEqualTo(novo);
    }

    @Test
    void naoDeveApagarNadaQuandoUsuarioNaoExiste() {
        UUID b = seeder.criarUsuario("b2-" + UUID.randomUUID() + "@email.com");
        seeder.popularTudo(b);
        Map<String, Long> antes = seeder.contagemPorTabela();

        int apagados = tx.execute(status -> usuarioRepository.excluirPorId(UUID.randomUUID()));

        assertThat(apagados).isZero();
        assertThat(seeder.contagemPorTabela()).isEqualTo(antes);
    }

    @Test
    void todaFkParaUsuarioDeveTerOnDeleteCascade() {
        List<String> semCascata = jdbc.queryForList("""
                SELECT tc.table_name || '.' || tc.constraint_name
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.referential_constraints rc
                    ON rc.constraint_name = tc.constraint_name
                  JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = rc.unique_constraint_name
                 WHERE tc.constraint_type = 'FOREIGN KEY'
                   AND ccu.table_name = 'usuario'
                   AND rc.delete_rule <> 'CASCADE'
                """, String.class);

        assertThat(semCascata).isEmpty();
    }

    @Test
    void deleteIsoladoDeTarefaComBlocoContinuaBarrado() {
        UUID a = seeder.criarUsuario("fk-" + UUID.randomUUID() + "@email.com");
        DadosUsuarioSeeder.Ids ids = seeder.popularTudo(a);
        Map<String, Long> antes = seeder.contagemPorTabela();

        assertThatThrownBy(() -> jdbc.update("DELETE FROM tarefa WHERE id = ?", ids.get("tarefa")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(seeder.contagemPorTabela()).isEqualTo(antes);
    }

    @Test
    void deleteIsoladoDeProjetoComTarefaContinuaBarrado() {
        UUID a = seeder.criarUsuario("fk2-" + UUID.randomUUID() + "@email.com");
        DadosUsuarioSeeder.Ids ids = seeder.popularTudo(a);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM projeto WHERE id = ?", ids.get("projeto")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM projeto WHERE id = ?", Long.class, ids.get("projeto")))
                .isEqualTo(1);
    }

    @Test
    void deleteIsoladoDeBlocoComExecucaoContinuaBarrado() {
        UUID a = seeder.criarUsuario("fk3-" + UUID.randomUUID() + "@email.com");
        DadosUsuarioSeeder.Ids ids = seeder.popularTudo(a);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM bloco_plano WHERE id = ?", ids.get("blocoTarefa")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
