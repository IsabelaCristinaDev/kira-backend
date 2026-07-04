package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BloqueioHorarioRequest(

        @NotNull(message = "Data e hora de início são obrigatórios")
        LocalDateTime dataHoraInicio,

        @NotNull(message = "Data e hora de fim são obrigatórios")
        LocalDateTime dataHoraFim,

        String motivo
) {}