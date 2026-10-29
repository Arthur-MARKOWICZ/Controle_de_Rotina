package com.controlerotina.xp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.shared.exception.XpEventoDuplicadoException;
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
            int minutosEfetivosBloco,
            int somaMinutosEfetivosTarefa,
            Tarefa tarefa
    ) {
        // Lock da linha do usuário antes de checar eventos/somar: serializa concessões concorrentes.
        Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId).orElseThrow();
        List<XpEvento> novos = new ArrayList<>();
        Instant agora = clock.instant();

        int xpBloco = XpMotor.xpBloco(minutosEfetivosBloco, tarefa.getPrioridade());
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
            int bonus = XpMotor.bonusTarefa(somaMinutosEfetivosTarefa, tarefa.getPrioridade());
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
            int xpTotal = usuario.getXpTotal();
            XpMotor.ProgressoXp progresso = XpMotor.progresso(xpTotal);
            return new ResultadoConcessao(0, List.of(), xpTotal, progresso);
        }

        try {
            xpEventoRepository.saveAll(novos);
            xpEventoRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // Rede de segurança: unique uk_xp_evento_tipo_origem → não duplica (409).
            if (violaUniqueXpEvento(ex)) {
                throw new XpEventoDuplicadoException();
            }
            throw ex;
        }

        int ganho = novos.stream().mapToInt(XpEvento::getQuantidade).sum();
        int xpTotal = usuario.getXpTotal() + ganho;
        XpMotor.ProgressoXp progresso = XpMotor.progresso(xpTotal);
        usuario.atualizarProgressoXp(xpTotal, progresso.nivel(), agora);
        usuarioRepository.save(usuario);

        return new ResultadoConcessao(ganho, List.copyOf(novos), xpTotal, progresso);
    }

    private static boolean violaUniqueXpEvento(DataIntegrityViolationException ex) {
        String mensagem = mensagemCompleta(ex).toLowerCase();
        return mensagem.contains("uk_xp_evento_tipo_origem");
    }

    private static String mensagemCompleta(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable atual = ex;
        while (atual != null) {
            if (atual.getMessage() != null) {
                sb.append(' ').append(atual.getMessage());
            }
            atual = atual.getCause();
        }
        return sb.toString();
    }

    public record ResultadoConcessao(
            int ganhoNesteRequest,
            List<XpEvento> eventos,
            int xpTotal,
            XpMotor.ProgressoXp progresso
    ) {
    }
}
