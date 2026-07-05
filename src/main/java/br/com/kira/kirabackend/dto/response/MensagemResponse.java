package br.com.kira.kirabackend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record MensagemResponse(
        UUID id,
        UUID agendamentoId,
        UUID remetenteId,
        String nomeRemetente,
        String conteudo,
        Boolean lida,
        LocalDateTime dataEnvio
) {}