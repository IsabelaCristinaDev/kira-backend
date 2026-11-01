package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.dto.response.AvisoConversaResponse;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
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

    @InjectMocks
    private MensagemEventListener listener;

    @Test
    @DisplayName("Envia a mensagem para o tópico da conversa e avisa o canal dos dois participantes")
    void enviaParaAConversaEAvisaOsParticipantes() {
        UUID agendamentoId = UUID.randomUUID();
        MensagemResponse mensagem = new MensagemResponse(UUID.randomUUID(), agendamentoId, UUID.randomUUID(),
                "Ana", "Oi", false, LocalDateTime.now());

        UUID clienteId = UUID.randomUUID();
        UUID empresaId = UUID.randomUUID();
        AvisoConversaResponse aviso = new AvisoConversaResponse(agendamentoId, mensagem);

        listener.onMensagemEnviada(new MensagemEnviadaEvent(this, mensagem, List.of(clienteId, empresaId)));

        verify(messagingTemplate).convertAndSend("/topic/agendamentos/" + agendamentoId + "/mensagens", mensagem);
        verify(messagingTemplate).convertAndSend("/topic/usuarios/" + clienteId + "/conversas", aviso);
        verify(messagingTemplate).convertAndSend("/topic/usuarios/" + empresaId + "/conversas", aviso);
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
