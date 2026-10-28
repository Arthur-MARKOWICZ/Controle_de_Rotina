package com.controlerotina.xp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;
import com.controlerotina.xp.XpMotor;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.entity.XpEvento;
import com.controlerotina.xp.repository.XpEventoRepository;

@Service
public class XpConcessaoService {

    private final XpEventoRepository xpEventoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public XpConcessaoService(
            XpEventoRepository xpEventoRepository,
            UsuarioRepository usuarioRepository,
            Clock clock
    ) {
        this.xpEventoRepository = xpEventoRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional
    public ResultadoConcessao concederAoConcluir(
            UUID usuarioId,
            UUID execucaoId,
            int duracaoMinutosBloco,
            Tarefa tarefa
    ) {
        List<XpEvento> novos = new ArrayList<>();
        Instant agora = clock.instant();

        int xpBloco = XpMotor.xpBloco(duracaoMinutosBloco, tarefa.getPrioridade());
        if (xpBloco > 0 && !xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.BLOCO_CONCLUIDO, execucaoId)) {
            novos.add(new XpEvento(
                    UUID.randomUUID(),
                    usuarioId,
                    TipoXpEvento.BLOCO_CONCLUIDO,
                    execucaoId,
                    xpBloco,
                    agora
            ));
        }

        if (tarefa.getStatus() == StatusTarefa.FINALIZADA
                && !xpEventoRepository.existsByTipoAndOrigemId(TipoXpEvento.TAREFA_FINALIZADA, tarefa.getId())) {
            int bonus = XpMotor.bonusTarefa(tarefa.getDuracaoHoras(), tarefa.getPrioridade());
            if (bonus > 0) {
                novos.add(new XpEvento(
                        UUID.randomUUID(),
                        usuarioId,
                        TipoXpEvento.TAREFA_FINALIZADA,
                        tarefa.getId(),
                        bonus,
                        agora
                ));
            }
        }

        if (novos.isEmpty()) {
            Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
            int xpTotal = usuario.getXpTotal();
            XpMotor.ProgressoXp progresso = XpMotor.progresso(xpTotal);
            return new ResultadoConcessao(0, List.of(), xpTotal, progresso);
        }

        xpEventoRepository.saveAll(novos);

        int ganho = novos.stream().mapToInt(XpEvento::getQuantidade).sum();
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        int xpTotal = usuario.getXpTotal() + ganho;
        XpMotor.ProgressoXp progresso = XpMotor.progresso(xpTotal);
        usuario.atualizarProgressoXp(xpTotal, progresso.nivel(), agora);
        usuarioRepository.save(usuario);

        return new ResultadoConcessao(ganho, List.copyOf(novos), xpTotal, progresso);
    }

    public record ResultadoConcessao(
            int ganhoNesteRequest,
            List<XpEvento> eventos,
            int xpTotal,
            XpMotor.ProgressoXp progresso
    ) {
    }
}
