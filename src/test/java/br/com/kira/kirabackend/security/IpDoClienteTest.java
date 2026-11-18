package br.com.kira.kirabackend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class IpDoClienteTest {

    private static MockHttpServletRequest requisicao() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        // O cliente pode inventar este header: ele nunca é lido para decidir o IP
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        return request;
    }

    @Test
    @DisplayName("O IP é o do request já tratado pelo Spring, nunca o X-Forwarded-For lido à mão")
    void usaRemoteAddr() {
        assertEquals("10.0.0.7", new IpDoCliente(false).de(requisicao()));
    }

    @Test
    @DisplayName("Com o diagnóstico desligado, nenhum header de IP vai para o log")
    void diagnosticoDesligado(CapturedOutput saida) {
        new IpDoCliente(false).de(requisicao());

        assertFalse(saida.getOut().contains("1.2.3.4"), saida.getOut());
    }

    @Test
    @DisplayName("Com o diagnóstico ligado, os headers de IP vão para o log")
    void diagnosticoLigado(CapturedOutput saida) {
        new IpDoCliente(true).de(requisicao());

        assertTrue(saida.getOut().contains("X-Forwarded-For=1.2.3.4"), saida.getOut());
        assertTrue(saida.getOut().contains("remoteAddr=10.0.0.7"), saida.getOut());
    }
}
