package com.controlerotina.historico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.historico.CalibracaoCalculadora.Amostra;
import com.controlerotina.historico.CalibracaoCalculadora.ProjetoInfo;
import com.controlerotina.historico.CalibracaoCalculadora.Resultado;
import com.controlerotina.historico.CalibracaoCalculadora.TagInfo;

class CalibracaoCalculadoraTest {

    private static final Instant BASE = Instant.parse("2026-11-10T12:00:00Z");

    private static final UUID TAG_FACULDADE = UUID.randomUUID();
    private static final UUID TAG_LEITURA = UUID.randomUUID();
    private static final UUID PROJETO_TCC = UUID.randomUUID();

    private static final Map<UUID, TagInfo> TAGS = Map.of(
            TAG_FACULDADE, new TagInfo(TAG_FACULDADE, "faculdade", "#3366FF"),
            TAG_LEITURA, new TagInfo(TAG_LEITURA, "leitura", "#22AA55")
    );
    private static final Map<UUID, ProjetoInfo> PROJETOS = Map.of(
            PROJETO_TCC, new ProjetoInfo(PROJETO_TCC, "TCC")
    );

    private static Amostra amostra(int planejadoMin, long realSeg) {
        return amostra(planejadoMin, realSeg, null, Set.of());
    }

    private static Amostra amostra(int planejadoMin, long realSeg, UUID projetoId, Set<UUID> tags) {
        return new Amostra(BASE, BASE.plusSeconds(realSeg), planejadoMin, projetoId, tags);
    }

    private static List<Amostra> exemploDaSpec(UUID projetoId, Set<UUID> tags) {
        return List.of(
                amostra(30, 45 * 60, projetoId, tags),
                amostra(60, 60 * 60, projetoId, tags),
                amostra(60, 90 * 60, projetoId, tags),
                amostra(30, 30 * 60, projetoId, tags),
                amostra(120, 150 * 60, projetoId, tags)
        );
    }

    private static List<Amostra> repetir(int vezes, int planejadoMin, long realSeg, UUID tag) {
        List<Amostra> lista = new ArrayList<>();
        for (int i = 0; i < vezes; i++) {
            lista.add(amostra(planejadoMin, realSeg, null, Set.of(tag)));
        }
        return lista;
    }

    @Test
    void deveCalcularRazaoPonderadaEDesvioMedioDoExemploDaSpec() {
        Resultado r = CalibracaoCalculadora.calcular(exemploDaSpec(null, Set.of()), TAGS, PROJETOS);

        assertThat(r.geral().amostras()).isEqualTo(5);
        assertThat(r.geral().suficiente()).isTrue();
        assertThat(r.geral().razao()).isEqualByComparingTo("1.25");
        assertThat(r.geral().desvioMedioMinutos()).isEqualByComparingTo("15.0");
    }

    @Test
    void deveArredondarRazaoHalfUpComDuasCasas() {
        // 5 blocos de 60 min, real total 302,5 min -> 302,5/300 = 1,0083 -> 1.01
        List<Amostra> amostras = new ArrayList<>(repetir(4, 60, 60 * 60, TAG_FACULDADE));
        amostras.add(amostra(60, 62 * 60 + 30));

        Resultado r = CalibracaoCalculadora.calcular(amostras, TAGS, PROJETOS);

        assertThat(r.geral().razao()).isEqualTo(new BigDecimal("1.01"));
        assertThat(r.geral().desvioMedioMinutos()).isEqualTo(new BigDecimal("0.5"));
    }

    @Test
    void deveRetornarSemDadosQuandoListaVazia() {
        Resultado r = CalibracaoCalculadora.calcular(List.of(), TAGS, PROJETOS);

        assertThat(r.geral().amostras()).isZero();
        assertThat(r.geral().suficiente()).isFalse();
        assertThat(r.geral().razao()).isNull();
        assertThat(r.geral().desvioMedioMinutos()).isNull();
        assertThat(r.porTag()).isEmpty();
        assertThat(r.porProjeto()).isEmpty();
    }

    @Test
    void deveRetornarSemNumeroComQuatroAmostrasESuficienteComCinco() {
        List<Amostra> quatro = exemploDaSpec(null, Set.of()).subList(0, 4);

        Resultado comQuatro = CalibracaoCalculadora.calcular(quatro, TAGS, PROJETOS);
        Resultado comCinco = CalibracaoCalculadora.calcular(exemploDaSpec(null, Set.of()), TAGS, PROJETOS);

        assertThat(comQuatro.geral().amostras()).isEqualTo(4);
        assertThat(comQuatro.geral().suficiente()).isFalse();
        assertThat(comQuatro.geral().razao()).isNull();
        assertThat(comCinco.geral().suficiente()).isTrue();
    }

    @Test
    void deveAceitarOutliersNosLimitesInclusivos() {
        List<Amostra> amostras = List.of(
                amostra(60, 15 * 60),   // 0,25 exato
                amostra(60, 240 * 60),  // 4,0 exato
                amostra(60, 60 * 60),
                amostra(60, 60 * 60),
                amostra(60, 60 * 60)
        );

        Resultado r = CalibracaoCalculadora.calcular(amostras, TAGS, PROJETOS);

        assertThat(r.geral().amostras()).isEqualTo(5);
    }

    @Test
    void deveDescartarOutliersLogoForaDosLimites() {
        List<Amostra> amostras = new ArrayList<>(exemploDaSpec(null, Set.of()));
        amostras.add(amostra(60, 15 * 60 - 1));   // 0,2499...
        amostras.add(amostra(60, 240 * 60 + 1));  // 4,0002...
        amostras.add(amostra(30, 5 * 60 * 60));   // sessão esquecida

        Resultado r = CalibracaoCalculadora.calcular(amostras, TAGS, PROJETOS);

        assertThat(r.geral().amostras()).isEqualTo(5);
        assertThat(r.geral().razao()).isEqualByComparingTo("1.25");
    }

    @Test
    void deveDescartarConclusaoDiretaEInstantesInvalidos() {
        List<Amostra> amostras = new ArrayList<>(exemploDaSpec(null, Set.of()));
        amostras.add(new Amostra(BASE, BASE, 60, null, Set.of()));                  // concluir-direto
        amostras.add(new Amostra(BASE, BASE.minusSeconds(60), 60, null, Set.of())); // fim antes do início
        amostras.add(new Amostra(null, BASE, 60, null, Set.of()));                  // sem início
        amostras.add(new Amostra(BASE, BASE.plusSeconds(60), 0, null, Set.of()));   // planejado zero

        Resultado r = CalibracaoCalculadora.calcular(amostras, TAGS, PROJETOS);

        assertThat(r.geral().amostras()).isEqualTo(5);
    }

    @Test
    void deveContarAmostraEmCadaTagDaTarefa() {
        Resultado r = CalibracaoCalculadora.calcular(
                exemploDaSpec(null, Set.of(TAG_FACULDADE, TAG_LEITURA)), TAGS, PROJETOS);

        assertThat(r.porTag()).hasSize(2);
        assertThat(r.porTag()).allSatisfy(rt -> {
            assertThat(rt.metricas().amostras()).isEqualTo(5);
            assertThat(rt.metricas().razao()).isEqualByComparingTo("1.25");
        });
        assertThat(r.porTag()).extracting(rt -> rt.tag().cor()).containsExactlyInAnyOrder("#3366FF", "#22AA55");
    }

    @Test
    void deveIgnorarTagApagadaQueNaoEstaNoCatalogo() {
        UUID tagApagada = UUID.randomUUID();

        Resultado r = CalibracaoCalculadora.calcular(exemploDaSpec(null, Set.of(tagApagada)), TAGS, PROJETOS);

        assertThat(r.geral().amostras()).isEqualTo(5);
        assertThat(r.porTag()).isEmpty();
    }

    @Test
    void deveDeixarTarefaSemProjetoSoNoGeral() {
        Resultado r = CalibracaoCalculadora.calcular(exemploDaSpec(null, Set.of()), TAGS, PROJETOS);

        assertThat(r.geral().amostras()).isEqualTo(5);
        assertThat(r.porProjeto()).isEmpty();
    }

    @Test
    void deveAgruparPorProjeto() {
        Resultado r = CalibracaoCalculadora.calcular(exemploDaSpec(PROJETO_TCC, Set.of()), TAGS, PROJETOS);

        assertThat(r.porProjeto()).singleElement().satisfies(rp -> {
            assertThat(rp.projeto().nome()).isEqualTo("TCC");
            assertThat(rp.metricas().razao()).isEqualByComparingTo("1.25");
        });
    }

    @Test
    void deveOrdenarSuficientesPorDistanciaEInsuficientesPorAmostras() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        Map<UUID, TagInfo> tags = Map.of(
                a, new TagInfo(a, "A", "#000001"),
                b, new TagInfo(b, "B", "#000002"),
                c, new TagInfo(c, "C", "#000003"),
                d, new TagInfo(d, "D", "#000004")
        );
        List<Amostra> amostras = new ArrayList<>();
        amostras.addAll(repetir(5, 60, 66 * 60, a)); // 1,10
        amostras.addAll(repetir(5, 60, 36 * 60, b)); // 0,60
        amostras.addAll(repetir(3, 60, 60 * 60, c)); // insuficiente, 3
        amostras.addAll(repetir(4, 60, 60 * 60, d)); // insuficiente, 4

        Resultado r = CalibracaoCalculadora.calcular(amostras, tags, PROJETOS);

        assertThat(r.porTag()).extracting(rt -> rt.tag().nome()).containsExactly("B", "A", "D", "C");
    }

    @Test
    void deveDesempatarPorNomeSemDiferenciarMaiusculas() {
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        Map<UUID, TagInfo> tags = Map.of(
                x, new TagInfo(x, "zeta", "#000001"),
                y, new TagInfo(y, "Alfa", "#000002")
        );
        List<Amostra> amostras = new ArrayList<>();
        amostras.addAll(repetir(5, 60, 72 * 60, x));
        amostras.addAll(repetir(5, 60, 72 * 60, y));

        Resultado r = CalibracaoCalculadora.calcular(amostras, tags, PROJETOS);

        assertThat(r.porTag()).extracting(rt -> rt.tag().nome()).containsExactly("Alfa", "zeta");
    }

    @Test
    void deveLancarNullPointerQuandoAmostrasNulas() {
        assertThatThrownBy(() -> CalibracaoCalculadora.calcular(null, TAGS, PROJETOS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("amostras");
    }

    @Test
    void deveLancarNullPointerQuandoTagsNulas() {
        assertThatThrownBy(() -> CalibracaoCalculadora.calcular(List.of(), null, PROJETOS))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tags");
    }

    @Test
    void deveLancarNullPointerQuandoProjetosNulos() {
        assertThatThrownBy(() -> CalibracaoCalculadora.calcular(List.of(), TAGS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("projetos");
    }
}
