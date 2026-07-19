package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import org.springframework.context.ApplicationEvent;

public class AtendimentoConcluidoEvent extends ApplicationEvent {

    private final Agendamento agendamento;

    public AtendimentoConcluidoEvent(Object source, Agendamento agendamento) {
        super(source);
        this.agendamento = agendamento;
    }

    public Agendamento getAgendamento() {
        return agendamento;
    }
}