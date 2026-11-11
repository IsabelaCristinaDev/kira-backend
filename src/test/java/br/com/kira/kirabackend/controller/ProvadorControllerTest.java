package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.dto.request.ConsentimentoProvadorRequest;
import br.com.kira.kirabackend.dto.response.CompatibilidadeEstudioResponse;
import br.com.kira.kirabackend.dto.response.ConsentimentoProvadorResponse;
import br.com.kira.kirabackend.dto.response.GeracaoProvadorResponse;
import br.com.kira.kirabackend.service.CompatibilidadePortfolioService;
import br.com.kira.kirabackend.service.ProvadorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvadorControllerTest {

    @Mock
    private ProvadorService provadorService;

    @Mock
    private CompatibilidadePortfolioService compatibilidadeService;

    @InjectMocks
    private ProvadorController controller;

    private Cliente usuarioLogado;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Cliente();
        usuarioLogado.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Consultar usa o usuário logado")
    void consultar() {
        ConsentimentoProvadorResponse esperado = new ConsentimentoProvadorResponse(true, "v1", LocalDateTime.now());
        when(provadorService.consultarConsentimento(usuarioLogado.getId())).thenReturn(esperado);

        assertEquals(esperado, controller.consultarConsentimento(usuarioLogado).getBody());
    }

    @Test
    @DisplayName("Aceitar manda a versão do termo e o usuário logado")
    void aceitar() {
        ConsentimentoProvadorResponse esperado = new ConsentimentoProvadorResponse(true, "v1", LocalDateTime.now());
        when(provadorService.aceitarConsentimento(usuarioLogado.getId(), "v1")).thenReturn(esperado);

        var resposta = controller.aceitarConsentimento(usuarioLogado, new ConsentimentoProvadorRequest("v1"));

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(esperado, resposta.getBody());
    }

    @Test
    @DisplayName("Revogar usa o usuário logado")
    void revogar() {
        ConsentimentoProvadorResponse esperado = new ConsentimentoProvadorResponse(false, "v1", LocalDateTime.now());
        when(provadorService.revogarConsentimento(usuarioLogado.getId())).thenReturn(esperado);

        assertEquals(esperado, controller.revogarConsentimento(usuarioLogado).getBody());
    }

    @Test
    @DisplayName("Gerar repassa as duas fotos e o usuário logado")
    void gerar() {
        MockMultipartFile cliente = new MockMultipartFile("cliente", "c.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile referencia = new MockMultipartFile("referencia", "r.jpg", "image/jpeg", new byte[]{2});
        GeracaoProvadorResponse esperado = new GeracaoProvadorResponse("data:image/png;base64,AAAA", LocalDateTime.now());
        when(provadorService.gerar(usuarioLogado.getId(), cliente, referencia)).thenReturn(esperado);

        var resposta = controller.gerar(usuarioLogado, cliente, referencia);

        assertEquals(esperado, resposta.getBody());
        verify(provadorService).gerar(usuarioLogado.getId(), cliente, referencia);
    }

    @Test
    @DisplayName("Apagar fotos responde 204, pois nada fica guardado")
    void apagarFotos() {
        var resposta = controller.apagarFotos();

        assertEquals(HttpStatus.NO_CONTENT, resposta.getStatusCode());
        assertNull(resposta.getBody());
    }

    @Test
    @DisplayName("Ranking repassa a foto de referência e devolve os estúdios na ordem do serviço")
    void ranking() {
        MockMultipartFile referencia = new MockMultipartFile("referencia", "r.jpg", "image/jpeg", new byte[]{2});
        List<CompatibilidadeEstudioResponse> esperado =
                List.of(new CompatibilidadeEstudioResponse(UUID.randomUUID(), 80.5));
        when(compatibilidadeService.ranquear(referencia)).thenReturn(esperado);

        var resposta = controller.ranking(referencia);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(esperado, resposta.getBody());
    }
}
