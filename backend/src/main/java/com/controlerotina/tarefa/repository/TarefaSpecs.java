package com.controlerotina.tarefa.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;

public final class TarefaSpecs {

    private TarefaSpecs() {
    }

    public static Specification<Tarefa> doUsuario(UUID usuarioId) {
        return (root, query, cb) -> cb.equal(root.get("usuarioId"), usuarioId);
    }

    public static Specification<Tarefa> comStatus(List<StatusTarefa> status) {
        return (root, query, cb) -> {
            if (status == null || status.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("status").in(status);
        };
    }

    public static Specification<Tarefa> comPrioridade(List<Prioridade> prioridade) {
        return (root, query, cb) -> {
            if (prioridade == null || prioridade.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("prioridade").in(prioridade);
        };
    }

    public static Specification<Tarefa> prazoEntre(Instant de, Instant ate) {
        return (root, query, cb) -> {
            if (de == null && ate == null) {
                return cb.conjunction();
            }
            if (de != null && ate != null) {
                return cb.between(root.get("prazo"), de, ate);
            }
            if (de != null) {
                return cb.greaterThanOrEqualTo(root.get("prazo"), de);
            }
            return cb.lessThanOrEqualTo(root.get("prazo"), ate);
        };
    }

    public static Specification<Tarefa> duracaoEntre(Integer de, Integer ate) {
        return (root, query, cb) -> {
            if (de == null && ate == null) {
                return cb.conjunction();
            }
            if (de != null && ate != null) {
                return cb.between(root.get("duracaoMinutos"), de, ate);
            }
            if (de != null) {
                return cb.greaterThanOrEqualTo(root.get("duracaoMinutos"), de);
            }
            return cb.lessThanOrEqualTo(root.get("duracaoMinutos"), ate);
        };
    }

    public static Specification<Tarefa> criadoEntre(Instant de, Instant ate) {
        return intervaloInstant("createdAt", de, ate);
    }

    public static Specification<Tarefa> atualizadoEntre(Instant de, Instant ate) {
        return intervaloInstant("updatedAt", de, ate);
    }

    public static Specification<Tarefa> filtroDelecao(boolean incluirDeletadas, boolean somenteDeletadas) {
        return (root, query, cb) -> {
            if (somenteDeletadas) {
                return cb.isNotNull(root.get("deletedAt"));
            }
            if (incluirDeletadas) {
                return cb.conjunction();
            }
            return cb.isNull(root.get("deletedAt"));
        };
    }

    private static Specification<Tarefa> intervaloInstant(String campo, Instant de, Instant ate) {
        return (root, query, cb) -> {
            if (de == null && ate == null) {
                return cb.conjunction();
            }
            if (de != null && ate != null) {
                return cb.between(root.get(campo), de, ate);
            }
            if (de != null) {
                return cb.greaterThanOrEqualTo(root.get(campo), de);
            }
            return cb.lessThanOrEqualTo(root.get(campo), ate);
        };
    }
}
