package br.com.kira.kirabackend.observer;

import java.util.UUID;

public record ConversaLidaEvent(UUID agendamentoId, UUID usuarioId) {}
