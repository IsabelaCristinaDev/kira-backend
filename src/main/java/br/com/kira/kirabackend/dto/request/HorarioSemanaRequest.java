package br.com.kira.kirabackend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Horário de funcionamento da semana inteira. Dia que não aparece na lista fica fechado.
 */
public record HorarioSemanaRequest(

        @NotNull(message = "A lista de dias é obrigatória")
        List<@Valid @NotNull DisponibilidadeRequest> dias
) {}
