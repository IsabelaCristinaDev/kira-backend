package br.com.kira.kirabackend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerProvadorTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/provador/geracoes");

    @Test
    @DisplayName("Falta de consentimento responde 428 com código próprio")
    void consentimentoNecessario() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta =
                handler.handleConsentimentoProvador(new ConsentimentoProvadorNecessarioException(), request);

        assertEquals(HttpStatus.PRECONDITION_REQUIRED, resposta.getStatusCode());
        assertEquals("PROVADOR_CONSENTIMENTO_NECESSARIO", resposta.getBody().erro());
    }

    @Test
    @DisplayName("Falha na geração responde 502 sem revelar o provedor nem o detalhe")
    void geracaoFalhou() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleGeracaoProvador(
                new GeracaoProvadorException("Cloudflare respondeu 500 para a conta abc"), request);

        assertEquals(HttpStatus.BAD_GATEWAY, resposta.getStatusCode());
        assertEquals("PROVADOR_GERACAO_FALHOU", resposta.getBody().erro());
        assertFalse(resposta.getBody().mensagem().toLowerCase().contains("cloudflare"));
        assertFalse(resposta.getBody().mensagem().contains("abc"));
    }

    @Test
    @DisplayName("Upload acima do limite responde 413")
    void arquivoGrande() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta =
                handler.handleArquivoGrande(new MaxUploadSizeExceededException(6L), request);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, resposta.getStatusCode());
        assertEquals("ARQUIVO_MUITO_GRANDE", resposta.getBody().erro());
    }
}
