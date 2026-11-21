package br.com.kira.kirabackend.exception;

import br.com.kira.kirabackend.exception.CodigoChegadaException.Motivo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerChegadaTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/api/agendamentos/1/chegada/validar");

    @ParameterizedTest
    @EnumSource(Motivo.class)
    void motivoVaiNoCampoErro(Motivo motivo) {
        ResponseEntity<GlobalExceptionHandler.ErroResponse> resposta =
                handler.handleCodigoChegada(new CodigoChegadaException(motivo), request);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals(motivo.name(), resposta.getBody().erro());
        assertEquals(new CodigoChegadaException(motivo).getMessage(), resposta.getBody().mensagem());
    }
}
