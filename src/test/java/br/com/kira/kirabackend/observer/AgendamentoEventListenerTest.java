package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoAvisoAgendamento;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.service.ComissaoService;
import br.com.kira.kirabackend.service.NotificacaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AgendamentoEventListenerTest {

    @Mock
    private ComissaoService comissaoService;

    @Mock
    private NotificacaoService notificacaoService;

    @InjectMocks
    private AgendamentoEventListener listener;

    @Test
    @DisplayName("Aviso do agendamento vai para a cliente pela notificação do aparelho")
    void avisoVaiParaACliente() {
        AgendamentoResponse agendamento = new AgendamentoResponse(UUID.randomUUID(), UUID.randomUUID(), "Camila",
                UUID.randomUUID(), "Espaço Aurora", null, null, UUID.randomUUID(), "Corte feminino", 60,
                LocalDateTime.of(2026, 11, 6, 10, 0), LocalDateTime.of(2026, 11, 6, 11, 0),
                StatusAgendamento.CONFIRMADO, null, null, null);

        listener.onAvisoAgendamento(new AvisoAgendamentoEvent(agendamento, TipoAvisoAgendamento.CANCELADO_PELO_ESTUDIO));

        verify(notificacaoService).notificarAgendamento(agendamento, TipoAvisoAgendamento.CANCELADO_PELO_ESTUDIO);
        verifyNoInteractions(comissaoService);
    }
}
