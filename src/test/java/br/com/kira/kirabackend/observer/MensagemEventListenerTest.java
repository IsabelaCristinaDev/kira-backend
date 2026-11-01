package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.dto.response.AvisoConversaResponse;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.service.NotificacaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class MensagemEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificacaoService notificacaoService;

    @InjectMocks
    private MensagemEventListener listener;

    @Test
    @DisplayName("Envia a mensagem para o tópico da conversa e avisa o canal dos dois participantes")
    void enviaParaAConversaEAvisaOsParticipantes() {
        UUID agendamentoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        MensagemResponse mensagem = new MensagemResponse(UUID.randomUUID(), agendamentoId, clienteId,
                "Ana", "Oi", false, LocalDateTime.now());

        AvisoConversaResponse aviso = new AvisoConversaResponse(agendamentoId, mensagem);

        listener.onMensagemEnviada(new MensagemEnviadaEvent(this, mensagem, List.of(clienteId, empresaId)));

        verify(messagingTemplate).convertAndSend("/topic/agendamentos/" + agendamentoId + "/mensagens", mensagem);
        verify(messagingTemplate).convertAndSend("/topic/usuarios/" + clienteId + "/conversas", aviso);
        verify(messagingTemplate).convertAndSend("/topic/usuarios/" + empresaId + "/conversas", aviso);
        // A notificação do aparelho vai só para quem recebeu, não para quem enviou
        verify(notificacaoService).notificarMensagem(mensagem, List.of(empresaId));
    }

    @Test
    @DisplayName("Conversa lida avisa só quem leu, sem mensagem no aviso")
    void conversaLidaAvisaQuemLeu() {
        UUID agendamentoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        listener.onConversaLida(new ConversaLidaEvent(this, agendamentoId, usuarioId));

        verify(messagingTemplate).convertAndSend("/topic/usuarios/" + usuarioId + "/conversas",
                new AvisoConversaResponse(agendamentoId, null));
        verifyNoMoreInteractions(messagingTemplate);
    }
}
