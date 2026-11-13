package com.controlerotina.shared.ordem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.shared.exception.ReordenacaoInvalidaException;

class ReordenacaoTest {

    record Item(UUID id, String nome) {
    }

    @Test
    void reordenaSubconjuntoMantendoOsDemaisNasMesmasPosicoes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        List<Item> atuais = List.of(
                new Item(a, "A"),
                new Item(b, "B"),
                new Item(c, "C"),
                new Item(d, "D")
        );

        List<Item> resultado = Reordenacao.aplicar(atuais, List.of(c, a), Item::id);

        assertThat(resultado).extracting(Item::nome).containsExactly("C", "B", "A", "D");
    }

    @Test
    void reordenaListaInteira() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<Item> atuais = List.of(new Item(a, "A"), new Item(b, "B"), new Item(c, "C"));

        List<Item> resultado = Reordenacao.aplicar(atuais, List.of(c, a, b), Item::id);

        assertThat(resultado).extracting(Item::id).containsExactly(c, a, b);
    }

    @Test
    void rejeitaIdForaDoGrupo() {
        UUID a = UUID.randomUUID();
        List<Item> atuais = List.of(new Item(a, "A"));

        assertThatThrownBy(() -> Reordenacao.aplicar(atuais, List.of(UUID.randomUUID()), Item::id))
                .isInstanceOf(ReordenacaoInvalidaException.class);
    }

    @Test
    void rejeitaDuplicatas() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Item> atuais = List.of(new Item(a, "A"), new Item(b, "B"));

        assertThatThrownBy(() -> Reordenacao.aplicar(atuais, List.of(a, a), Item::id))
                .isInstanceOf(ReordenacaoInvalidaException.class)
                .satisfies(ex -> {
                    ReordenacaoInvalidaException erro = (ReordenacaoInvalidaException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(400);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Reordenação inválida");
                });
        assertThat(atuais).extracting(Item::nome).containsExactly("A", "B");
    }

    @Test
    void listaVaziaDeIdsNaoDeveAlterarAtuais() {
        UUID a = UUID.randomUUID();
        List<Item> atuais = List.of(new Item(a, "A"));

        assertThatThrownBy(() -> Reordenacao.aplicar(atuais, List.of(), Item::id))
                .isInstanceOf(ReordenacaoInvalidaException.class)
                .satisfies(ex -> {
                    ReordenacaoInvalidaException erro = (ReordenacaoInvalidaException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(400);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Reordenação inválida");
                });
        assertThat(atuais).extracting(Item::nome).containsExactly("A");
    }

    @Test
    void idsNulosDevemLancarSemMutarLista() {
        UUID a = UUID.randomUUID();
        List<Item> atuais = List.of(new Item(a, "A"));

        assertThatThrownBy(() -> Reordenacao.aplicar(atuais, null, Item::id))
                .isInstanceOf(ReordenacaoInvalidaException.class);
        assertThat(atuais).hasSize(1);
    }

    @Test
    void identidadePreservaOrdem() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Item> atuais = List.of(new Item(a, "A"), new Item(b, "B"));

        List<Item> resultado = Reordenacao.aplicar(atuais, List.of(a, b), Item::id);

        assertThat(resultado).extracting(Item::nome).containsExactly("A", "B");
        assertThat(atuais).extracting(Item::nome).containsExactly("A", "B");
    }
}
