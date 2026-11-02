package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.dto.response.MensagemResponse;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.UUID;

public class MensagemEnviadaEvent extends ApplicationEvent {

    private final transient MensagemResponse mensagem;
    private final transient List<UUID> participantes;

    public MensagemEnviadaEvent(Object source, MensagemResponse mensagem, List<UUID> participantes) {
        super(source);
        this.mensagem = mensagem;
        this.participantes = participantes;
    }

    public MensagemResponse getMensagem() {
        return mensagem;
    }

    public List<UUID> getParticipantes() {
        return participantes;
    }
}
