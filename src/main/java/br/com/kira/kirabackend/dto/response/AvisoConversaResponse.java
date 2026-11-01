package br.com.kira.kirabackend.dto.response;

import java.util.UUID;

/**
 * Aviso no canal ao vivo da pessoa: a lista de conversas e o contador mudaram.
 * Com {@code mensagem}, chegou mensagem nova; sem ela, a conversa foi marcada como lida.
 */
public record AvisoConversaResponse(
        UUID agendamentoId,
        MensagemResponse mensagem
) {}
