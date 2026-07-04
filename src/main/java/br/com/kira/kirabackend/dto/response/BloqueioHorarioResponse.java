package br.com.kira.kirabackend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record BloqueioHorarioResponse(
        UUID id,
        UUID funcionariaId,
        String nomeFuncionaria,
        LocalDateTime dataHoraInicio,
        LocalDateTime dataHoraFim,
        String motivo
) {}