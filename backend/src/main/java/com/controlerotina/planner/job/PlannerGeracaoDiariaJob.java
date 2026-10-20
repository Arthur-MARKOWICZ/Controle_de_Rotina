package com.controlerotina.planner.job;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@Component
public class PlannerGeracaoDiariaJob {

    static final ZoneId ZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static final Logger log = LoggerFactory.getLogger(PlannerGeracaoDiariaJob.class);

    private final UsuarioRepository usuarioRepository;
    private final PlanoRepository planoRepository;
    private final PlannerGeracaoService plannerGeracaoService;
    private final Clock clock;

    public PlannerGeracaoDiariaJob(
            UsuarioRepository usuarioRepository,
            PlanoRepository planoRepository,
            PlannerGeracaoService plannerGeracaoService,
            Clock clock
    ) {
        this.usuarioRepository = usuarioRepository;
        this.planoRepository = planoRepository;
        this.plannerGeracaoService = plannerGeracaoService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")
    public void gerarPlanosDoDia() {
        gerarPlanosPara(LocalDate.now(clock.withZone(ZONE_SAO_PAULO)));
    }

    public void gerarPlanosPara(LocalDate data) {
        List<Usuario> usuarios = usuarioRepository.findAll();
        for (Usuario usuario : usuarios) {
            try {
                if (planoRepository.existsByUsuarioIdAndDataAndStatus(
                        usuario.getId(), data, StatusPlano.APROVADO
                )) {
                    log.debug("Plano aprovado já existe para usuario {} na data {}; pulando",
                            usuario.getId(), data);
                    continue;
                }
                plannerGeracaoService.gerar(usuario.getId(), data);
            } catch (Exception ex) {
                log.warn("Falha ao gerar plano diário para usuario {} na data {}: {}",
                        usuario.getId(), data, ex.getMessage());
            }
        }
    }
}
