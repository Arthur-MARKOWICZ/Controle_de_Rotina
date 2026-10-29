package com.controlerotina.planner.job;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.planner.service.PlannerGeracaoService;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.shared.lock.PostgresAdvisoryLock;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@Component
public class PlannerGeracaoDiariaJob {

    /** Chave estável do advisory lock da geração diária (evita duas instâncias na mesma rodada). */
    static final long LOCK_GERACAO_DIARIA = 419_001_906L;

    static final int TAMANHO_PAGINA = 100;

    private static final Logger log = LoggerFactory.getLogger(PlannerGeracaoDiariaJob.class);

    private final UsuarioRepository usuarioRepository;
    private final PlanoRepository planoRepository;
    private final PlannerGeracaoService plannerGeracaoService;
    private final PropostaGeracaoService propostaGeracaoService;
    private final PostgresAdvisoryLock advisoryLock;
    private final Clock clock;

    public PlannerGeracaoDiariaJob(
            UsuarioRepository usuarioRepository,
            PlanoRepository planoRepository,
            PlannerGeracaoService plannerGeracaoService,
            PropostaGeracaoService propostaGeracaoService,
            PostgresAdvisoryLock advisoryLock,
            Clock clock
    ) {
        this.usuarioRepository = usuarioRepository;
        this.planoRepository = planoRepository;
        this.plannerGeracaoService = plannerGeracaoService;
        this.propostaGeracaoService = propostaGeracaoService;
        this.advisoryLock = advisoryLock;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = PeriodoBRT.ZONE_ID)
    public void gerarPlanosDoDia() {
        gerarPlanosPara(LocalDate.now(clock.withZone(PeriodoBRT.ZONE)));
    }

    public void gerarPlanosPara(LocalDate data) {
        boolean executou = advisoryLock.tryWithLock(LOCK_GERACAO_DIARIA, () -> processarUsuarios(data));
        if (!executou) {
            log.info("Geração diária já em execução em outra instância; saindo sem gerar de novo");
        }
    }

    private void processarUsuarios(LocalDate data) {
        int pagina = 0;
        Page<Usuario> usuarios;
        do {
            usuarios = usuarioRepository.findAll(PageRequest.of(pagina, TAMANHO_PAGINA));
            for (Usuario usuario : usuarios) {
                processarUsuario(usuario, data);
            }
            pagina++;
        } while (usuarios.hasNext());
    }

    private void processarUsuario(Usuario usuario, LocalDate data) {
        try {
            if (planoRepository.existsByUsuarioIdAndDataAndStatus(
                    usuario.getId(), data, StatusPlano.APROVADO
            )) {
                boolean gerou = propostaGeracaoService.gerarSeDiaAnteriorIncompleto(
                        usuario.getId(), data);
                if (!gerou) {
                    log.debug("Plano aprovado para usuario {} na data {} sem gatilho de proposta; pulando",
                            usuario.getId(), data);
                }
                return;
            }
            plannerGeracaoService.gerar(usuario.getId(), data);
        } catch (Exception ex) {
            log.error("Falha ao gerar plano diário para usuario {} na data {}",
                    usuario.getId(), data, ex);
        }
    }
}
