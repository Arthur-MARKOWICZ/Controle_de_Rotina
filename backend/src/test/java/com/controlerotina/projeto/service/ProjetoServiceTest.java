package com.controlerotina.projeto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.controlerotina.decidir.repository.ItemDecidirRepository;
import com.controlerotina.pendencia.dto.EsperasDeOrigem;
import com.controlerotina.pendencia.service.EsperaService;
import com.controlerotina.projeto.dto.AtualizarProjetoRequest;
import com.controlerotina.projeto.dto.CriarProjetoRequest;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.ProjetoJaArquivadoException;
import com.controlerotina.shared.exception.ProjetoJaAtivoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;
import com.controlerotina.tag.service.TagVinculoService;

@ExtendWith(MockitoExtension.class)
class ProjetoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T15:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OUTRO = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PROJETO_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    ProjetoRepository projetoRepository;
    @Mock
    TagVinculoService tagVinculoService;
    @Mock
    ItemDecidirRepository itemDecidirRepository;
    @Mock
    EsperaService esperaService;

    private ProjetoService service;

    @BeforeEach
    void setUp() {
        service = new ProjetoService(
                projetoRepository,
                tagVinculoService,
                itemDecidirRepository,
                esperaService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveCriarProjetoAtivoComOrdemSeguinte() {
        when(projetoRepository.findMaxOrdem(USUARIO)).thenReturn(2);
        when(projetoRepository.save(any(Projeto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tagVinculoService.substituirTagsProjeto(eq(USUARIO), any(), any())).thenReturn(List.of());
        when(esperaService.enriquecerProjetos(eq(USUARIO), any())).thenReturn(EsperasDeOrigem.vazio());

        var criado = service.criar(USUARIO, new CriarProjetoRequest("Casa", "obras", List.of()));

        assertThat(criado.nome()).isEqualTo("Casa");
        assertThat(criado.status()).isEqualTo(StatusProjeto.ATIVO);
        assertThat(criado.ordem()).isEqualTo(3);
        verify(projetoRepository).save(any(Projeto.class));
        verify(projetoRepository).flush();
    }

    @Test
    void listarSemProjetosDevolvePaginaVazia() {
        when(projetoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(tagVinculoService.mapearTagsPorProjeto(any())).thenReturn(java.util.Map.of());
        when(esperaService.enriquecerProjetos(eq(USUARIO), any())).thenReturn(EsperasDeOrigem.vazio());

        Page<?> pagina = service.listar(USUARIO, StatusProjeto.ATIVO, List.of(), PageRequest.of(0, 20));

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
    }

    @Test
    void buscarProjetoDeOutroUsuarioNaoAlteraEstado() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(USUARIO, PROJETO_ID))
                .isInstanceOf(ProjetoNaoEncontradoException.class)
                .satisfies(ex -> {
                    ProjetoNaoEncontradoException erro = (ProjetoNaoEncontradoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Projeto não encontrado");
                });
        verify(projetoRepository, never()).save(any());
        verify(tagVinculoService, never()).listarTagsProjeto(any());
    }

    @Test
    void atualizarProjetoInexistenteNaoPersiste() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(
                USUARIO, PROJETO_ID, new AtualizarProjetoRequest("Novo", null, null, false)))
                .isInstanceOf(ProjetoNaoEncontradoException.class)
                .satisfies(ex -> {
                    ProjetoNaoEncontradoException erro = (ProjetoNaoEncontradoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Projeto não encontrado");
                });
        verify(projetoRepository, never()).save(any());
    }

    @Test
    void arquivarJaArquivadoLanca409SemApagarEsperas() {
        Projeto projeto = projeto(StatusProjeto.ARQUIVADO);
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO)).thenReturn(Optional.of(projeto));

        assertThatThrownBy(() -> service.arquivar(USUARIO, PROJETO_ID))
                .isInstanceOf(ProjetoJaArquivadoException.class)
                .satisfies(ex -> {
                    ProjetoJaArquivadoException erro = (ProjetoJaArquivadoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Projeto já arquivado");
                });
        verify(esperaService, never()).apagarPorRecurso(any(), any(), any());
        verify(itemDecidirRepository, never()).zerarProjetoId(any(), any());
    }

    @Test
    void reativarJaAtivoLanca409() {
        Projeto projeto = projeto(StatusProjeto.ATIVO);
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO)).thenReturn(Optional.of(projeto));

        assertThatThrownBy(() -> service.reativar(USUARIO, PROJETO_ID))
                .isInstanceOf(ProjetoJaAtivoException.class)
                .satisfies(ex -> {
                    ProjetoJaAtivoException erro = (ProjetoJaAtivoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Projeto já ativo");
                });
        assertThat(projeto.getStatus()).isEqualTo(StatusProjeto.ATIVO);
    }

    @Test
    void reordenarComIdDeOutroUsuarioNaoSalva() {
        Projeto doOutro = new Projeto(PROJETO_ID, OUTRO, "X", null, StatusProjeto.ATIVO, AGORA, AGORA);
        when(projetoRepository.findAllById(List.of(PROJETO_ID))).thenReturn(List.of(doOutro));

        assertThatThrownBy(() -> service.reordenar(USUARIO, List.of(PROJETO_ID)))
                .isInstanceOf(ProjetoNaoEncontradoException.class)
                .satisfies(ex -> {
                    ProjetoNaoEncontradoException erro = (ProjetoNaoEncontradoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Projeto não encontrado");
                });
        verify(projetoRepository, never()).saveAll(any());
    }

    private Projeto projeto(StatusProjeto status) {
        return new Projeto(PROJETO_ID, USUARIO, "Casa", "d", status, AGORA, AGORA);
    }
}
