package br.com.kira.kirabackend.observer;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/** A senha do usuário foi redefinida: as sessões dele precisam ser encerradas. */
public class SenhaRedefinidaEvent extends ApplicationEvent {

    private final UUID usuarioId;

    public SenhaRedefinidaEvent(Object source, UUID usuarioId) {
        super(source);
        this.usuarioId = usuarioId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }
}
