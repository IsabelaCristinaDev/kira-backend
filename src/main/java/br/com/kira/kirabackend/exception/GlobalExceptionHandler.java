package br.com.kira.kirabackend.exception;

import br.com.kira.kirabackend.util.KiraTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    // 403: a conta logada não é dona do recurso. O código é outro que o da verificação obrigatória, que também é 403
    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResponse> handleAcessoNegado(
            AcessoNegadoException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", ex.getMessage(), request);
    }

    // 428: falta uma condição prévia (o consentimento). Não é 403, que o app trata como falta de permissão
    @ExceptionHandler(ConsentimentoProvadorNecessarioException.class)
    public ResponseEntity<ErroResponse> handleConsentimentoProvador(
            ConsentimentoProvadorNecessarioException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.PRECONDITION_REQUIRED, "PROVADOR_CONSENTIMENTO_NECESSARIO",
                "Aceite o termo do Provador virtual para continuar", request);
    }

    // O detalhe vem do log; ao app vai uma mensagem fixa que não cita o provedor da IA
    @ExceptionHandler(GeracaoProvadorException.class)
    public ResponseEntity<ErroResponse> handleGeracaoProvador(
            GeracaoProvadorException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.BAD_GATEWAY, "PROVADOR_GERACAO_FALHOU",
                "Não foi possível gerar a prévia agora. Tente novamente", request);
    }

    @ExceptionHandler(LimiteProvadorException.class)
    public ResponseEntity<ErroResponse> handleLimiteProvador(
            LimiteProvadorException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, "PROVADOR_LIMITE_DIARIO", ex.getMessage(), request);
    }

    // 503: a IA de compatibilidade está fora do ar. O app segue com a ordem que já calcula
    @ExceptionHandler(CompatibilidadeIndisponivelException.class)
    public ResponseEntity<ErroResponse> handleCompatibilidadeIndisponivel(
            CompatibilidadeIndisponivelException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "PROVADOR_RANKING_INDISPONIVEL",
                "Não foi possível comparar com os portfólios agora", request);
    }

    // 503: o serviço de verificação está fora do ar ou demorou. A tentativa continua pendente e o app pode reenviar
    @ExceptionHandler(VerificacaoIndisponivelException.class)
    public ResponseEntity<ErroResponse> handleVerificacaoIndisponivel(
            VerificacaoIndisponivelException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "VERIFICACAO_INDISPONIVEL",
                "A verificação está indisponível agora. Tente novamente em instantes", request);
    }

    // 403: a ação exige a identidade verificada (VERIFICACAO_OBRIGATORIA=true). O app mostra o caminho para verificar
    @ExceptionHandler(VerificacaoObrigatoriaException.class)
    public ResponseEntity<ErroResponse> handleVerificacaoObrigatoria(
            VerificacaoObrigatoriaException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.FORBIDDEN, "VERIFICACAO_OBRIGATORIA", ex.getMessage(), request);
    }

    // 502: o serviço de verificação recusou o pedido (ex.: chave errada). O detalhe fica só no log
    @ExceptionHandler(FalhaVerificacaoException.class)
    public ResponseEntity<ErroResponse> handleFalhaVerificacao(
            FalhaVerificacaoException ex,
            HttpServletRequest request) {
        return resposta(HttpStatus.BAD_GATEWAY, "VERIFICACAO_FALHOU",
                "Não foi possível verificar sua identidade agora. Tente novamente mais tarde", request);
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

    @ExceptionHandler(DocumentoJaCadastradoException.class)
    public ResponseEntity<ErroResponse> handleDocumentoJaCadastrado(
            DocumentoJaCadastradoException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErroResponse(
                        LocalDateTime.now(KiraTimeZone.DEFAULT),
                        409,
                        "DOCUMENTO_JA_CADASTRADO",
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

    // Erros da requisição, não do servidor. As mensagens são fixas: as do Spring citam classes e campos internos.
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ErroResponse> handleRequisicaoInvalida(Exception ex, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", "Os dados enviados são inválidos", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResponse> handleArquivoGrande(Exception ex, HttpServletRequest request) {
        return resposta(HttpStatus.PAYLOAD_TOO_LARGE, "ARQUIVO_GRANDE", "O arquivo pode ter no máximo 5 MB", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> handleRotaInexistente(Exception ex, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Endereço não encontrado", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> handleMetodoNaoPermitido(Exception ex, HttpServletRequest request) {
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_PERMITIDO", "Operação não permitida neste endereço", request);
    }

    @ExceptionHandler(HttpMediaTypeException.class)
    public ResponseEntity<ErroResponse> handleFormatoNaoSuportado(Exception ex, HttpServletRequest request) {
        return resposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FORMATO_NAO_SUPORTADO", "Formato dos dados não suportado", request);
    }

    // O detalhe do erro fica só no log: a resposta não pode revelar nada do funcionamento interno
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGenerico(
            Exception ex,
            HttpServletRequest request) {
        log.error("Erro interno em {} {}", request.getMethod(), request.getRequestURI(), ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Ocorreu um erro interno. Tente novamente.", request);
    }

    private static ResponseEntity<ErroResponse> resposta(
            HttpStatus status, String erro, String mensagem, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErroResponse(
                LocalDateTime.now(KiraTimeZone.DEFAULT),
                status.value(),
                erro,
                mensagem,
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
