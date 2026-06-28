package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AvaliacaoRequest(

        @NotNull(message = "Agendamento é obrigatório")
        UUID agendamentoId,

        @NotNull(message = "Nota é obrigatória")
        @Min(value = 1, message = "Nota mínima é 1")
        @Max(value = 5, message = "Nota máxima é 5")
        Integer nota,

        String comentario
) {}