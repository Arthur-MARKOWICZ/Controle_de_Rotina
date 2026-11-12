package com.controlerotina.pendencia.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Esperas de um conjunto de origens, já resolvidas em lote.
 *
 * @param porOrigem esperas de cada origem (somente as dela própria)
 * @param bloqueadas ids das origens bloqueadas, incluindo bloqueio herdado de pai e de projeto
 */
public record EsperasDeOrigem(
        Map<UUID, List<EsperaResumo>> porOrigem,
        Set<UUID> bloqueadas
) {
    public static EsperasDeOrigem vazio() {
        return new EsperasDeOrigem(Map.of(), Set.of());
    }

    public List<EsperaResumo> de(UUID origemId) {
        return porOrigem.getOrDefault(origemId, List.of());
    }

    public boolean bloqueada(UUID origemId) {
        return bloqueadas.contains(origemId);
    }
}
