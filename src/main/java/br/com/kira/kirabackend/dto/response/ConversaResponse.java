package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.StatusAgendamento;

import java.time.LocalDateTime;
import java.util.UUID;

/** Uma linha da lista de conversas: com quem é, sobre qual atendimento, a última mensagem e quantas não foram lidas. */
public record ConversaResponse(
        UUID agendamentoId,
        String nomeOutraParte,
        String fotoOutraParte,
        String nomeServico,
        LocalDateTime dataHoraInicio,
        StatusAgendamento statusAgendamento,
        String ultimaMensagem,
        boolean ultimaMensagemMinha,
        LocalDateTime dataUltimaMensagem,
        long naoLidas
) {}
