package com.controlerotina.planner.motor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.tarefa.entity.Tarefa;

public final class MotorAlocacao {

    public static final String MENSAGEM_SEM_TEMPO = "Sem tempo neste dia. Nada planejado.";
    public static final String MENSAGEM_PARCIAL =
            "Capacidade insuficiente: ao menos uma tarefa foi alocada parcialmente.";

    private static final DateTimeFormatter PRAZO_MSG = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Teto da janela: hoje + 13 = 14 dias. */
    public static final int TETO_JANELA_DIAS = 14;

    /** Teto de um bloco alocado (minutos). */
    public static final int TETO_BLOCO_MINUTOS = 90;

    /** Piso de um bloco; saldo restante da tarefa &lt; piso pode gerar bloco menor. */
    public static final int PISO_BLOCO_MINUTOS = 30;

    private MotorAlocacao() {
    }

    public record ReservaPedido(UUID projetoId, int minutosReservados) {
    }

    public record DiaCapacidade(LocalDate data, int capacidadeMinutos, List<ReservaPedido> reservas) {
        public DiaCapacidade(LocalDate data, int capacidadeMinutos) {
            this(data, capacidadeMinutos, List.of());
        }

        public DiaCapacidade {
            reservas = reservas == null ? List.of() : List.copyOf(reservas);
        }
    }

    /**
     * Saldo a planejar: estimativa − minutos já concluídos.
     * Abortados e blocos sem {@code CONCLUIDO} não entram em {@code minutosConcluidos}.
     */
    public static int calcularSaldoMinutos(int duracaoEstimadaMinutos, long minutosConcluidos) {
        long saldo = (long) duracaoEstimadaMinutos - minutosConcluidos;
        if (saldo <= 0) {
            return 0;
        }
        return saldo > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) saldo;
    }

    /**
     * Quanto alocar no próximo bloco, ou {@code null} se não deve abrir bloco.
     * Capacidade restante &lt; 30 → não abre. Teto 90. Piso 30, exceto saldo da tarefa &lt; 30.
     */
    public static Integer minutosDoProximoBloco(int saldoTarefa, int capacidadeRestante) {
        if (saldoTarefa <= 0 || capacidadeRestante < PISO_BLOCO_MINUTOS) {
            return null;
        }
        int usar = Math.min(saldoTarefa, Math.min(capacidadeRestante, TETO_BLOCO_MINUTOS));
        if (usar < PISO_BLOCO_MINUTOS) {
            if (saldoTarefa < PISO_BLOCO_MINUTOS && saldoTarefa <= capacidadeRestante) {
                return saldoTarefa;
            }
            return null;
        }
        return usar;
    }

    /** Compatível com estimativa cheia (sem execuções concluídas). */
    public static ResultadoAlocacao alocar(List<Tarefa> elegiveis, int capacidadeMinutos, LocalDate diaD) {
        return alocar(elegiveis, capacidadeMinutos, diaD, List.of());
    }

    public static ResultadoAlocacao alocar(
            List<Tarefa> elegiveis,
            int capacidadeMinutos,
            LocalDate diaD,
            List<ReservaPedido> reservas
    ) {
        Map<UUID, Integer> saldoPorTarefa = new HashMap<>();
        for (Tarefa tarefa : elegiveis) {
            saldoPorTarefa.put(tarefa.getId(), tarefa.getDuracaoMinutos());
        }
        return alocar(elegiveis, saldoPorTarefa, capacidadeMinutos, diaD, reservas);
    }

    public static ResultadoAlocacao alocar(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoMinutosPorTarefa,
            int capacidadeMinutos,
            LocalDate diaD
    ) {
        return alocar(elegiveis, saldoMinutosPorTarefa, capacidadeMinutos, diaD, List.of());
    }

    public static ResultadoAlocacao alocar(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoMinutosPorTarefa,
            int capacidadeMinutos,
            LocalDate diaD,
            List<ReservaPedido> reservas
    ) {
        if (capacidadeMinutos <= 0) {
            return new ResultadoAlocacao(List.of(), false, MENSAGEM_SEM_TEMPO);
        }

        Map<UUID, Integer> saldoRestante = new HashMap<>(saldoMinutosPorTarefa);
        List<BlocoAlocado> blocos = alocarDia(
                elegiveis, saldoRestante, capacidadeMinutos, diaD, false, reservas);

        boolean temConflito = elegiveis.stream()
                .anyMatch(t -> saldoRestante.getOrDefault(t.getId(), 0) > 0
                        && saldoMinutosPorTarefa.getOrDefault(t.getId(), 0) > 0);
        if (temConflito && !blocos.isEmpty()) {
            BlocoAlocado ultimo = blocos.get(blocos.size() - 1);
            if (saldoRestante.getOrDefault(ultimo.tarefaId(), 0) > 0) {
                blocos.set(blocos.size() - 1, new BlocoAlocado(
                        ultimo.tarefaId(),
                        ultimo.duracaoMinutos(),
                        ultimo.ordem(),
                        true
                ));
            }
        }

        String mensagem = temConflito ? MENSAGEM_PARCIAL : null;
        return new ResultadoAlocacao(List.copyOf(blocos), temConflito, mensagem);
    }

    /**
     * Fim da janela: {@code min(inicio + 13, max(prazo BRT das elegíveis com prazo))}.
     * Sem elegíveis com prazo, a janela é só o dia {@code inicio}.
     */
    public static LocalDate fimJanela(LocalDate inicio, List<Tarefa> elegiveis) {
        LocalDate teto = inicio.plusDays(TETO_JANELA_DIAS - 1L);
        LocalDate maxPrazo = elegiveis.stream()
                .filter(t -> t.getPrazo() != null)
                .map(MotorAlocacao::prazoEmDiaLocal)
                .max(LocalDate::compareTo)
                .orElse(inicio);
        LocalDate fim = maxPrazo.isBefore(inicio) ? inicio : maxPrazo;
        return fim.isAfter(teto) ? teto : fim;
    }

    /**
     * Distribui o saldo das tarefas pelos dias da janela.
     * Capacidade 0 → sem blocos; não aloca após o prazo; soma na janela ≤ saldo.
     * Blocos ≤ 90; piso 30 (exceto saldo &lt; 30); capacidade restante &lt; 30 não vira bloco.
     * Pós-alocação: marca conflito quando tarefa com prazo na janela ainda tem saldo.
     */
    public static Map<LocalDate, ResultadoAlocacao> alocarJanela(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoMinutosPorTarefa,
            List<DiaCapacidade> dias
    ) {
        Map<UUID, Integer> saldoRestante = new HashMap<>(saldoMinutosPorTarefa);
        Map<LocalDate, ResultadoAlocacao> porDia = new LinkedHashMap<>();

        for (DiaCapacidade dia : dias) {
            if (dia.capacidadeMinutos() <= 0) {
                porDia.put(dia.data(), new ResultadoAlocacao(List.of(), false, MENSAGEM_SEM_TEMPO));
                continue;
            }

            List<BlocoAlocado> blocos = alocarDia(
                    elegiveis,
                    saldoRestante,
                    dia.capacidadeMinutos(),
                    dia.data(),
                    true,
                    dia.reservas()
            );
            porDia.put(dia.data(), new ResultadoAlocacao(List.copyOf(blocos), false, null));
        }

        return aplicarConflitosDePrazo(porDia, elegiveis, saldoRestante, dias);
    }

    /**
     * Distribui o teto efetivo entre projetos: ordem por {@code minutosReservados} desc,
     * empate {@code projetoId} asc; cada um recebe {@code min(pedido, restanteDoTetoEfetivo)}.
     */
    static List<ReservaPedido> fatiasEfetivas(List<ReservaPedido> pedidos, int capacidadeMinutos) {
        if (pedidos == null || pedidos.isEmpty() || capacidadeMinutos <= 0) {
            return List.of();
        }
        List<ReservaPedido> comPedido = pedidos.stream()
                .filter(r -> r.minutosReservados() > 0)
                .sorted(Comparator
                        .comparingInt(ReservaPedido::minutosReservados).reversed()
                        .thenComparing(ReservaPedido::projetoId))
                .toList();
        int somaPedida = comPedido.stream().mapToInt(ReservaPedido::minutosReservados).sum();
        int tetoEfetivo = Math.min(somaPedida, capacidadeMinutos);
        List<ReservaPedido> efetivas = new ArrayList<>();
        int restante = tetoEfetivo;
        for (ReservaPedido pedido : comPedido) {
            if (restante <= 0) {
                break;
            }
            int fatia = Math.min(pedido.minutosReservados(), restante);
            if (fatia > 0) {
                efetivas.add(new ReservaPedido(pedido.projetoId(), fatia));
                restante -= fatia;
            }
        }
        return List.copyOf(efetivas);
    }

    private static List<BlocoAlocado> alocarDia(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoRestante,
            int capacidadeMinutos,
            LocalDate dia,
            boolean filtrarPrazo,
            List<ReservaPedido> reservas
    ) {
        List<BlocoAlocado> blocos = new ArrayList<>();
        int ordem = 1;

        for (ReservaPedido fatia : fatiasEfetivas(reservas, capacidadeMinutos)) {
            List<Tarefa> doProjeto = elegiveis.stream()
                    .filter(t -> fatia.projetoId().equals(t.getProjetoId()))
                    .toList();
            ordem = alocarComPrazoDepoisSemPrazo(
                    doProjeto, saldoRestante, fatia.minutosReservados(), dia, filtrarPrazo, blocos, ordem);
        }

        int poolLivre = capacidadeMinutos - minutosAlocados(blocos);
        alocarComPrazoDepoisSemPrazo(
                elegiveis, saldoRestante, poolLivre, dia, filtrarPrazo, blocos, ordem);
        return blocos;
    }

    private static int alocarComPrazoDepoisSemPrazo(
            List<Tarefa> candidatas,
            Map<UUID, Integer> saldoRestante,
            int capacidade,
            LocalDate dia,
            boolean filtrarPrazo,
            List<BlocoAlocado> blocos,
            int ordemInicial
    ) {
        if (capacidade <= 0 || candidatas.isEmpty()) {
            return ordemInicial;
        }
        List<Tarefa> comPrazo = candidatas.stream().filter(t -> t.getPrazo() != null).toList();
        List<Tarefa> semPrazo = candidatas.stream().filter(t -> t.getPrazo() == null).toList();

        int usadoAntes = minutosAlocados(blocos);
        int ordem = alocarFase(
                comPrazo, saldoRestante, capacidade, dia, filtrarPrazo, true, blocos, ordemInicial);
        int restante = capacidade - (minutosAlocados(blocos) - usadoAntes);
        return alocarFase(
                semPrazo, saldoRestante, restante, dia, filtrarPrazo, false, blocos, ordem);
    }

    private static int minutosAlocados(List<BlocoAlocado> blocos) {
        return blocos.stream().mapToInt(BlocoAlocado::duracaoMinutos).sum();
    }

    private static int alocarFase(
            List<Tarefa> candidatasBase,
            Map<UUID, Integer> saldoRestante,
            int capacidadeRestante,
            LocalDate dia,
            boolean filtrarPrazo,
            boolean exigirPrazoNoDia,
            List<BlocoAlocado> blocos,
            int ordemInicial
    ) {
        int restante = capacidadeRestante;
        int ordem = ordemInicial;

        while (restante >= PISO_BLOCO_MINUTOS) {
            List<Tarefa> candidatas = candidatasBase.stream()
                    .filter(t -> saldoRestante.getOrDefault(t.getId(), 0) > 0)
                    .filter(t -> !exigirPrazoNoDia
                            || !filtrarPrazo
                            || !dia.isAfter(prazoEmDiaLocal(t)))
                    .sorted(comparadorPorScore(dia))
                    .toList();

            boolean alocou = false;
            for (Tarefa tarefa : candidatas) {
                int saldo = saldoRestante.getOrDefault(tarefa.getId(), 0);
                Integer usar = minutosDoProximoBloco(saldo, restante);
                if (usar == null) {
                    continue;
                }
                blocos.add(new BlocoAlocado(tarefa.getId(), usar, ordem++, false));
                saldoRestante.put(tarefa.getId(), saldo - usar);
                restante -= usar;
                alocou = true;
                break;
            }
            if (!alocou) {
                break;
            }
        }
        return ordem;
    }

    /**
     * Tarefas com prazo dentro da janela e saldo ainda positivo → conflito
     * (minutos que não caberam até o prazo). Mensagem agregada nos planos da proposta.
     */
    static Map<LocalDate, ResultadoAlocacao> aplicarConflitosDePrazo(
            Map<LocalDate, ResultadoAlocacao> porDia,
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoRestante,
            List<DiaCapacidade> dias
    ) {
        if (dias.isEmpty()) {
            return Map.copyOf(porDia);
        }

        LocalDate inicio = dias.get(0).data();
        LocalDate fim = dias.get(dias.size() - 1).data();

        List<String> itens = new ArrayList<>();
        for (Tarefa tarefa : elegiveis) {
            if (tarefa.getPrazo() == null) {
                continue;
            }
            LocalDate prazo = prazoEmDiaLocal(tarefa);
            if (prazo.isBefore(inicio) || prazo.isAfter(fim)) {
                continue;
            }
            int fora = saldoRestante.getOrDefault(tarefa.getId(), 0);
            if (fora <= 0) {
                continue;
            }
            itens.add(formatarItemConflitoPrazo(tarefa.getTitulo(), fora, prazo));
        }

        if (itens.isEmpty()) {
            return Map.copyOf(porDia);
        }

        String mensagem = String.join(" ", itens);
        Map<LocalDate, ResultadoAlocacao> comConflito = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, ResultadoAlocacao> entry : porDia.entrySet()) {
            ResultadoAlocacao atual = entry.getValue();
            String mensagemDia = atual.mensagem();
            if (mensagemDia == null || mensagemDia.isBlank() || MENSAGEM_SEM_TEMPO.equals(mensagemDia)) {
                mensagemDia = mensagem;
            } else if (!mensagemDia.contains(mensagem)) {
                mensagemDia = mensagemDia + " " + mensagem;
            }
            comConflito.put(entry.getKey(), new ResultadoAlocacao(atual.blocos(), true, mensagemDia));
        }
        return Map.copyOf(comConflito);
    }

    static String formatarItemConflitoPrazo(String titulo, int minutosFora, LocalDate prazo) {
        return "Não cabe até o prazo: \"" + titulo + "\" — " + minutosFora
                + " min fora (prazo " + prazo.format(PRAZO_MSG) + ").";
    }

    static LocalDate prazoEmDiaLocal(Tarefa tarefa) {
        return tarefa.getPrazo().atZone(PeriodoBRT.ZONE).toLocalDate();
    }

    static Comparator<Tarefa> comparadorPorScore(LocalDate diaD) {
        return Comparator
                .comparingInt((Tarefa t) -> ScoreV1.calcular(t, diaD)).reversed()
                .thenComparing(Tarefa::getPrazo, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Tarefa::getCreatedAt);
    }

    public static int capacidadeMinutos(Integer horas) {
        if (horas == null || horas <= 0) {
            return 0;
        }
        return horas * 60;
    }
}
