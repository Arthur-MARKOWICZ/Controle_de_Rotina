package com.controlerotina.capacidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.entity.OrigemCapacidade;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.capacidade.repository.CapacidadeSemanalRepository;
import com.controlerotina.shared.exception.IntervaloCapacidadeInvalidoException;

@ExtendWith(MockitoExtension.class)
class CapacidadeResolvidaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    CapacidadeOverrideRepository overrideRepository;
    @Mock
    CapacidadeSemanalRepository semanalRepository;

    private CapacidadeResolvidaService service;

    @BeforeEach
    void setUp() {
        service = new CapacidadeResolvidaService(overrideRepository, semanalRepository);
    }

    @Test
    void deveResolverOverrideSobreSemanalEVazio() {
        LocalDate quinta = LocalDate.of(2026, 10, 8);
        LocalDate sexta = LocalDate.of(2026, 10, 9);
        LocalDate sabado = LocalDate.of(2026, 10, 10);
        when(overrideRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(USUARIO, quinta, sabado))
                .thenReturn(List.of(new CapacidadeOverride(
                        UUID.randomUUID(), USUARIO, quinta, 1, true, AGORA, AGORA)));
        when(semanalRepository.findByUsuarioIdAndAtivoTrueOrderByDiaSemanaAsc(USUARIO))
                .thenReturn(List.of(new CapacidadeSemanal(
                        UUID.randomUUID(), USUARIO, DiaSemana.SEXTA, 6, true, AGORA, AGORA)));

        var resultado = service.resolver(USUARIO, quinta, sabado);

        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).origem()).isEqualTo(OrigemCapacidade.OVERRIDE);
        assertThat(resultado.get(0).horas()).isEqualTo(1);
        assertThat(resultado.get(1).origem()).isEqualTo(OrigemCapacidade.SEMANAL);
        assertThat(resultado.get(1).horas()).isEqualTo(6);
        assertThat(resultado.get(2).origem()).isEqualTo(OrigemCapacidade.VAZIO);
        assertThat(resultado.get(2).horas()).isNull();
    }

    @Test
    void deveRetornarVazioQuandoUsuarioNaoTemDados() {
        LocalDate de = LocalDate.of(2026, 10, 8);
        LocalDate ate = LocalDate.of(2026, 10, 9);
        when(overrideRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(USUARIO, de, ate))
                .thenReturn(List.of());
        when(semanalRepository.findByUsuarioIdAndAtivoTrueOrderByDiaSemanaAsc(USUARIO))
                .thenReturn(List.of());

        var resultado = service.resolver(USUARIO, de, ate);

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(r -> r.origem() == OrigemCapacidade.VAZIO);
    }

    @Test
    void deveRejeitarPeriodoInvertido() {
        assertThatThrownBy(() -> service.resolver(
                USUARIO, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 8)))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
    }

    @Test
    void deveRejeitarPeriodoAcimaDe31Dias() {
        assertThatThrownBy(() -> service.resolver(
                USUARIO, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
    }

    @Test
    void deveAceitarPeriodoDe31Dias() {
        LocalDate de = LocalDate.of(2026, 10, 1);
        LocalDate ate = LocalDate.of(2026, 10, 31);
        when(overrideRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(USUARIO, de, ate))
                .thenReturn(List.of());
        when(semanalRepository.findByUsuarioIdAndAtivoTrueOrderByDiaSemanaAsc(USUARIO))
                .thenReturn(List.of());

        assertThat(service.resolver(USUARIO, de, ate)).hasSize(31);
    }

    @Test
    void deveRejeitarDatasNulas() {
        assertThatThrownBy(() -> service.resolver(USUARIO, null, LocalDate.of(2026, 10, 8)))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
        assertThatThrownBy(() -> service.resolver(USUARIO, LocalDate.of(2026, 10, 8), null))
                .isInstanceOf(IntervaloCapacidadeInvalidoException.class);
    }

    @Test
    void deveResolverUmUnicoDia() {
        LocalDate dia = LocalDate.of(2026, 10, 8);
        when(overrideRepository.findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(USUARIO, dia, dia))
                .thenReturn(List.of());
        when(semanalRepository.findByUsuarioIdAndAtivoTrueOrderByDiaSemanaAsc(USUARIO))
                .thenReturn(List.of());

        var resultado = service.resolver(USUARIO, dia, dia);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).data()).isEqualTo(dia);
        assertThat(resultado.get(0).origem()).isEqualTo(OrigemCapacidade.VAZIO);
    }
}
