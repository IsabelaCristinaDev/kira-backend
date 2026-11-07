package br.com.kira.kirabackend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerVerificacaoTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/verificacao");

    @Test
    @DisplayName("Serviço de verificação fora do ar responde 503 para o app tentar de novo")
    void indisponivel() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleVerificacaoIndisponivel(
                new VerificacaoIndisponivelException("timeout", new RuntimeException()), request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resposta.getStatusCode());
        assertEquals("VERIFICACAO_INDISPONIVEL", resposta.getBody().erro());
    }

    @Test
    @DisplayName("Falha do serviço responde 502 sem revelar o detalhe")
    void falhou() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleFalhaVerificacao(
                new FalhaVerificacaoException("Chave do serviço de verificação recusada"), request);

        assertEquals(HttpStatus.BAD_GATEWAY, resposta.getStatusCode());
        assertEquals("VERIFICACAO_FALHOU", resposta.getBody().erro());
        assertFalse(resposta.getBody().mensagem().toLowerCase().contains("chave"));
    }

    @Test
    @DisplayName("Conta sem verificação, com a verificação obrigatória, responde 403 com o código próprio")
    void obrigatoria() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleVerificacaoObrigatoria(
                new VerificacaoObrigatoriaException("Verifique sua identidade para agendar"), request);

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        assertEquals("VERIFICACAO_OBRIGATORIA", resposta.getBody().erro());
        assertEquals("Verifique sua identidade para agendar", resposta.getBody().mensagem());
    }
}
