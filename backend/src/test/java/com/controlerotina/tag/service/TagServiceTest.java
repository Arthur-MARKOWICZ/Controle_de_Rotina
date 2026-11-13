package com.controlerotina.tag.service;

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

import com.controlerotina.shared.exception.TagNomeDuplicadoException;
import com.controlerotina.shared.exception.TagNaoEncontradaException;
import com.controlerotina.tag.dto.AtualizarTagRequest;
import com.controlerotina.tag.dto.CriarTagRequest;
import com.controlerotina.tag.entity.Tag;
import com.controlerotina.tag.repository.DecidirTagRepository;
import com.controlerotina.tag.repository.HabitoTagRepository;
import com.controlerotina.tag.repository.ProjetoTagRepository;
import com.controlerotina.tag.repository.TagRepository;
import com.controlerotina.tag.repository.TarefaTagRepository;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TAG_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    TagRepository tagRepository;
    @Mock
    TarefaTagRepository tarefaTagRepository;
    @Mock
    HabitoTagRepository habitoTagRepository;
    @Mock
    ProjetoTagRepository projetoTagRepository;
    @Mock
    DecidirTagRepository decidirTagRepository;

    private TagService service;

    @BeforeEach
    void setUp() {
        service = new TagService(
                tagRepository,
                tarefaTagRepository,
                habitoTagRepository,
                projetoTagRepository,
                decidirTagRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void deveCriarTagComNomeNormalizado() {
        when(tagRepository.existsByUsuarioIdAndNomeNormalizado(USUARIO, "estudo")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        var criada = service.criar(USUARIO, new CriarTagRequest("Estudo", "#112233"));

        assertThat(criada.nome()).isEqualTo("Estudo");
        assertThat(criada.cor()).isEqualTo("#112233");
        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    void deveRejeitarNomeDuplicadoComCaseDiferente() {
        when(tagRepository.existsByUsuarioIdAndNomeNormalizado(USUARIO, "estudo")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(USUARIO, new CriarTagRequest("ESTUDO", null)))
                .isInstanceOf(TagNomeDuplicadoException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void deveRejeitarNomeDuplicadoComAcentoNaMesmaLetra() {
        when(tagRepository.existsByUsuarioIdAndNomeNormalizado(USUARIO, "ação")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(USUARIO, new CriarTagRequest("AÇÃO", null)))
                .isInstanceOf(TagNomeDuplicadoException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void deveListarVazioQuandoUsuarioNaoTemTags() {
        when(tagRepository.findByUsuarioIdOrderByNomeAsc(USUARIO)).thenReturn(List.of());

        assertThat(service.listar(USUARIO)).isEmpty();
    }

    @Test
    void deveRetornar404AoAtualizarTagDeOutroUsuario() {
        when(tagRepository.findByIdAndUsuarioId(TAG_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(USUARIO, TAG_ID, new AtualizarTagRequest("x", null)))
                .isInstanceOf(TagNaoEncontradaException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void deveExcluirTagRemovendoVinculos() {
        Tag tag = new Tag(TAG_ID, USUARIO, "estudo", "estudo", "#6B7280", AGORA, AGORA);
        when(tagRepository.findByIdAndUsuarioId(TAG_ID, USUARIO)).thenReturn(Optional.of(tag));

        service.excluir(USUARIO, TAG_ID);

        verify(tarefaTagRepository).deleteByTagId(TAG_ID);
        verify(habitoTagRepository).deleteByTagId(TAG_ID);
        verify(projetoTagRepository).deleteByTagId(TAG_ID);
        verify(decidirTagRepository).deleteByTagId(TAG_ID);
        verify(tagRepository).delete(tag);
    }

    @Test
    void naoDeveExcluirTagDeOutroUsuarioNemRemoverVinculos() {
        when(tagRepository.findByIdAndUsuarioId(TAG_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(USUARIO, TAG_ID))
                .isInstanceOf(TagNaoEncontradaException.class);
        verify(tarefaTagRepository, never()).deleteByTagId(any());
        verify(tagRepository, never()).delete(any());
    }
}
