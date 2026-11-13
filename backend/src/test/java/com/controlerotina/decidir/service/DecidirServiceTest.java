package com.controlerotina.decidir.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.controlerotina.decidir.dto.PromoverDecidirRequest;
import com.controlerotina.decidir.entity.ItemDecidir;
import com.controlerotina.decidir.repository.ItemDecidirRepository;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.ItemDecidirJaPromovidoException;
import com.controlerotina.shared.exception.ItemDecidirNaoEncontradoException;
import com.controlerotina.tag.service.TagVinculoService;
import com.controlerotina.tarefa.dto.TarefaResponse;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.service.TarefaService;

@ExtendWith(MockitoExtension.class)
class DecidirServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    ItemDecidirRepository itemDecidirRepository;
    @Mock
    ProjetoRepository projetoRepository;
    @Mock
    TagVinculoService tagVinculoService;
    @Mock
    TarefaService tarefaService;

    private DecidirService service;

    @BeforeEach
    void setUp() {
        service = new DecidirService(
                itemDecidirRepository,
                projetoRepository,
                tagVinculoService,
                tarefaService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void devePromoverItemAbertoCriandoTarefa() {
        ItemDecidir item = itemAberto();
        when(itemDecidirRepository.findByIdAndUsuarioId(ITEM_ID, USUARIO)).thenReturn(Optional.of(item));
        when(tagVinculoService.listarTagsDecidir(ITEM_ID)).thenReturn(List.of());
        Tarefa tarefa = new Tarefa(
                UUID.randomUUID(), USUARIO, "Comprar", "", Prioridade.MEDIA,
                null, StatusTarefa.PENDENTE, 60, AGORA, AGORA);
        when(tarefaService.criar(any(), any())).thenReturn(TarefaResponse.from(tarefa));
        when(itemDecidirRepository.save(item)).thenReturn(item);

        var response = service.promover(USUARIO, ITEM_ID, new PromoverDecidirRequest(null, null, null, null, null));

        assertThat(response.tarefaId()).isEqualTo(tarefa.getId());
        assertThat(item.isPromovido()).isTrue();
        verify(tarefaService).criar(any(), any());
        verify(itemDecidirRepository).save(item);
    }

    @Test
    void naoDeveCriarTarefaQuandoItemJaPromovido() {
        ItemDecidir item = itemAberto();
        item.promover(UUID.randomUUID(), AGORA);
        when(itemDecidirRepository.findByIdAndUsuarioId(ITEM_ID, USUARIO)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.promover(USUARIO, ITEM_ID, null))
                .isInstanceOf(ItemDecidirJaPromovidoException.class);
        verify(tarefaService, never()).criar(any(), any());
        verify(itemDecidirRepository, never()).save(any());
        assertThat(item.isPromovido()).isTrue();
    }

    @Test
    void naoDeveCriarTarefaQuandoItemDeOutroUsuario() {
        when(itemDecidirRepository.findByIdAndUsuarioId(ITEM_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promover(USUARIO, ITEM_ID, null))
                .isInstanceOf(ItemDecidirNaoEncontradoException.class);
        verify(tarefaService, never()).criar(any(), any());
        verify(itemDecidirRepository, never()).save(any());
    }

    @Test
    void naoDeveCriarTarefaQuandoItemInexistente() {
        when(itemDecidirRepository.findByIdAndUsuarioId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promover(USUARIO, UUID.randomUUID(), null))
                .isInstanceOf(ItemDecidirNaoEncontradoException.class);
        verify(tarefaService, never()).criar(any(), any());
    }

    @Test
    void deveRejeitarAtualizarItemJaPromovido() {
        ItemDecidir item = itemAberto();
        item.promover(UUID.randomUUID(), AGORA);
        when(itemDecidirRepository.findByIdAndUsuarioId(ITEM_ID, USUARIO)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.atualizar(
                USUARIO, ITEM_ID,
                new com.controlerotina.decidir.dto.AtualizarDecidirRequest("Novo", null, false, null, false, null, false, null, false)
        )).isInstanceOf(ItemDecidirJaPromovidoException.class);
        assertThat(item.getTitulo()).isEqualTo("Comprar");
    }

    @Test
    void deveExcluirItemDoUsuario() {
        ItemDecidir item = itemAberto();
        when(itemDecidirRepository.findByIdAndUsuarioId(ITEM_ID, USUARIO)).thenReturn(Optional.of(item));

        service.excluir(USUARIO, ITEM_ID);

        verify(itemDecidirRepository).delete(item);
    }

    @Test
    void naoDeveExcluirItemDeOutroUsuario() {
        when(itemDecidirRepository.findByIdAndUsuarioId(ITEM_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(USUARIO, ITEM_ID))
                .isInstanceOf(ItemDecidirNaoEncontradoException.class);
        verify(itemDecidirRepository, never()).delete(org.mockito.ArgumentMatchers.<ItemDecidir>any());
    }

    private static ItemDecidir itemAberto() {
        return new ItemDecidir(ITEM_ID, USUARIO, "Comprar", null, null, null, AGORA, AGORA);
    }
}
