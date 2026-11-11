package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.dto.request.DispositivoWebRequest;
import br.com.kira.kirabackend.dto.request.RemoverDispositivoRequest;
import br.com.kira.kirabackend.service.NotificacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoControllerTest {

    @Mock
    private NotificacaoService notificacaoService;

    @InjectMocks
    private NotificacaoController controller;

    private Cliente usuarioLogado;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Cliente();
        usuarioLogado.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Registrar usa o usuário logado")
    void registrar() {
        DispositivoWebRequest request = new DispositivoWebRequest("https://fcm.googleapis.com/x", "chave", "segredo");

        assertEquals(HttpStatus.NO_CONTENT, controller.registrar(usuarioLogado, request).getStatusCode());
        verify(notificacaoService).registrar(usuarioLogado.getId(), request);
    }

    @Test
    @DisplayName("Remover usa o usuário logado")
    void remover() {
        RemoverDispositivoRequest request = new RemoverDispositivoRequest("https://fcm.googleapis.com/x");

        assertEquals(HttpStatus.NO_CONTENT, controller.remover(usuarioLogado, request).getStatusCode());
        verify(notificacaoService).remover(usuarioLogado.getId(), request.endpoint());
    }

    @Test
    @DisplayName("Devolve a chave pública")
    void chavePublica() {
        when(notificacaoService.chavePublica()).thenReturn("abc");

        assertEquals("abc", controller.chavePublica().getBody().chavePublica());
    }
}
