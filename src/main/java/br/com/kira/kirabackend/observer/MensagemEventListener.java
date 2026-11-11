package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.dto.response.AvisoConversaResponse;
import br.com.kira.kirabackend.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MensagemEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificacaoService notificacaoService;

    // Só depois do commit: quem está na conversa nunca recebe uma mensagem que acabou não sendo salva.
    // O evento já traz o DTO, então nada aqui lê relação LAZY.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMensagemEnviada(MensagemEnviadaEvent event) {
        var mensagem = event.getMensagem();
        messagingTemplate.convertAndSend("/topic/agendamentos/" + mensagem.agendamentoId() + "/mensagens", mensagem);
        // Os dois lados: quem enviou também tem a lista de conversas mudada, em outra aba por exemplo
        AvisoConversaResponse aviso = new AvisoConversaResponse(mensagem.agendamentoId(), mensagem);
        event.getParticipantes().forEach(participante -> avisar(participante, aviso));
        // Notificação do aparelho: só para quem recebeu
        notificacaoService.notificarMensagem(mensagem, event.getParticipantes().stream()
                .filter(participante -> !participante.equals(mensagem.remetenteId()))
                .toList());
    }

    // Só para quem leu: o contador da outra parte não muda
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConversaLida(ConversaLidaEvent event) {
        avisar(event.usuarioId(), new AvisoConversaResponse(event.agendamentoId(), null));
    }

    private void avisar(UUID usuarioId, AvisoConversaResponse aviso) {
        messagingTemplate.convertAndSend("/topic/usuarios/" + usuarioId + "/conversas", aviso);
    }
}
