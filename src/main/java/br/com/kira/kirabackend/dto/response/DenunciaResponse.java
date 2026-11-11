package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.MotivoDenuncia;
import br.com.kira.kirabackend.domain.enums.StatusDenuncia;

import java.time.LocalDateTime;
import java.util.UUID;

public record DenunciaResponse(
        UUID id,
        UUID denunciadoId,
        String nomeDenunciado,
        UUID denuncianteId,
        String nomeDenunciante,
        String telefoneDenunciante,
        UUID agendamentoId,
        MotivoDenuncia motivo,
        String descricao,
        Boolean riscoImediato,
        Boolean anonima,
        StatusDenuncia status,
        Boolean perfilSuspenso,
        UUID suspensaoAtivaId,
        Boolean autoridadeAcionada,
        Boolean infundada,
        String parecer,
        LocalDateTime dataCriacao,
        LocalDateTime dataAnalise
) {}
