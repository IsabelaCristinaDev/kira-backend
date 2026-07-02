package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.DiaSemana;

import java.time.LocalTime;
import java.util.UUID;

public record DisponibilidadeResponse(
        UUID id,
        DiaSemana diaSemana,
        LocalTime horaInicio,
        LocalTime horaFim
) {}