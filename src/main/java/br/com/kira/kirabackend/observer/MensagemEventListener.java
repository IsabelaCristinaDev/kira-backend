package br.com.kira.kirabackend.observer;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MensagemEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    // Só depois do commit: quem está na conversa nunca recebe uma mensagem que acabou não sendo salva.
    // O evento já traz o DTO, então nada aqui lê relação LAZY.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMensagemEnviada(MensagemEnviadaEvent event) {
        var mensagem = event.getMensagem();
        messagingTemplate.convertAndSend("/topic/agendamentos/" + mensagem.agendamentoId() + "/mensagens", mensagem);
    }
}
