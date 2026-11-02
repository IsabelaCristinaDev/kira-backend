package br.com.kira.kirabackend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/teste");

    @Test
    @DisplayName("Erro interno responde 500 sem revelar a mensagem original")
    void erroInternoNaoRevelaDetalhe() {
        Exception erro = new IllegalStateException("could not initialize proxy [br.com.kira.Segredo#123]");

        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleGenerico(erro, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
        assertEquals("ERRO_INTERNO", resposta.getBody().erro());
        assertFalse(resposta.getBody().mensagem().contains("proxy"));
        assertFalse(resposta.getBody().mensagem().contains("br.com"));
        assertEquals("/api/teste", resposta.getBody().path());
    }

    @Test
    @DisplayName("Requisição inválida responde 400 com mensagem fixa")
    void requisicaoInvalida() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta =
                handler.handleRequisicaoInvalida(new IllegalArgumentException("detalhe interno"), request);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals("Os dados enviados são inválidos", resposta.getBody().mensagem());
    }

    @Test
    @DisplayName("Rota inexistente responde 404")
    void rotaInexistente() {
        assertEquals(HttpStatus.NOT_FOUND,
                handler.handleRotaInexistente(new IllegalArgumentException("x"), request).getStatusCode());
    }

    @Test
    @DisplayName("Método errado responde 405")
    void metodoNaoPermitido() {
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED,
                handler.handleMetodoNaoPermitido(new IllegalArgumentException("x"), request).getStatusCode());
    }

    @Test
    @DisplayName("Formato não suportado responde 415")
    void formatoNaoSuportado() {
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                handler.handleFormatoNaoSuportado(new IllegalArgumentException("x"), request).getStatusCode());
    }
}
