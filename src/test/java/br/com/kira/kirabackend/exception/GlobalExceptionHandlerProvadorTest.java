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
    @DisplayName("Limite do dia responde 429 com a mensagem para a pessoa")
    void limiteDoDia() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta =
                handler.handleLimiteProvador(new LimiteProvadorException(5), request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, resposta.getStatusCode());
        assertEquals("PROVADOR_LIMITE_DIARIO", resposta.getBody().erro());
        assertEquals("Você chegou ao limite de 5 prévias por dia. Tente novamente amanhã", resposta.getBody().mensagem());
    }

    @Test
    @DisplayName("Upload acima do limite responde 413")
    void arquivoGrande() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta =
                handler.handleArquivoGrande(new MaxUploadSizeExceededException(6L), request);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, resposta.getStatusCode());
        assertEquals("ARQUIVO_GRANDE", resposta.getBody().erro());
    }

    @Test
    @DisplayName("IA de compatibilidade fora do ar responde 503 sem revelar o detalhe")
    void compatibilidadeIndisponivel() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleCompatibilidadeIndisponivel(
                new CompatibilidadeIndisponivelException("kira-ai respondeu 500 em http://10.0.0.5", null), request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resposta.getStatusCode());
        assertEquals("PROVADOR_RANKING_INDISPONIVEL", resposta.getBody().erro());
        assertFalse(resposta.getBody().mensagem().contains("kira-ai"));
        assertFalse(resposta.getBody().mensagem().contains("10.0.0.5"));
    }
}
