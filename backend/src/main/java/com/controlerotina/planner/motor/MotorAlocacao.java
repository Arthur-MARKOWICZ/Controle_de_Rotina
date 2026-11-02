package com.controlerotina.planner.motor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.tarefa.entity.Tarefa;

public final class MotorAlocacao {

    public static final String MENSAGEM_SEM_TEMPO = "Sem tempo neste dia. Nada planejado.";
    public static final String MENSAGEM_PARCIAL =
            "Capacidade insuficiente: ao menos uma tarefa foi alocada parcialmente.";
    public static final String MENSAGEM_HABITO_OMITIDO =
            "Capacidade insuficiente: um ou mais hábitos do dia não couberam por completo.";

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
        return alocar(elegiveis, capacidadeMinutos, diaD, List.of(), List.of());
    }

    public static ResultadoAlocacao alocar(
            List<Tarefa> elegiveis,
            int capacidadeMinutos,
            LocalDate diaD,
            List<ReservaPedido> reservas
    ) {
        return alocar(elegiveis, capacidadeMinutos, diaD, reservas, List.of());
    }

    public static ResultadoAlocacao alocar(
            List<Tarefa> elegiveis,
            int capacidadeMinutos,
            LocalDate diaD,
            List<ReservaPedido> reservas,
            List<Habito> habitos
    ) {
        Map<UUID, Integer> saldoPorTarefa = new HashMap<>();
        for (Tarefa tarefa : elegiveis) {
            saldoPorTarefa.put(tarefa.getId(), tarefa.getDuracaoMinutos());
        }
        return alocar(elegiveis, saldoPorTarefa, capacidadeMinutos, diaD, reservas, habitos);
    }

    public static ResultadoAlocacao alocar(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoMinutosPorTarefa,
            int capacidadeMinutos,
            LocalDate diaD
    ) {
        return alocar(elegiveis, saldoMinutosPorTarefa, capacidadeMinutos, diaD, List.of(), List.of());
    }

    public static ResultadoAlocacao alocar(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoMinutosPorTarefa,
            int capacidadeMinutos,
            LocalDate diaD,
            List<ReservaPedido> reservas
    ) {
        return alocar(elegiveis, saldoMinutosPorTarefa, capacidadeMinutos, diaD, reservas, List.of());
    }

    public static ResultadoAlocacao alocar(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoMinutosPorTarefa,
            int capacidadeMinutos,
            LocalDate diaD,
            List<ReservaPedido> reservas,
            List<Habito> habitos
    ) {
        List<Habito> habitosEntrada = habitos == null ? List.of() : habitos;
        Map<UUID, Integer> saldoRestante = new HashMap<>(saldoMinutosPorTarefa);
        List<ItemNaoAlocado> omitidos = new ArrayList<>();

        if (capacidadeMinutos <= 0) {
            registrarHabitosOmitidos(filtrarHabitosElegiveis(habitosEntrada, diaD), Set.of(), omitidos);
            registrarTarefasOmitidas(elegiveis, saldoRestante, omitidos);
            boolean temConflito = !omitidos.isEmpty();
            String mensagem = temConflito
                    ? montarMensagemConflito(temConflitoTarefa(omitidos), temConflitoHabito(omitidos))
                    : MENSAGEM_SEM_TEMPO;
            return new ResultadoAlocacao(List.of(), temConflito, mensagem, omitidos);
        }

        List<BlocoAlocado> blocos = alocarDia(
                elegiveis,
                saldoRestante,
                capacidadeMinutos,
                diaD,
                false,
                reservas,
                habitosEntrada,
                omitidos
        );
        registrarTarefasOmitidas(elegiveis, saldoRestante, omitidos);

        boolean temConflitoTarefa = elegiveis.stream()
                .anyMatch(t -> saldoRestante.getOrDefault(t.getId(), 0) > 0
                        && saldoMinutosPorTarefa.getOrDefault(t.getId(), 0) > 0);
        if (temConflitoTarefa && !blocos.isEmpty()) {
            BlocoAlocado ultimo = blocos.get(blocos.size() - 1);
            if (ultimo.tarefaId() != null && saldoRestante.getOrDefault(ultimo.tarefaId(), 0) > 0) {
                blocos.set(blocos.size() - 1, new BlocoAlocado(
                        ultimo.tarefaId(),
                        ultimo.duracaoMinutos(),
                        ultimo.ordem(),
                        true
                ));
            }
        }

        boolean temConflito = temConflitoTarefa || !omitidos.isEmpty();
        String mensagem = montarMensagemConflito(temConflitoTarefa, temConflitoHabito(omitidos));
        return new ResultadoAlocacao(List.copyOf(blocos), temConflito, mensagem, omitidos);
    }

    static String montarMensagemConflito(boolean conflitoTarefa, boolean conflitoHabito) {
        if (conflitoTarefa && conflitoHabito) {
            return MENSAGEM_PARCIAL + " " + MENSAGEM_HABITO_OMITIDO;
        }
        if (conflitoHabito) {
            return MENSAGEM_HABITO_OMITIDO;
        }
        if (conflitoTarefa) {
            return MENSAGEM_PARCIAL;
        }
        return null;
    }

    /** Hábitos ATIVO, não deletados, com o weekday de {@code dia} na agenda. */
    public static List<Habito> filtrarHabitosElegiveis(List<Habito> habitos, LocalDate dia) {
        if (habitos == null || habitos.isEmpty()) {
            return List.of();
        }
        DiaSemana weekday = DiaSemana.de(dia);
        return habitos.stream()
                .filter(h -> h.getDeletedAt() == null)
                .filter(h -> h.getStatus() == StatusHabito.ATIVO)
                .filter(h -> h.getDiasSemana().contains(weekday))
                .toList();
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
        return alocarJanela(elegiveis, saldoMinutosPorTarefa, dias, List.of());
    }

    public static Map<LocalDate, ResultadoAlocacao> alocarJanela(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoMinutosPorTarefa,
            List<DiaCapacidade> dias,
            List<Habito> habitos
    ) {
        Map<UUID, Integer> saldoRestante = new HashMap<>(saldoMinutosPorTarefa);
        Map<LocalDate, ResultadoAlocacao> porDia = new LinkedHashMap<>();
        List<Habito> habitosEntrada = habitos == null ? List.of() : habitos;

        for (DiaCapacidade dia : dias) {
            List<ItemNaoAlocado> omitidos = new ArrayList<>();
            if (dia.capacidadeMinutos() <= 0) {
                registrarHabitosOmitidos(
                        filtrarHabitosElegiveis(habitosEntrada, dia.data()), Set.of(), omitidos);
                boolean conflitoHabito = !omitidos.isEmpty();
                porDia.put(
                        dia.data(),
                        new ResultadoAlocacao(
                                List.of(),
                                conflitoHabito,
                                conflitoHabito ? MENSAGEM_HABITO_OMITIDO : MENSAGEM_SEM_TEMPO,
                                omitidos
                        )
                );
                continue;
            }

            List<BlocoAlocado> blocos = alocarDia(
                    elegiveis,
                    saldoRestante,
                    dia.capacidadeMinutos(),
                    dia.data(),
                    true,
                    dia.reservas(),
                    habitosEntrada,
                    omitidos
            );
            boolean conflitoHabito = temConflitoHabito(omitidos);
            porDia.put(
                    dia.data(),
                    new ResultadoAlocacao(
                            List.copyOf(blocos),
                            conflitoHabito,
                            conflitoHabito ? MENSAGEM_HABITO_OMITIDO : null,
                            omitidos
                    )
            );
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
            List<ReservaPedido> reservas,
            List<Habito> habitos,
            List<ItemNaoAlocado> omitidos
    ) {
        List<BlocoAlocado> blocos = new ArrayList<>();
        int ordem = 1;

        for (ReservaPedido fatia : fatiasEfetivas(reservas, capacidadeMinutos)) {
            List<Tarefa> doProjeto = elegiveis.stream()
                    .filter(t -> fatia.projetoId().equals(t.getProjetoId()))
                    .toList();
            // Reservas de projeto: só tarefas do projeto (sem hábitos).
            ordem = alocarPoolUnificado(
                    doProjeto,
                    List.of(),
                    saldoRestante,
                    fatia.minutosReservados(),
                    dia,
                    filtrarPrazo,
                    blocos,
                    ordem,
                    omitidos
            );
        }

        // Pool livre (após reservas): tarefas com/sem prazo + hábitos, ordenados por score.
        int poolLivre = capacidadeMinutos - minutosAlocados(blocos);
        alocarPoolUnificado(
                elegiveis,
                filtrarHabitosElegiveis(habitos, dia),
                saldoRestante,
                poolLivre,
                dia,
                filtrarPrazo,
                blocos,
                ordem,
                omitidos
        );
        return blocos;
    }

    /**
     * Pool unificado por score: tarefas elegíveis com saldo + hábitos do dia.
     * Empate: TAREFA antes de HABITO; depois createdAt asc.
     */
    private static int alocarPoolUnificado(
            List<Tarefa> candidatas,
            List<Habito> habitosElegiveis,
            Map<UUID, Integer> saldoRestante,
            int capacidade,
            LocalDate dia,
            boolean filtrarPrazo,
            List<BlocoAlocado> blocos,
            int ordemInicial,
            List<ItemNaoAlocado> omitidos
    ) {
        if (capacidade <= 0) {
            registrarHabitosOmitidos(habitosElegiveis, Set.of(), omitidos);
            return ordemInicial;
        }

        int restante = capacidade;
        int ordem = ordemInicial;
        Set<UUID> habitosAlocados = new HashSet<>();
        Set<UUID> habitosDescartados = new HashSet<>();

        while (restante >= PISO_BLOCO_MINUTOS) {
            List<Tarefa> tarefasCandidatas = candidatas.stream()
                    .filter(t -> saldoRestante.getOrDefault(t.getId(), 0) > 0)
                    .filter(t -> !filtrarPrazo
                            || t.getPrazo() == null
                            || !dia.isAfter(prazoEmDiaLocal(t)))
                    .toList();
            List<Habito> habitosCandidatos = habitosElegiveis.stream()
                    .filter(h -> !habitosAlocados.contains(h.getId()))
                    .filter(h -> !habitosDescartados.contains(h.getId()))
                    .toList();

            List<CandidatoPool> fila = new ArrayList<>();
            for (Tarefa t : tarefasCandidatas) {
                fila.add(CandidatoPool.tarefa(t, ScoreV1.calcular(t, dia)));
            }
            for (Habito h : habitosCandidatos) {
                fila.add(CandidatoPool.habito(h, ScoreV1.calcularHabito(h.getDuracaoMinutos())));
            }
            fila.sort(CandidatoPool.COMPARADOR);

            boolean alocou = false;
            for (CandidatoPool candidato : fila) {
                if (candidato.habito() != null) {
                    Habito habito = candidato.habito();
                    if (habito.getDuracaoMinutos() <= restante) {
                        blocos.add(BlocoAlocado.deHabito(habito.getId(), habito.getDuracaoMinutos(), ordem++));
                        restante -= habito.getDuracaoMinutos();
                        habitosAlocados.add(habito.getId());
                        alocou = true;
                        break;
                    }
                    habitosDescartados.add(habito.getId());
                    continue;
                }
                Tarefa tarefa = candidato.tarefa();
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

        registrarHabitosOmitidos(habitosElegiveis, habitosAlocados, omitidos);
        return ordem;
    }

    private static void registrarHabitosOmitidos(
            List<Habito> elegiveis,
            Set<UUID> alocados,
            List<ItemNaoAlocado> omitidos
    ) {
        if (elegiveis == null || elegiveis.isEmpty()) {
            return;
        }
        Set<UUID> jaRegistrados = new HashSet<>();
        for (ItemNaoAlocado item : omitidos) {
            if (item.tipo() == TipoOrigemAlocacao.HABITO) {
                jaRegistrados.add(item.id());
            }
        }
        for (Habito habito : elegiveis) {
            if (alocados.contains(habito.getId()) || jaRegistrados.contains(habito.getId())) {
                continue;
            }
            omitidos.add(new ItemNaoAlocado(
                    TipoOrigemAlocacao.HABITO,
                    habito.getId(),
                    habito.getTitulo(),
                    habito.getDuracaoMinutos(),
                    MotivoNaoAlocado.CAPACIDADE
            ));
        }
    }

    private static void registrarTarefasOmitidas(
            List<Tarefa> elegiveis,
            Map<UUID, Integer> saldoRestante,
            List<ItemNaoAlocado> omitidos
    ) {
        if (elegiveis == null || elegiveis.isEmpty()) {
            return;
        }
        Set<UUID> jaRegistrados = new HashSet<>();
        for (ItemNaoAlocado item : omitidos) {
            if (item.tipo() == TipoOrigemAlocacao.TAREFA) {
                jaRegistrados.add(item.id());
            }
        }
        for (Tarefa tarefa : elegiveis) {
            if (jaRegistrados.contains(tarefa.getId())) {
                continue;
            }
            if (saldoRestante.getOrDefault(tarefa.getId(), 0) <= 0) {
                continue;
            }
            omitidos.add(new ItemNaoAlocado(
                    TipoOrigemAlocacao.TAREFA,
                    tarefa.getId(),
                    tarefa.getTitulo(),
                    tarefa.getDuracaoMinutos(),
                    MotivoNaoAlocado.CAPACIDADE
            ));
        }
    }

    private static boolean temConflitoHabito(List<ItemNaoAlocado> omitidos) {
        return omitidos.stream().anyMatch(i -> i.tipo() == TipoOrigemAlocacao.HABITO);
    }

    private static boolean temConflitoTarefa(List<ItemNaoAlocado> omitidos) {
        return omitidos.stream().anyMatch(i -> i.tipo() == TipoOrigemAlocacao.TAREFA);
    }

    private record CandidatoPool(Tarefa tarefa, Habito habito, int score, java.time.Instant createdAt) {
        static CandidatoPool tarefa(Tarefa t, int score) {
            return new CandidatoPool(t, null, score, t.getCreatedAt());
        }

        static CandidatoPool habito(Habito h, int score) {
            return new CandidatoPool(null, h, score, h.getCreatedAt());
        }

        static final Comparator<CandidatoPool> COMPARADOR = Comparator
                .comparingInt(CandidatoPool::score).reversed()
                .thenComparing(c -> c.habito() != null) // tarefa (false) antes de hábito (true)
                .thenComparing(CandidatoPool::createdAt);
    }

    private static int minutosAlocados(List<BlocoAlocado> blocos) {
        return blocos.stream().mapToInt(BlocoAlocado::duracaoMinutos).sum();
    }

    /**
     * Tarefas com prazo dentro da janela e saldo ainda positivo → conflito
     * (minutos que não caberam até o prazo). Mensagem agregada nos planos da proposta.
     * Tarefas com saldo residual entram em {@code itensNaoAlocados} no dia da última chance
     * (prazo na janela ou último dia), sem listar em D o que ainda cabe em D+1.
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

        Map<LocalDate, List<ItemNaoAlocado>> tarefasOmitidasPorDia = new HashMap<>();
        List<String> itens = new ArrayList<>();
        for (Tarefa tarefa : elegiveis) {
            int fora = saldoRestante.getOrDefault(tarefa.getId(), 0);
            if (fora <= 0) {
                continue;
            }
            LocalDate diaOmitido;
            if (tarefa.getPrazo() != null) {
                LocalDate prazo = prazoEmDiaLocal(tarefa);
                if (prazo.isBefore(inicio) || prazo.isAfter(fim)) {
                    continue;
                }
                diaOmitido = prazo;
                itens.add(formatarItemConflitoPrazo(tarefa.getTitulo(), fora, prazo));
            } else {
                diaOmitido = fim;
            }
            tarefasOmitidasPorDia
                    .computeIfAbsent(diaOmitido, d -> new ArrayList<>())
                    .add(new ItemNaoAlocado(
                            TipoOrigemAlocacao.TAREFA,
                            tarefa.getId(),
                            tarefa.getTitulo(),
                            tarefa.getDuracaoMinutos(),
                            MotivoNaoAlocado.CAPACIDADE
                    ));
        }

        String mensagemPrazo = itens.isEmpty() ? null : String.join(" ", itens);
        Map<LocalDate, ResultadoAlocacao> comConflito = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, ResultadoAlocacao> entry : porDia.entrySet()) {
            ResultadoAlocacao atual = entry.getValue();
            List<ItemNaoAlocado> omitidos = new ArrayList<>(atual.itensNaoAlocados());
            List<ItemNaoAlocado> extras = tarefasOmitidasPorDia.getOrDefault(entry.getKey(), List.of());
            omitidos.addAll(extras);

            boolean temConflito = atual.temConflito() || !extras.isEmpty() || mensagemPrazo != null;
            String mensagemDia = atual.mensagem();
            if (mensagemPrazo != null) {
                if (mensagemDia == null || mensagemDia.isBlank() || MENSAGEM_SEM_TEMPO.equals(mensagemDia)) {
                    mensagemDia = mensagemPrazo;
                } else if (!mensagemDia.contains(mensagemPrazo)) {
                    mensagemDia = mensagemDia + " " + mensagemPrazo;
                }
            } else if (temConflito && (mensagemDia == null || mensagemDia.isBlank())) {
                mensagemDia = montarMensagemConflito(temConflitoTarefa(omitidos), temConflitoHabito(omitidos));
            }
            comConflito.put(
                    entry.getKey(),
                    new ResultadoAlocacao(atual.blocos(), temConflito, mensagemDia, omitidos)
            );
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

    public static int capacidadeMinutos(Integer horas) {
        if (horas == null || horas <= 0) {
            return 0;
        }
        return horas * 60;
    }
}
