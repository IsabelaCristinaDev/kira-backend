package br.com.kira.kirabackend.observer;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class ConversaLidaEvent extends ApplicationEvent {

    private final transient UUID agendamentoId;
    private final transient UUID usuarioId;

    public ConversaLidaEvent(Object source, UUID agendamentoId, UUID usuarioId) {
        super(source);
        this.agendamentoId = agendamentoId;
        this.usuarioId = usuarioId;
    }

    public UUID getAgendamentoId() {
        return agendamentoId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }
}
