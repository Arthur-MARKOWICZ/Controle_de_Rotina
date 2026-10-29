package com.controlerotina.tarefa.job;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    }

    @Test
    void falhaNoServicoNaoPropaga() {
        doThrow(new RuntimeException("falha no batch")).when(tarefaService).processarAtrasos();

        job.processarAtrasos();

        verify(tarefaService).processarAtrasos();
    }
}
