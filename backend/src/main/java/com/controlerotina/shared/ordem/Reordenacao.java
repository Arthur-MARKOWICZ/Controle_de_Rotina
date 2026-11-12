package com.controlerotina.shared.ordem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.controlerotina.shared.exception.ReordenacaoInvalidaException;

public final class Reordenacao {

    private Reordenacao() {
    }

    /**
     * Aplica a nova ordem relativa de {@code idsNovos} sobre {@code atuais},
     * preservando a posição dos itens que não foram mencionados.
     */
    public static <T> List<T> aplicar(List<T> atuais, List<UUID> idsNovos, Function<T, UUID> idDe) {
        if (idsNovos == null || idsNovos.isEmpty()) {
            throw new ReordenacaoInvalidaException("Informe ao menos um item para reordenar.");
        }
        if (new HashSet<>(idsNovos).size() != idsNovos.size()) {
            throw new ReordenacaoInvalidaException("A lista de ids não pode conter duplicatas.");
        }

        Set<UUID> atuaisIds = atuais.stream().map(idDe).collect(Collectors.toSet());
        for (UUID id : idsNovos) {
            if (!atuaisIds.contains(id)) {
                throw new ReordenacaoInvalidaException("Item não pertence ao mesmo grupo.");
            }
        }

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < atuais.size(); i++) {
            if (idsNovos.contains(idDe.apply(atuais.get(i)))) {
                slots.add(i);
            }
        }

        Map<UUID, T> porId = atuais.stream().collect(Collectors.toMap(idDe, item -> item));
        List<T> resultado = new ArrayList<>(atuais);
        for (int i = 0; i < slots.size(); i++) {
            resultado.set(slots.get(i), porId.get(idsNovos.get(i)));
        }
        return List.copyOf(resultado);
    }
}
