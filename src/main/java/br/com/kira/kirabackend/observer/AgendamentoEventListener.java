package br.com.kira.kirabackend.observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AgendamentoEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoEventListener.class);

    @EventListener
    @Async
    public void onAgendamentoCriado(AgendamentoCriadoEvent event) {
        var agendamento = event.getAgendamento();
        log.info("Agendamento criado: {} - Cliente: {} - Empresa: {}",
                agendamento.getId(),
                agendamento.getCliente().getNome(),
                agendamento.getEmpresa().getNome());

    }

    @EventListener
    @Async
    public void onAgendamentoCancelado(AgendamentoCanceladoEvent event) {
        var agendamento = event.getAgendamento();
        log.info("Agendamento cancelado: {} - Cliente: {} - Empresa: {}",
                agendamento.getId(),
                agendamento.getCliente().getNome(),
                agendamento.getEmpresa().getNome());

    }

    @EventListener
    @Async
    public void onAtendimentoConcluido(AtendimentoConcluidoEvent event) {
        var agendamento = event.getAgendamento();
        log.info("Atendimento concluído: {} - Cliente: {} - Empresa: {}",
                agendamento.getId(),
                agendamento.getCliente().getNome(),
                agendamento.getEmpresa().getNome());
    }
}