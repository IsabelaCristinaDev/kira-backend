package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.dto.response.MensagemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MensagemEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MensagemEventListener listener;

    @Test
    @DisplayName("Envia a mensagem para o tópico da conversa do agendamento")
    void enviaParaOTopicoDaConversa() {
        UUID agendamentoId = UUID.randomUUID();
        MensagemResponse mensagem = new MensagemResponse(UUID.randomUUID(), agendamentoId, UUID.randomUUID(),
                "Ana", "Oi", false, LocalDateTime.now());

        listener.onMensagemEnviada(new MensagemEnviadaEvent(this, mensagem));

        verify(messagingTemplate).convertAndSend("/topic/agendamentos/" + agendamentoId + "/mensagens", mensagem);
    }
}
