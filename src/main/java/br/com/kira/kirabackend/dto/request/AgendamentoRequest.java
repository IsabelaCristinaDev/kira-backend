package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.FormaPagamento;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgendamentoRequest(

        @NotNull(message = "Empresa é obrigatória")
        UUID empresaId,

        UUID funcionariaId,

        @NotNull(message = "Serviço é obrigatório")
        UUID servicoId,

        @NotNull(message = "Data e hora são obrigatórios")
        LocalDateTime dataHoraInicio,

        FormaPagamento formaPagamento,

        String observacoes
) {}