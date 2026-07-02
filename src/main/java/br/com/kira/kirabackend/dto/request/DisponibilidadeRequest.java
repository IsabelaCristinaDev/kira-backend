package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.DiaSemana;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record DisponibilidadeRequest(

        @NotNull(message = "Dia da semana é obrigatório")
        DiaSemana diaSemana,

        @NotNull(message = "Hora de início é obrigatória")
        LocalTime horaInicio,

        @NotNull(message = "Hora de fim é obrigatória")
        LocalTime horaFim
) {}