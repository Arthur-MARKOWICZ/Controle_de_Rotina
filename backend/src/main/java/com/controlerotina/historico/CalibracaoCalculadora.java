package com.controlerotina.historico;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Razão tempo real ÷ planejado dos blocos de tarefa concluídos com timer.
 * Função pura: recebe amostras já carregadas e devolve os recortes geral, por tag e por projeto.
 */
public final class CalibracaoCalculadora {

    public static final int MINIMO_AMOSTRAS = 5;
    public static final int JANELA_DIAS = 90;
    /** Razão individual mínima aceita (1/4): abaixo disso é clique acidental. */
    static final int FATOR_OUTLIER = 4;

    private static final BigDecimal UM = BigDecimal.ONE;

    private CalibracaoCalculadora() {
    }

    public static Resultado calcular(
            List<Amostra> amostras,
            Map<UUID, TagInfo> tags,
            Map<UUID, ProjetoInfo> projetos
    ) {
        Objects.requireNonNull(amostras, "amostras");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(projetos, "projetos");

        List<Medida> validas = amostras.stream()
                .map(CalibracaoCalculadora::medir)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, List<Medida>> porTag = new LinkedHashMap<>();
        Map<UUID, List<Medida>> porProjeto = new LinkedHashMap<>();
        for (Medida m : validas) {
            for (UUID tagId : m.tagIds()) {
                if (tags.containsKey(tagId)) {
                    porTag.computeIfAbsent(tagId, k -> new ArrayList<>()).add(m);
                }
            }
            if (m.projetoId() != null && projetos.containsKey(m.projetoId())) {
                porProjeto.computeIfAbsent(m.projetoId(), k -> new ArrayList<>()).add(m);
            }
        }

        List<RecorteTag> recortesTag = porTag.entrySet().stream()
                .map(e -> new RecorteTag(tags.get(e.getKey()), agregar(e.getValue())))
                .sorted(ordem(RecorteTag::metricas, r -> r.tag().nome()))
                .toList();
        List<RecorteProjeto> recortesProjeto = porProjeto.entrySet().stream()
                .map(e -> new RecorteProjeto(projetos.get(e.getKey()), agregar(e.getValue())))
                .sorted(ordem(RecorteProjeto::metricas, r -> r.projeto().nome()))
                .toList();

        return new Resultado(agregar(validas), recortesTag, recortesProjeto);
    }

    /** Devolve a medida em segundos ou {@code null} se a amostra deve ser descartada. */
    private static Medida medir(Amostra a) {
        if (a.iniciadoEm() == null || a.concluidoEm() == null || a.duracaoPlanejadaMinutos() <= 0) {
            return null;
        }
        long realSeg = Duration.between(a.iniciadoEm(), a.concluidoEm()).toSeconds();
        if (realSeg <= 0) {
            return null;
        }
        long planejadoSeg = a.duracaoPlanejadaMinutos() * 60L;
        boolean abaixoDoMinimo = realSeg * FATOR_OUTLIER < planejadoSeg;
        boolean acimaDoMaximo = realSeg > planejadoSeg * FATOR_OUTLIER;
        if (abaixoDoMinimo || acimaDoMaximo) {
            return null;
        }
        Set<UUID> tagIds = a.tagIds() == null ? Set.of() : a.tagIds();
        return new Medida(realSeg, planejadoSeg, a.projetoId(), tagIds);
    }

    static Metricas agregar(List<Medida> medidas) {
        int n = medidas.size();
        if (n < MINIMO_AMOSTRAS) {
            return new Metricas(n, false, null, null);
        }
        long somaReal = 0;
        long somaPlanejado = 0;
        long somaDesvio = 0;
        for (Medida m : medidas) {
            somaReal += m.realSeg();
            somaPlanejado += m.planejadoSeg();
            somaDesvio += Math.abs(m.realSeg() - m.planejadoSeg());
        }
        BigDecimal razao = BigDecimal.valueOf(somaReal)
                .divide(BigDecimal.valueOf(somaPlanejado), 2, RoundingMode.HALF_UP);
        BigDecimal desvio = BigDecimal.valueOf(somaDesvio)
                .divide(BigDecimal.valueOf(n * 60L), 1, RoundingMode.HALF_UP);
        return new Metricas(n, true, razao, desvio);
    }

    private static <T> Comparator<T> ordem(
            Function<T, Metricas> metricas,
            Function<T, String> nome
    ) {
        Comparator<T> suficientesPrimeiro = Comparator.comparing(r -> !metricas.apply(r).suficiente());
        Comparator<T> porDistanciaOuAmostras = (a, b) -> {
            Metricas ma = metricas.apply(a);
            Metricas mb = metricas.apply(b);
            if (ma.suficiente()) {
                return distancia(mb).compareTo(distancia(ma));
            }
            return Integer.compare(mb.amostras(), ma.amostras());
        };
        return suficientesPrimeiro
                .thenComparing(porDistanciaOuAmostras)
                .thenComparing(r -> nome.apply(r), String.CASE_INSENSITIVE_ORDER);
    }

    private static BigDecimal distancia(Metricas m) {
        return m.razao().subtract(UM).abs();
    }

    public record Amostra(
            Instant iniciadoEm,
            Instant concluidoEm,
            int duracaoPlanejadaMinutos,
            UUID projetoId,
            Set<UUID> tagIds
    ) {
    }

    record Medida(long realSeg, long planejadoSeg, UUID projetoId, Set<UUID> tagIds) {
    }

    public record TagInfo(UUID id, String nome, String cor) {
    }

    public record ProjetoInfo(UUID id, String nome) {
    }

    /** {@code razao} e {@code desvioMedioMinutos} são nulos quando {@code suficiente} é falso. */
    public record Metricas(int amostras, boolean suficiente, BigDecimal razao, BigDecimal desvioMedioMinutos) {
    }

    public record RecorteTag(TagInfo tag, Metricas metricas) {
    }

    public record RecorteProjeto(ProjetoInfo projeto, Metricas metricas) {
    }

    public record Resultado(Metricas geral, List<RecorteTag> porTag, List<RecorteProjeto> porProjeto) {
    }
}
