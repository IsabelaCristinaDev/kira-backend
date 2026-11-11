package br.com.kira.kirabackend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @DisplayName("Recurso não encontrado responde 404 com a mensagem da exceção")
    void recursoNaoEncontrado() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleRecursoNaoEncontrado(
                new RecursoNaoEncontradoException("Agendamento não encontrado"), request);

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        GlobalExceptionHandler.ErroResponse corpo = resposta.getBody();
        assertEquals(404, corpo.status());
        assertEquals("RECURSO_NAO_ENCONTRADO", corpo.erro());
        assertEquals("Agendamento não encontrado", corpo.mensagem());
        assertEquals("/api/teste", corpo.path());
        assertNotNull(corpo.timestamp());
        assertNull(corpo.erros());
    }

    @Test
    @DisplayName("E-mail já cadastrado responde 409 com código próprio")
    void emailJaCadastrado() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta =
                handler.handleEmailJaCadastrado(new EmailJaCadastradoException(), request);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        GlobalExceptionHandler.ErroResponse corpo = resposta.getBody();
        assertEquals(409, corpo.status());
        assertEquals("EMAIL_JA_CADASTRADO", corpo.erro());
        assertEquals("Email já cadastrado", corpo.mensagem());
        assertEquals("/api/teste", corpo.path());
        assertNull(corpo.erros());
    }

    @Test
    @DisplayName("CPF ou CNPJ já cadastrado responde 409 com código diferente do de e-mail")
    void documentoJaCadastrado() {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleDocumentoJaCadastrado(
                new DocumentoJaCadastradoException("CNPJ já cadastrado"), request);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        GlobalExceptionHandler.ErroResponse corpo = resposta.getBody();
        assertEquals(409, corpo.status());
        assertEquals("DOCUMENTO_JA_CADASTRADO", corpo.erro());
        assertEquals("CNPJ já cadastrado", corpo.mensagem());
        assertEquals("/api/teste", corpo.path());
        assertNull(corpo.erros());
    }

    @Test
    @DisplayName("Validação responde 400 com mensagem fixa e o erro de cada campo, na ordem em que vieram")
    void validacaoListaOsCampos() throws NoSuchMethodException {
        MethodArgumentNotValidException erro = erroDeValidacao(
                new FieldError("request", "email", "E-mail inválido"),
                new FieldError("request", "senha", "A senha precisa ter pelo menos 8 caracteres"));

        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta = handler.handleValidacao(erro, request);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        GlobalExceptionHandler.ErroResponse corpo = resposta.getBody();
        assertEquals(400, corpo.status());
        assertEquals("VALIDACAO", corpo.erro());
        assertEquals("Erro de validação nos campos informados", corpo.mensagem());
        assertEquals("/api/teste", corpo.path());
        assertEquals(List.of("email", "senha"), List.copyOf(corpo.erros().keySet()));
        assertEquals("E-mail inválido", corpo.erros().get("email"));
        assertEquals("A senha precisa ter pelo menos 8 caracteres", corpo.erros().get("senha"));
    }

    @Test
    @DisplayName("Dois erros no mesmo campo aparecem juntos, separados por ponto e vírgula, sem perder nenhum")
    void validacaoJuntaErrosDoMesmoCampo() throws NoSuchMethodException {
        MethodArgumentNotValidException erro = erroDeValidacao(
                new FieldError("request", "senha", "A senha é obrigatória"),
                new FieldError("request", "senha", "A senha precisa ter pelo menos 8 caracteres"));

        Map<String, String> erros = handler.handleValidacao(erro, request).getBody().erros();

        assertEquals(1, erros.size());
        assertEquals("A senha é obrigatória; A senha precisa ter pelo menos 8 caracteres", erros.get("senha"));
    }

    private static MethodArgumentNotValidException erroDeValidacao(FieldError... erros)
            throws NoSuchMethodException {
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(new Object(), "request");
        for (FieldError erro : erros) {
            resultado.addError(erro);
        }
        MethodParameter parametro = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
        return new MethodArgumentNotValidException(parametro, resultado);
    }
}
