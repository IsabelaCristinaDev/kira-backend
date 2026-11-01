package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.dto.response.MensagemResponse;
import org.springframework.context.ApplicationEvent;

public class MensagemEnviadaEvent extends ApplicationEvent {

    private final transient MensagemResponse mensagem;

    public MensagemEnviadaEvent(Object source, MensagemResponse mensagem) {
        super(source);
        this.mensagem = mensagem;
    }

    public MensagemResponse getMensagem() {
        return mensagem;
    }
}
