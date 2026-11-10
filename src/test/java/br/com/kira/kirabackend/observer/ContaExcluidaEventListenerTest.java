package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.service.ImagensEnviadas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContaExcluidaEventListenerTest {

    @Mock
    private ImagensEnviadas imagens;

    @InjectMocks
    private ContaExcluidaEventListener listener;

    @Test
    @DisplayName("Depois da exclusão, apaga do disco cada foto da conta")
    void apagaAsFotos() {
        listener.onContaExcluida(new ContaExcluidaEvent(List.of("/api/arquivos/a.jpg", "/api/arquivos/b.png")));

        verify(imagens).apagarSemFalhar("/api/arquivos/a.jpg");
        verify(imagens).apagarSemFalhar("/api/arquivos/b.png");
    }
}
