package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.service.ComissaoService;
import br.com.kira.kirabackend.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AgendamentoEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoEventListener.class);

    private final ComissaoService comissaoService;
    private final NotificacaoService notificacaoService;

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

    // Só depois do commit, para a cliente não ser avisada de uma mudança que acabou não sendo salva.
    // O evento já traz o DTO, então o envio assíncrono não lê relação LAZY.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAvisoAgendamento(AvisoAgendamentoEvent event) {
        notificacaoService.notificarAgendamento(event.agendamento(), event.tipo());
    }
}
