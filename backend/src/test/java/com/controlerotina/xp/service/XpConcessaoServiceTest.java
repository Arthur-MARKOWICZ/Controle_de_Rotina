package com.controlerotina.xp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.controlerotina.shared.exception.XpEventoDuplicadoException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.repository.XpEventoRepository;

@ExtendWith(MockitoExtension.class)
class XpConcessaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final UUID USUARIO_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID EXECUCAO_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TAREFA_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    XpEventoRepository xpEventoRepository;

    @Mock
    UsuarioRepository usuarioRepository;

    private XpConcessaoService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new XpConcessaoService(
                xpEventoRepository,
                usuarioRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
        usuario = new Usuario(USUARIO_ID, "ana@email.com", "hash", "Ana", AGORA, AGORA);
    }

    @Test
    void deveConcederXpDoBlocoNoCaminhoFeliz() {
        when(usuarioRepository.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, EXECUCAO_ID))
                .thenReturn(false);

        var resultado = service.concederAoConcluir(USUARIO_ID, EXECUCAO_ID, 60, 60, tarefa(StatusTarefa.EM_PROGRESSO));

        assertThat(resultado.ganhoNesteRequest()).isEqualTo(90);
        assertThat(usuario.getXpTotal()).isEqualTo(90);
        verify(xpEventoRepository).saveAll(anyIterable());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void eventoDuplicadoDetectadoAntesDeSalvarNaoDeveAlterarSaldo() {
        usuario.atualizarProgressoXp(50, 1, AGORA);
        when(usuarioRepository.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, EXECUCAO_ID))
                .thenReturn(true);

        var resultado = service.concederAoConcluir(USUARIO_ID, EXECUCAO_ID, 60, 60, tarefa(StatusTarefa.EM_PROGRESSO));

        assertThat(resultado.ganhoNesteRequest()).isZero();
        assertThat(usuario.getXpTotal()).isEqualTo(50);
        verify(xpEventoRepository, never()).saveAll(anyIterable());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void uniqueNoBancoDeveLancarXpEventoDuplicadoExceptionSemCreditar() {
        when(usuarioRepository.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, EXECUCAO_ID))
                .thenReturn(false);
        when(xpEventoRepository.saveAll(anyIterable())).thenReturn(java.util.List.of());
        doThrow(new DataIntegrityViolationException("ERROR: duplicate key uk_xp_evento_tipo_origem"))
                .when(xpEventoRepository).flush();

        assertThatThrownBy(() -> service.concederAoConcluir(
                USUARIO_ID, EXECUCAO_ID, 60, 60, tarefa(StatusTarefa.EM_PROGRESSO)))
                .isInstanceOf(XpEventoDuplicadoException.class)
                .satisfies(ex -> {
                    XpEventoDuplicadoException erro = (XpEventoDuplicadoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Evento de XP já registrado");
                });
        assertThat(usuario.getXpTotal()).isZero();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void usuarioInexistenteNaoDevePersistirEvento() {
        when(usuarioRepository.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.concederAoConcluir(
                USUARIO_ID, EXECUCAO_ID, 60, 60, tarefa(StatusTarefa.EM_PROGRESSO)))
                .isInstanceOf(NoSuchElementException.class);
        verify(xpEventoRepository, never()).saveAll(anyIterable());
    }

    @Test
    void xpZeroNaoDeveCriarEventoNemAlterarSaldo() {
        when(usuarioRepository.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario));

        var resultado = service.concederAoConcluir(USUARIO_ID, EXECUCAO_ID, 0, 0, tarefa(StatusTarefa.EM_PROGRESSO));

        assertThat(resultado.ganhoNesteRequest()).isZero();
        assertThat(usuario.getXpTotal()).isZero();
        verify(xpEventoRepository, never()).existsByTipoAndOrigemId(any(), any());
        verify(xpEventoRepository, never()).saveAll(anyIterable());
    }

    @Test
    void xpNegativoNaoDeveCriarEvento() {
        when(usuarioRepository.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario));

        var resultado = service.concederAoConcluir(USUARIO_ID, EXECUCAO_ID, -30, 0, tarefa(StatusTarefa.EM_PROGRESSO));

        assertThat(resultado.ganhoNesteRequest()).isZero();
        assertThat(usuario.getXpTotal()).isZero();
        verify(xpEventoRepository, never()).saveAll(anyIterable());
    }

    @Test
    void tarefaFinalizadaDeveCreditarBonusAlemDoBloco() {
        when(usuarioRepository.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, EXECUCAO_ID))
                .thenReturn(false);
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.TAREFA_FINALIZADA, TAREFA_ID))
                .thenReturn(false);

        var resultado = service.concederAoConcluir(USUARIO_ID, EXECUCAO_ID, 60, 60, tarefa(StatusTarefa.FINALIZADA));

        assertThat(resultado.ganhoNesteRequest()).isEqualTo(99);
        assertThat(usuario.getXpTotal()).isEqualTo(99);
        verify(xpEventoRepository).saveAll(anyIterable());
    }

    @Test
    void violacaoDeIntegridadeNaoXpDevePropagar() {
        when(usuarioRepository.findByIdForUpdate(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, EXECUCAO_ID))
                .thenReturn(false);
        when(xpEventoRepository.saveAll(anyIterable())).thenReturn(java.util.List.of());
        doThrow(new DataIntegrityViolationException("uk_outra_constraint"))
                .when(xpEventoRepository).flush();

        assertThatThrownBy(() -> service.concederAoConcluir(
                USUARIO_ID, EXECUCAO_ID, 60, 60, tarefa(StatusTarefa.EM_PROGRESSO)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(usuario.getXpTotal()).isZero();
    }

    private static Tarefa tarefa(StatusTarefa status) {
        return new Tarefa(
                TAREFA_ID,
                USUARIO_ID,
                "Estudar",
                "desc",
                Prioridade.MEDIA,
                null,
                status,
                60,
                AGORA,
                AGORA
        );
    }
}
