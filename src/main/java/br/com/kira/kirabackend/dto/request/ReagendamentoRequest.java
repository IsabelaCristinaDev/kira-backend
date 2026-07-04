package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ReagendamentoRequest(

        @NotNull(message = "Nova data e hora são obrigatórias")
        LocalDateTime novaDataHoraInicio,

        String motivo
) {}