package com.controlerotina.capacidade.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.controlerotina.capacidade.dto.CapacidadeResolvidaResponse;
import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.entity.OrigemCapacidade;
import com.controlerotina.capacidade.repository.CapacidadeOverrideRepository;
import com.controlerotina.capacidade.repository.CapacidadeSemanalRepository;

@Service
public class CapacidadeResolvidaService {

    static final int INTERVALO_MAXIMO_DIAS = 31;

    private final CapacidadeOverrideRepository capacidadeOverrideRepository;
    private final CapacidadeSemanalRepository capacidadeSemanalRepository;

    public CapacidadeResolvidaService(
            CapacidadeOverrideRepository capacidadeOverrideRepository,
            CapacidadeSemanalRepository capacidadeSemanalRepository
    ) {
        this.capacidadeOverrideRepository = capacidadeOverrideRepository;
        this.capacidadeSemanalRepository = capacidadeSemanalRepository;
    }

    @Transactional(readOnly = true)
    public List<CapacidadeResolvidaResponse> resolver(UUID usuarioId, LocalDate dataDe, LocalDate dataAte) {
        if (dataDe == null || dataAte == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataDe e dataAte são obrigatórios.");
        }
        if (dataAte.isBefore(dataDe)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataAte deve ser maior ou igual a dataDe.");
        }
        if (ChronoUnit.DAYS.between(dataDe, dataAte) + 1 > INTERVALO_MAXIMO_DIAS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Intervalo máximo permitido é de " + INTERVALO_MAXIMO_DIAS + " dias."
            );
        }

        Map<LocalDate, CapacidadeOverride> overrides = capacidadeOverrideRepository
                .findByUsuarioIdAndAtivoTrueAndDataBetweenOrderByDataAsc(usuarioId, dataDe, dataAte)
                .stream()
                .collect(Collectors.toMap(CapacidadeOverride::getData, Function.identity()));

        Map<DiaSemana, CapacidadeSemanal> semanais = capacidadeSemanalRepository
                .findByUsuarioIdAndAtivoTrueOrderByDiaSemanaAsc(usuarioId)
                .stream()
                .collect(Collectors.toMap(CapacidadeSemanal::getDiaSemana, Function.identity()));

        List<CapacidadeResolvidaResponse> resultado = new ArrayList<>();
        for (LocalDate data = dataDe; !data.isAfter(dataAte); data = data.plusDays(1)) {
            CapacidadeOverride override = overrides.get(data);
            if (override != null) {
                resultado.add(new CapacidadeResolvidaResponse(
                        data, override.getHoras(), OrigemCapacidade.OVERRIDE));
                continue;
            }

            CapacidadeSemanal semanal = semanais.get(DiaSemana.de(data));
            if (semanal != null) {
                resultado.add(new CapacidadeResolvidaResponse(
                        data, semanal.getHoras(), OrigemCapacidade.SEMANAL));
                continue;
            }

            resultado.add(new CapacidadeResolvidaResponse(data, null, OrigemCapacidade.VAZIO));
        }
        return resultado;
    }
}
