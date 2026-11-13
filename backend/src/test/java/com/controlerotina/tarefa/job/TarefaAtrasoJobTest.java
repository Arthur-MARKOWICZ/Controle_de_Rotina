package com.controlerotina.tarefa.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.controlerotina.tarefa.service.TarefaService;

@ExtendWith(MockitoExtension.class)
class TarefaAtrasoJobTest {

    @Mock
    TarefaService tarefaService;

    @InjectMocks
    TarefaAtrasoJob job;

    @Test
    void deveDelegarProcessamentoAoServico() {
        job.processarAtrasos();

        verify(tarefaService).processarAtrasos();
        verifyNoMoreInteractions(tarefaService);
    }

    @Test
    void falhaNoServicoNaoPropaga() {
        doThrow(new RuntimeException("falha no batch")).when(tarefaService).processarAtrasos();

        assertThatCode(() -> job.processarAtrasos()).doesNotThrowAnyException();
        verify(tarefaService).processarAtrasos();
    }

    @Test
    void errorResponseExceptionNaoPropaga() {
        doThrow(new ErrorResponseException(HttpStatus.CONFLICT)).when(tarefaService).processarAtrasos();

        assertThatCode(() -> job.processarAtrasos()).doesNotThrowAnyException();
        verify(tarefaService).processarAtrasos();
    }

    @Test
    void npeNoServicoNaoPropaga() {
        doThrow(new NullPointerException("repo nulo")).when(tarefaService).processarAtrasos();

        assertThatCode(() -> job.processarAtrasos()).doesNotThrowAnyException();
        verify(tarefaService).processarAtrasos();
    }

    @Test
    void illegalStateNaoPropaga() {
        doThrow(new IllegalStateException("clock")).when(tarefaService).processarAtrasos();

        assertThatCode(() -> job.processarAtrasos()).doesNotThrowAnyException();
        verify(tarefaService).processarAtrasos();
    }

    @Test
    void umaChamadaNaoDisparaOServicoDuasVezes() {
        job.processarAtrasos();

        verify(tarefaService, times(1)).processarAtrasos();
    }

    @Test
    void recuperaAposFalhaNaChamadaAnterior() {
        doThrow(new RuntimeException("falha")).doNothing().when(tarefaService).processarAtrasos();

        assertThatCode(() -> job.processarAtrasos()).doesNotThrowAnyException();
        job.processarAtrasos();

        verify(tarefaService, times(2)).processarAtrasos();
    }
}
