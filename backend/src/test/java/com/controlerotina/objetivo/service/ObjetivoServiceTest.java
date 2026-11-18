package com.controlerotina.objetivo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.ErrorResponseException;

import com.controlerotina.objetivo.dto.AtualizarObjetivoRequest;
import com.controlerotina.objetivo.dto.CriarObjetivoRequest;
import com.controlerotina.objetivo.dto.ObjetivoProgressoResponse;
import com.controlerotina.objetivo.dto.ObjetivoResponse;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.objetivo.repository.ObjetivoRepository;
import com.controlerotina.shared.exception.ObjetivoArquivadoException;
import com.controlerotina.shared.exception.ObjetivoJaArquivadoException;
import com.controlerotina.shared.exception.ObjetivoJaAtivoException;
import com.controlerotina.shared.exception.ObjetivoJaConcluidoException;
import com.controlerotina.shared.exception.ObjetivoNaoEncontradoException;
import com.controlerotina.shared.exception.PrazoNoPassadoException;

@ExtendWith(MockitoExtension.class)
class ObjetivoServiceTest {

    /** 17/11/2026 12:00 em BRT. */
    private static final Instant AGORA = Instant.parse("2026-11-17T15:00:00Z");
    /** 17/11/2026 23:30 em BRT, já 18/11 em UTC. */
    private static final Instant QUASE_MEIA_NOITE_BRT = Instant.parse("2026-11-18T02:30:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 17);
    private static final Instant ANTES = Instant.parse("2026-11-01T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OBJETIVO_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OBJETIVO_VAZIO_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock
    ObjetivoRepository objetivoRepository;
    @Mock
    ObjetivoVinculoService objetivoVinculoService;
    @Mock
    ObjetivoProgressoService objetivoProgressoService;

    private ObjetivoService service;

    @BeforeEach
    void setUp() {
        service = novoService(AGORA);
    }

    @Test
    void deveCriarObjetivoAtivoSemDatasDeEncerramento() {
        when(objetivoRepository.save(any(Objetivo.class))).thenAnswer(inv -> inv.getArgument(0));

        ObjetivoResponse criado = service.criar(USUARIO, new CriarObjetivoRequest("Perder 5 kg", null, HOJE));

        assertThat(criado.titulo()).isEqualTo("Perder 5 kg");
        assertThat(criado.status()).isEqualTo(StatusObjetivo.ATIVO);
        assertThat(criado.prazo()).isEqualTo(HOJE);
        assertThat(criado.concluidoEm()).isNull();
        assertThat(criado.arquivadoEm()).isNull();
        assertThat(criado.createdAt()).isEqualTo(AGORA);
        assertThat(criado.progresso()).isNull();
        verify(objetivoVinculoService, never()).resolverConjunto(any());
        verify(objetivoVinculoService, never()).resolverConjuntos(any());
        verify(objetivoProgressoService, never()).calcular(any(), any());
        verify(objetivoProgressoService, never()).calcularLote(any(), any());
    }

    @Test
    void deveRejeitarPrazoDeOntemNaCriacaoSemSalvar() {
        CriarObjetivoRequest request = new CriarObjetivoRequest("Perder 5 kg", null, HOJE.minusDays(1));

        assertErro(() -> service.criar(USUARIO, request), PrazoNoPassadoException.class, 400, "Prazo inválido");
        verify(objetivoRepository, never()).save(any());
    }

    @Test
    void deveAceitarPrazoDoDiaBrtMesmoComUtcJaNoDiaSeguinte() {
        service = novoService(QUASE_MEIA_NOITE_BRT);
        when(objetivoRepository.save(any(Objetivo.class))).thenAnswer(inv -> inv.getArgument(0));

        ObjetivoResponse criado = service.criar(USUARIO, new CriarObjetivoRequest("Entregar TCC", null, HOJE));

        assertThat(criado.prazo()).isEqualTo(HOJE);
    }

    @Test
    void deveRetornar404AoBuscarObjetivoDeOutroUsuario() {
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_ID, USUARIO)).thenReturn(Optional.empty());

        assertErro(() -> service.buscarPorId(USUARIO, OBJETIVO_ID),
                ObjetivoNaoEncontradoException.class, 404, "Objetivo não encontrado");
        verify(objetivoVinculoService, never()).resolverConjunto(any());
        verify(objetivoProgressoService, never()).calcular(any(), any());
    }

    @Test
    void deveBuscarDetalheComProgresso80() {
        Objetivo objetivo = objetivo(null);
        doUsuario(objetivo);
        ObjetivoConjunto conjunto = ObjetivoConjunto.vazio();
        when(objetivoVinculoService.resolverConjunto(OBJETIVO_ID)).thenReturn(conjunto);
        when(objetivoProgressoService.calcular(objetivo, conjunto))
                .thenReturn(new ObjetivoProgressoResponse(80, 120, 150));

        ObjetivoResponse detalhe = service.buscarPorId(USUARIO, OBJETIVO_ID);

        assertThat(detalhe.progresso()).isEqualTo(new ObjetivoProgressoResponse(80, 120, 150));
    }

    @Test
    void deveListarProgressoNuloECemPorCentoNaMesmaPagina() {
        Objetivo vazio = new Objetivo(OBJETIVO_VAZIO_ID, USUARIO, "Vazio", null, null, ANTES);
        Objetivo completo = objetivo(null);
        when(objetivoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(vazio, completo), PageRequest.of(0, 20), 2));
        ObjetivoConjunto conjuntoVazio = ObjetivoConjunto.vazio();
        ObjetivoConjunto conjuntoCompleto = ObjetivoConjunto.vazio();
        when(objetivoVinculoService.resolverConjuntos(List.of(OBJETIVO_VAZIO_ID, OBJETIVO_ID)))
                .thenReturn(Map.of(OBJETIVO_VAZIO_ID, conjuntoVazio, OBJETIVO_ID, conjuntoCompleto));
        Map<UUID, ObjetivoProgressoResponse> lote = new HashMap<>();
        lote.put(OBJETIVO_VAZIO_ID, null);
        lote.put(OBJETIVO_ID, new ObjetivoProgressoResponse(100, 60, 60));
        when(objetivoProgressoService.calcularLote(any(), any())).thenReturn(lote);

        var pagina = service.listar(USUARIO, StatusObjetivo.ATIVO, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getContent().get(0).progresso()).isNull();
        assertThat(pagina.getContent().get(1).progresso())
                .isEqualTo(new ObjetivoProgressoResponse(100, 60, 60));
        verify(objetivoProgressoService).calcularLote(any(), any());
        verify(objetivoProgressoService, never()).calcular(any(), any());
    }

    @Test
    void naoDeveConcluirObjetivoDeOutroUsuario() {
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_ID, USUARIO)).thenReturn(Optional.empty());

        assertErro(() -> service.concluir(USUARIO, OBJETIVO_ID),
                ObjetivoNaoEncontradoException.class, 404, "Objetivo não encontrado");
    }

    @Test
    void deveAceitarReenvioDoMesmoPrazoVencidoAoEditarTitulo() {
        Objetivo objetivo = objetivo(HOJE.minusDays(10));
        doUsuario(objetivo);

        ObjetivoResponse atualizado = service.atualizar(USUARIO, OBJETIVO_ID,
                new AtualizarObjetivoRequest("Título corrigido", null, HOJE.minusDays(10), true));

        assertThat(atualizado.titulo()).isEqualTo("Título corrigido");
        assertThat(atualizado.prazo()).isEqualTo(HOJE.minusDays(10));
    }

    @Test
    void deveRejeitarTrocaParaOutroPrazoNoPassadoSemAlterarObjetivo() {
        Objetivo objetivo = objetivo(HOJE.minusDays(10));
        doUsuario(objetivo);
        AtualizarObjetivoRequest request = new AtualizarObjetivoRequest("Outro", null, HOJE.minusDays(5), true);

        assertErro(() -> service.atualizar(USUARIO, OBJETIVO_ID, request),
                PrazoNoPassadoException.class, 400, "Prazo inválido");
        assertThat(objetivo.getPrazo()).isEqualTo(HOJE.minusDays(10));
        assertThat(objetivo.getTitulo()).isEqualTo("Perder 5 kg");
    }

    @Test
    void deveRemoverPrazoComNuloExplicito() {
        Objetivo objetivo = objetivo(HOJE.plusDays(30));
        doUsuario(objetivo);

        ObjetivoResponse atualizado = service.atualizar(USUARIO, OBJETIVO_ID,
                new AtualizarObjetivoRequest(null, null, null, true));

        assertThat(atualizado.prazo()).isNull();
    }

    @Test
    void deveEditarObjetivoConcluidoSemMudarStatusNemData() {
        Objetivo objetivo = objetivo(null);
        objetivo.concluir(ANTES);
        doUsuario(objetivo);

        ObjetivoResponse atualizado = service.atualizar(USUARIO, OBJETIVO_ID,
                new AtualizarObjetivoRequest("Meta batida", null, null, false));

        assertThat(atualizado.titulo()).isEqualTo("Meta batida");
        assertThat(atualizado.status()).isEqualTo(StatusObjetivo.CONCLUIDO);
        assertThat(atualizado.concluidoEm()).isEqualTo(ANTES);
    }

    @Test
    void deveConcluirObjetivoAtivoComInstanteAtual() {
        Objetivo objetivo = objetivo(null);
        doUsuario(objetivo);

        ObjetivoResponse concluido = service.concluir(USUARIO, OBJETIVO_ID);

        assertThat(concluido.status()).isEqualTo(StatusObjetivo.CONCLUIDO);
        assertThat(concluido.concluidoEm()).isEqualTo(AGORA);
    }

    @Test
    void naoDeveConcluirDuasVezesNemMudarConcluidoEm() {
        Objetivo objetivo = objetivo(null);
        objetivo.concluir(ANTES);
        doUsuario(objetivo);

        assertErro(() -> service.concluir(USUARIO, OBJETIVO_ID),
                ObjetivoJaConcluidoException.class, 409, "Objetivo já concluído");
        assertThat(objetivo.getConcluidoEm()).isEqualTo(ANTES);
    }

    @Test
    void deveRetornar409AoConcluirObjetivoArquivado() {
        Objetivo objetivo = objetivo(null);
        objetivo.arquivar(ANTES);
        doUsuario(objetivo);

        assertErro(() -> service.concluir(USUARIO, OBJETIVO_ID),
                ObjetivoArquivadoException.class, 409, "Objetivo arquivado");
        assertThat(objetivo.getStatus()).isEqualTo(StatusObjetivo.ARQUIVADO);
        assertThat(objetivo.getConcluidoEm()).isNull();
        assertThat(objetivo.getArquivadoEm()).isEqualTo(ANTES);
    }

    @Test
    void deveArquivarConcluidoMantendoConcluidoEm() {
        Objetivo objetivo = objetivo(null);
        objetivo.concluir(ANTES);
        doUsuario(objetivo);

        ObjetivoResponse arquivado = service.arquivar(USUARIO, OBJETIVO_ID);

        assertThat(arquivado.status()).isEqualTo(StatusObjetivo.ARQUIVADO);
        assertThat(arquivado.concluidoEm()).isEqualTo(ANTES);
        assertThat(arquivado.arquivadoEm()).isEqualTo(AGORA);
    }

    @Test
    void naoDeveArquivarDuasVezesNemMudarArquivadoEm() {
        Objetivo objetivo = objetivo(null);
        objetivo.arquivar(ANTES);
        doUsuario(objetivo);

        assertErro(() -> service.arquivar(USUARIO, OBJETIVO_ID),
                ObjetivoJaArquivadoException.class, 409, "Objetivo já arquivado");
        assertThat(objetivo.getArquivadoEm()).isEqualTo(ANTES);
    }

    @Test
    void deveReabrirArquivadoQueTinhaSidoConcluidoLimpandoAsDatas() {
        Objetivo objetivo = objetivo(null);
        objetivo.concluir(ANTES);
        objetivo.arquivar(ANTES);
        doUsuario(objetivo);

        ObjetivoResponse reaberto = service.reabrir(USUARIO, OBJETIVO_ID);

        assertThat(reaberto.status()).isEqualTo(StatusObjetivo.ATIVO);
        assertThat(reaberto.concluidoEm()).isNull();
        assertThat(reaberto.arquivadoEm()).isNull();
    }

    @Test
    void deveRetornar409AoReabrirObjetivoAtivo() {
        Objetivo objetivo = objetivo(null);
        doUsuario(objetivo);

        assertErro(() -> service.reabrir(USUARIO, OBJETIVO_ID),
                ObjetivoJaAtivoException.class, 409, "Objetivo já ativo");
        assertThat(objetivo.getUpdatedAt()).isEqualTo(ANTES);
    }

    @Test
    void listarSemStatusUsaAtivoEOrdemPorPrazoComNulosNoFim() {
        Pageable aplicado = listarECapturar(null, PageRequest.of(0, 20));

        assertThat(aplicado.getSort().getOrderFor("prazo"))
                .isEqualTo(Sort.Order.asc("prazo").nullsLast());
        assertThat(aplicado.getSort().toList())
                .extracting(Sort.Order::getProperty)
                .containsExactly("prazo", "createdAt");
    }

    @Test
    void listarConcluidosOrdenaPorConclusaoMaisRecente() {
        Pageable aplicado = listarECapturar(StatusObjetivo.CONCLUIDO, PageRequest.of(0, 20));

        assertThat(aplicado.getSort().toList())
                .containsExactly(Sort.Order.desc("concluidoEm"), Sort.Order.desc("createdAt"));
    }

    @Test
    void listarArquivadosOrdenaPorArquivamentoMaisRecente() {
        Pageable aplicado = listarECapturar(StatusObjetivo.ARQUIVADO, PageRequest.of(1, 5));

        assertThat(aplicado.getSort().toList())
                .containsExactly(Sort.Order.desc("arquivadoEm"), Sort.Order.desc("createdAt"));
        assertThat(aplicado.getPageNumber()).isEqualTo(1);
        assertThat(aplicado.getPageSize()).isEqualTo(5);
    }

    @Test
    void listarPreservaOrdenacaoInformadaPeloCliente() {
        PageRequest doCliente = PageRequest.of(0, 20, Sort.by("titulo"));

        Pageable aplicado = listarECapturar(StatusObjetivo.ATIVO, doCliente);

        assertThat(aplicado.getSort()).isEqualTo(Sort.by("titulo"));
    }

    @Test
    void listarSemObjetivosDevolvePaginaVazia() {
        when(objetivoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var pagina = service.listar(USUARIO, StatusObjetivo.ARQUIVADO, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
        verify(objetivoVinculoService, never()).resolverConjuntos(any());
        verify(objetivoProgressoService, never()).calcularLote(any(), any());
    }

    private ObjetivoService novoService(Instant instante) {
        return new ObjetivoService(
                objetivoRepository,
                objetivoVinculoService,
                objetivoProgressoService,
                Clock.fixed(instante, ZoneOffset.UTC)
        );
    }

    @SuppressWarnings("unchecked")
    private Pageable listarECapturar(StatusObjetivo status, Pageable pageable) {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(objetivoRepository.findAll(any(Specification.class), captor.capture()))
                .thenReturn(new PageImpl<>(List.of()));
        service.listar(USUARIO, status, pageable);
        return captor.getValue();
    }

    private void doUsuario(Objetivo objetivo) {
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_ID, USUARIO)).thenReturn(Optional.of(objetivo));
    }

    private static Objetivo objetivo(LocalDate prazo) {
        return new Objetivo(OBJETIVO_ID, USUARIO, "Perder 5 kg", null, prazo, ANTES);
    }

    private static void assertErro(
            ThrowingCallable chamada,
            Class<? extends ErrorResponseException> tipo,
            int status,
            String title
    ) {
        assertThatThrownBy(chamada)
                .isInstanceOf(tipo)
                .satisfies(ex -> {
                    ErrorResponseException erro = (ErrorResponseException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(status);
                    assertThat(erro.getBody().getTitle()).isEqualTo(title);
                });
    }
}
