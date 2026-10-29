package com.controlerotina.tarefa.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.tarefa.service.TarefaService;

@Component
public class TarefaAtrasoJob {

    private static final Logger log = LoggerFactory.getLogger(TarefaAtrasoJob.class);

    private final TarefaService tarefaService;

    public TarefaAtrasoJob(TarefaService tarefaService) {
        this.tarefaService = tarefaService;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = PeriodoBRT.ZONE_ID)
    public void processarAtrasos() {
        try {
            tarefaService.processarAtrasos();
        } catch (Exception ex) {
            log.error("Falha ao processar atrasos de tarefas", ex);
        }
    }
}
