package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.dto.response.VerificacaoResponse;
import br.com.kira.kirabackend.service.VerificacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificacaoControllerTest {

    @Mock
    private VerificacaoService verificacaoService;

    @InjectMocks
    private VerificacaoController controller;

    private Cliente usuarioLogado;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Cliente();
        usuarioLogado.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Enviar repassa as fotos (frente, verso e selfie), o tipo, o consentimento e o usuário logado")
    void enviar() {
        MockMultipartFile documento = new MockMultipartFile("document", "d.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile verso = new MockMultipartFile("document_back", "v.jpg", "image/jpeg", new byte[]{3});
        MockMultipartFile selfie = new MockMultipartFile("selfie", "s.jpg", "image/jpeg", new byte[]{2});
        VerificacaoResponse esperado = new VerificacaoResponse(StatusVerificacao.VERIFIED, List.of(), 2);
        when(verificacaoService.enviar(usuarioLogado, documento, verso, selfie, "RG", true)).thenReturn(esperado);

        var resposta = controller.enviar(usuarioLogado, documento, verso, selfie, "RG", true);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(esperado, resposta.getBody());
    }

    @Test
    @DisplayName("Consultar usa só o id do usuário logado")
    void consultar() {
        VerificacaoResponse esperado = new VerificacaoResponse(StatusVerificacao.IN_REVIEW, List.of("LOW_IMAGE_QUALITY"), 2);
        when(verificacaoService.consultar(usuarioLogado.getId())).thenReturn(esperado);

        assertEquals(esperado, controller.consultar(usuarioLogado).getBody());
    }
}
