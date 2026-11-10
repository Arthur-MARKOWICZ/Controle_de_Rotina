package com.controlerotina.tarefa.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SessaoTarefaRequest(
        Integer duracaoMinutos,
        LocalDate data,
        UUID blocoId
) {
}
