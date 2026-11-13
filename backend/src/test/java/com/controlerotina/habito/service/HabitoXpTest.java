package com.controlerotina.habito.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;
import com.controlerotina.xp.XpMotor;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.entity.XpEvento;
import com.controlerotina.xp.repository.XpEventoRepository;
import com.controlerotina.xp.service.XpConcessaoService;

@ExtendWith(MockitoExtension.class)
class HabitoXpTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T15:00:00Z");

    @Mock
    XpEventoRepository xpEventoRepository;

    @Mock
    UsuarioRepository usuarioRepository;

    XpConcessaoService service;

    @BeforeEach
    void setUp() {
        service = new XpConcessaoService(
                xpEventoRepository,
                usuarioRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void t6_xpBlocoHabitoUsaPesoMediaSemTarefaFinalizada() {
        assertThat(XpMotor.xpBloco(60, Prioridade.MEDIA)).isEqualTo(90);

        UUID usuarioId = UUID.randomUUID();
        UUID execucaoId = UUID.randomUUID();
        Usuario usuario = new Usuario(usuarioId, "ana@email.com", "hash", "Ana", AGORA, AGORA);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, execucaoId))
                .thenReturn(false);

        XpConcessaoService.ResultadoConcessao resultado =
                service.concederBlocoHabito(usuarioId, execucaoId, 60);

        assertThat(resultado.ganhoNesteRequest()).isEqualTo(90);
        ArgumentCaptor<Iterable<XpEvento>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(xpEventoRepository).saveAll(captor.capture());
        Iterable<XpEvento> salvos = captor.getValue();
        assertThat(salvos).hasSize(1);
        XpEvento evento = salvos.iterator().next();
        assertThat(evento.getTipo()).isEqualTo(TipoXpEvento.BLOCO_CONCLUIDO);
        assertThat(evento.getQuantidade()).isEqualTo(90);
        verify(xpEventoRepository, never()).existsByTipoAndOrigemId(
                eq(TipoXpEvento.TAREFA_FINALIZADA), any());
    }

    @Test
    void naoDeveConcederXpDuasVezesParaMesmoBlocoHabito() {
        UUID usuarioId = UUID.randomUUID();
        UUID execucaoId = UUID.randomUUID();
        Usuario usuario = new Usuario(usuarioId, "ana@email.com", "hash", "Ana", AGORA, AGORA);
        usuario.atualizarProgressoXp(40, 1, AGORA);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, execucaoId))
                .thenReturn(true);

        var resultado = service.concederBlocoHabito(usuarioId, execucaoId, 60);

        assertThat(resultado.ganhoNesteRequest()).isZero();
        assertThat(usuario.getXpTotal()).isEqualTo(40);
        verify(xpEventoRepository, never()).saveAll(any());
    }

    @Test
    void minutosZeroNaoDeveCriarEventoDeHabito() {
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = new Usuario(usuarioId, "ana@email.com", "hash", "Ana", AGORA, AGORA);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));

        var resultado = service.concederBlocoHabito(usuarioId, UUID.randomUUID(), 0);

        assertThat(resultado.ganhoNesteRequest()).isZero();
        verify(xpEventoRepository, never()).saveAll(any());
    }

    @Test
    void usuarioInexistenteNoHabitoNaoDeveSalvar() {
        UUID usuarioId = UUID.randomUUID();
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.concederBlocoHabito(usuarioId, UUID.randomUUID(), 60))
                .isInstanceOf(java.util.NoSuchElementException.class);
        verify(xpEventoRepository, never()).saveAll(any());
    }

    @Test
    void minutosNegativosNaoDeveCreditarHabito() {
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = new Usuario(usuarioId, "ana@email.com", "hash", "Ana", AGORA, AGORA);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));

        var resultado = service.concederBlocoHabito(usuarioId, UUID.randomUUID(), -15);

        assertThat(resultado.ganhoNesteRequest()).isZero();
        assertThat(usuario.getXpTotal()).isZero();
    }

    @Test
    void concederHabitoNaoDeveConsultarEventoDeTarefaFinalizada() {
        UUID usuarioId = UUID.randomUUID();
        UUID execucaoId = UUID.randomUUID();
        Usuario usuario = new Usuario(usuarioId, "ana@email.com", "hash", "Ana", AGORA, AGORA);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, execucaoId))
                .thenReturn(false);

        service.concederBlocoHabito(usuarioId, execucaoId, 30);

        verify(xpEventoRepository, never()).existsByTipoAndOrigemId(
                eq(TipoXpEvento.TAREFA_FINALIZADA), any());
    }

    @Test
    void metadeDaDuracaoDeHabitoDeveCreditarMetadeDoXpMedia() {
        UUID usuarioId = UUID.randomUUID();
        UUID execucaoId = UUID.randomUUID();
        Usuario usuario = new Usuario(usuarioId, "ana@email.com", "hash", "Ana", AGORA, AGORA);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, execucaoId))
                .thenReturn(false);

        var resultado = service.concederBlocoHabito(usuarioId, execucaoId, 30);

        assertThat(resultado.ganhoNesteRequest()).isEqualTo(45);
        assertThat(usuario.getXpTotal()).isEqualTo(45);
    }
}
