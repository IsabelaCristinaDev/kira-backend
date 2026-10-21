package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.domain.enums.MotivoDenuncia;

import java.time.LocalDateTime;
import java.util.UUID;

public record DenunciaRiscoImediatoEvent(
        UUID denunciaId,
        MotivoDenuncia motivo,
        String nomeDenunciante,
        String telefoneDenunciante,
        String nomeDenunciado,
        boolean perfilSuspenso,
        LocalDateTime dataCriacao
) {}
