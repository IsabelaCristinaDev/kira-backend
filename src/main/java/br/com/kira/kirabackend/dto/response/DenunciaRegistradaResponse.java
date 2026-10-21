package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.StatusDenuncia;

import java.time.LocalDateTime;
import java.util.UUID;

public record DenunciaRegistradaResponse(
        UUID id,
        StatusDenuncia status,
        Boolean riscoImediato,
        Boolean perfilSuspenso,
        String telefoneEmergencia,
        String orientacao,
        LocalDateTime dataCriacao
) {}
