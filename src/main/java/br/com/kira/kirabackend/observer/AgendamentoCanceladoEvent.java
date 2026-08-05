package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import org.springframework.context.ApplicationEvent;

public class AgendamentoCanceladoEvent extends ApplicationEvent {

    private final transient Agendamento agendamento;

    public AgendamentoCanceladoEvent(Object source, Agendamento agendamento) {
        super(source);
        this.agendamento = agendamento;
    }

    public Agendamento getAgendamento() {
        return agendamento;
    }
}