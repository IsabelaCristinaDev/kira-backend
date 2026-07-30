package br.com.kira.kirabackend.exception;

import br.com.kira.kirabackend.util.KiraTimeZone;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroResponse(
                        LocalDateTime.now(KiraTimeZone.DEFAULT),
                        401,
                        "CREDENCIAIS_INVALIDAS",
                        "Usuário inexistente ou senha inválida",
                        request.getRequestURI(),
                        null));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleRecursoNaoEncontrado(
            RecursoNaoEncontradoException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErroResponse(
                        LocalDateTime.now(KiraTimeZone.DEFAULT),
                        404,
                        "RECURSO_NAO_ENCONTRADO",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null));
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> handleRegraDeNegocio(
            RegraDeNegocioException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResponse(
                        LocalDateTime.now(KiraTimeZone.DEFAULT),
                        400,
                        "REGRA_DE_NEGOCIO",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null));
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErroResponse> handleEmailJaCadastrado(
            EmailJaCadastradoException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErroResponse(
                        LocalDateTime.now(KiraTimeZone.DEFAULT),
                        409,
                        "EMAIL_JA_CADASTRADO",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String campo = ((FieldError) error).getField();
            String mensagem = error.getDefaultMessage();
            erros.merge(campo, mensagem, (existente, novo) -> existente + "; " + novo);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResponse(
                        LocalDateTime.now(KiraTimeZone.DEFAULT),
                        400,
                        "VALIDACAO",
                        "Erro de validação nos campos informados",
                        request.getRequestURI(),
                        erros));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGenerico(
            Exception ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroResponse(
                        LocalDateTime.now(KiraTimeZone.DEFAULT),
                        500,
                        "ERRO_INTERNO",
                        ex.getMessage(),
                        request.getRequestURI(),
                        null));
    }

    public record ErroResponse(
            LocalDateTime timestamp,
            int status,
            String erro,
            String mensagem,
            String path,
            Map<String, String> erros
    ) {}
}
