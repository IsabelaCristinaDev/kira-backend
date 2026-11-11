package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.entity.Mensagem;

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
) {
    public static MensagemResponse from(Mensagem m) {
        return new MensagemResponse(
                m.getId(),
                m.getAgendamento().getId(),
                m.getRemetente().getId(),
                m.getRemetente().getNome(),
                m.getConteudo(),
                m.getLida(),
                m.getDataEnvio()
        );
    }
}
