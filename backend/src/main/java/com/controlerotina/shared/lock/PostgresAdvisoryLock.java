package com.controlerotina.shared.lock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

@Component
public class PostgresAdvisoryLock {

    private final DataSource dataSource;

    public PostgresAdvisoryLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Tenta obter {@code pg_try_advisory_lock} na mesma conexão e executa {@code acao}.
     *
     * @return {@code false} se outra sessão já detém o lock; {@code true} se executou.
     */
    public boolean tryWithLock(long chave, Runnable acao) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection, chave)) {
                return false;
            }
            try {
                acao.run();
                return true;
            } finally {
                unlock(connection, chave);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Falha ao usar advisory lock Postgres", ex);
        }
    }

    private static boolean tryLock(Connection connection, long chave) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, chave);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection connection, long chave) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, chave);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }
}
