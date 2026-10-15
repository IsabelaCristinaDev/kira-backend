package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.dto.request.MensagemRequest;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.service.MensagemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MensagemControllerTest {

    @Mock
    private MensagemService mensagemService;

    @InjectMocks
    private MensagemController controller;

    private Cliente usuarioLogado;
    private MensagemRequest request;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Cliente();
        usuarioLogado.setId(UUID.randomUUID());
        request = new MensagemRequest(UUID.randomUUID(), "Oi");
    }

    @Test
    @DisplayName("Envia a mensagem quando o remetente da URL é o usuário logado")
    void enviaEmNomeProprio() {
        MensagemResponse resposta = new MensagemResponse(UUID.randomUUID(), request.agendamentoId(),
                usuarioLogado.getId(), "Ana", "Oi", false, LocalDateTime.now());
        when(mensagemService.enviar(usuarioLogado.getId(), request)).thenReturn(resposta);

        ResponseEntity<MensagemResponse> response =
                controller.enviar(usuarioLogado.getId(), usuarioLogado, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(resposta, response.getBody());
        verify(mensagemService).enviar(usuarioLogado.getId(), request);
    }

    @Test
    @DisplayName("Não permite enviar mensagem em nome de outro usuário")
    void naoEnviaEmNomeDeOutro() {
        UUID outroUsuario = UUID.randomUUID();

        assertThrows(RegraDeNegocioException.class,
                () -> controller.enviar(outroUsuario, usuarioLogado, request));
        verifyNoInteractions(mensagemService);
    }
}
