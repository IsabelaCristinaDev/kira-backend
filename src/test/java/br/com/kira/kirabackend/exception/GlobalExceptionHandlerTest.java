package br.com.kira.kirabackend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    @DisplayName("Recurso de outra conta responde 403 com a mensagem genérica, sem o código da verificação")
    void acessoNegado() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleAcessoNegado(
                new AcessoNegadoException("Você só pode acessar os dados do seu estabelecimento"), request);

        assertEquals(HttpStatus.FORBIDDEN, resposta.getStatusCode());
        assertEquals(403, resposta.getBody().status());
        assertEquals("ACESSO_NEGADO", resposta.getBody().erro());
        assertEquals("Você só pode acessar os dados do seu estabelecimento", resposta.getBody().mensagem());
        assertEquals("/api/teste", resposta.getBody().path());
    }

    @Test
    @DisplayName("E-mail já cadastrado responde 409 com o código EMAIL_JA_CADASTRADO")
    void emailJaCadastrado() {
        // Act
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta =
                handler.handleEmailJaCadastrado(new EmailJaCadastradoException(), request);

        // Assert
        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals(409, resposta.getBody().status());
        assertEquals("EMAIL_JA_CADASTRADO", resposta.getBody().erro());
        assertEquals("Email já cadastrado", resposta.getBody().mensagem());
        assertEquals("/api/teste", resposta.getBody().path());
        assertNull(resposta.getBody().erros());
    }

    @Test
    @DisplayName("CPF ou CNPJ já cadastrado responde 409 com o código DOCUMENTO_JA_CADASTRADO e a mensagem da exceção")
    void documentoJaCadastrado() {
        // Act
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleDocumentoJaCadastrado(
                new DocumentoJaCadastradoException("CPF já cadastrado"), request);

        // Assert
        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals(409, resposta.getBody().status());
        assertEquals("DOCUMENTO_JA_CADASTRADO", resposta.getBody().erro());
        assertEquals("CPF já cadastrado", resposta.getBody().mensagem());
        assertEquals("/api/teste", resposta.getBody().path());
        assertNull(resposta.getBody().erros());
    }

    @Test
    @DisplayName("Validação responde 400 e junta com '; ' os erros do mesmo campo, na ordem em que vieram")
    void validacaoJuntaErrosDoMesmoCampo() {
        // Arrange
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(new Object(), "cadastroRequest");
        resultado.addError(new FieldError("cadastroRequest", "senha", "A senha deve ter no mínimo 8 caracteres"));
        resultado.addError(new FieldError("cadastroRequest", "email", "E-mail inválido"));
        resultado.addError(new FieldError("cadastroRequest", "senha", "A senha deve ter letras e números"));
        MethodArgumentNotValidException erro = new MethodArgumentNotValidException(null, resultado);

        // Act
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleValidacao(erro, request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals("VALIDACAO", resposta.getBody().erro());
        assertEquals("Erro de validação nos campos informados", resposta.getBody().mensagem());
        Map<String, String> erros = resposta.getBody().erros();
        assertEquals(List.of("senha", "email"), List.copyOf(erros.keySet()));
        assertEquals("A senha deve ter no mínimo 8 caracteres; A senha deve ter letras e números", erros.get("senha"));
        assertEquals("E-mail inválido", erros.get("email"));
    }
}
