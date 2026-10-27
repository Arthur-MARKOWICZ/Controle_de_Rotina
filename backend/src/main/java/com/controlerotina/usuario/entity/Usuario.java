package com.controlerotina.usuario.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(name = "xp_total", nullable = false)
    private int xpTotal;

    @Column(nullable = false)
    private int nivel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Usuario() {
    }

    public Usuario(UUID id, String email, String senhaHash, String nome, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.senhaHash = senhaHash;
        this.nome = nome;
        this.xpTotal = 0;
        this.nivel = 1;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void atualizar(String nome, String email, String senhaHash, Instant updatedAt) {
        if (nome != null) {
            this.nome = nome;
        }
        if (email != null) {
            this.email = email;
        }
        if (senhaHash != null) {
            this.senhaHash = senhaHash;
        }
        this.updatedAt = updatedAt;
    }

    public void atualizarProgressoXp(int xpTotal, int nivel, Instant updatedAt) {
        this.xpTotal = xpTotal;
        this.nivel = nivel;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public String getNome() {
        return nome;
    }

    public int getXpTotal() {
        return xpTotal;
    }

    public int getNivel() {
        return nivel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
