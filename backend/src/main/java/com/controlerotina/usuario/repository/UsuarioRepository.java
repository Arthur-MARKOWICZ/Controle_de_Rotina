package com.controlerotina.usuario.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.usuario.entity.Usuario;

import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    boolean existsByEmail(String email);

    Optional<Usuario> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Apaga o usuário e, pelo ON DELETE CASCADE do banco (V29), tudo que pertence a ele.
     * Não carrega entidades: a cascata é do banco, não do JPA. Exige transação ativa: as FKs
     * internas (adiáveis) só são checadas no commit, quando as duas pontas já foram apagadas.
     */
    default int excluirPorId(UUID id) {
        adiarConstraintsAdiaveis();
        return apagarLinhaUsuario(id);
    }

    @Modifying
    @Query(value = "SET CONSTRAINTS ALL DEFERRED", nativeQuery = true)
    void adiarConstraintsAdiaveis();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Usuario u where u.id = :id")
    int apagarLinhaUsuario(@Param("id") UUID id);
}
