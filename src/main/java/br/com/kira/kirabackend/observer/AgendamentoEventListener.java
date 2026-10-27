package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.service.ComissaoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgendamentoEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoEventListener.class);

    private final ComissaoService comissaoService;

    // Síncronos de propósito: rodar em outra thread lendo cliente/empresa (lazy) usa a mesma sessão
    // do Hibernate ao mesmo tempo que a requisição e quebra o cancelamento com erro 500.
    @EventListener
    public void onAgendamentoCriado(AgendamentoCriadoEvent event) {
        var agendamento = event.getAgendamento();
        log.info("Agendamento criado: {} - Cliente: {} - Empresa: {}",
                agendamento.getId(),
                agendamento.getCliente().getNome(),
                agendamento.getEmpresa().getNome());
    }

    @EventListener
    public void onAgendamentoCancelado(AgendamentoCanceladoEvent event) {
        var agendamento = event.getAgendamento();
        log.info("Agendamento cancelado: {} - Cliente: {} - Empresa: {}",
                agendamento.getId(),
                agendamento.getCliente().getNome(),
                agendamento.getEmpresa().getNome());
    }

    @EventListener
    public void onAtendimentoConcluido(AtendimentoConcluidoEvent event) {
        var agendamento = event.getAgendamento();
        log.info("Atendimento concluído: {} - Cliente: {} - Empresa: {}",
                agendamento.getId(),
                agendamento.getCliente().getNome(),
                agendamento.getEmpresa().getNome());
        comissaoService.calcularERegistrarComissao(agendamento);
    }
}