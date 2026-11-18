package br.com.kira.kirabackend.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Único lugar que decide o IP de quem fez a requisição.
 * <p>
 * Usa o {@code getRemoteAddr()} do request já tratado pelo Spring, nunca o X-Forwarded-For lido à mão,
 * porque o cliente consegue inventar esse header. Atrás de um proxy, quem faz o Spring trocar o endereço
 * do proxy pelo do cliente é o {@code server.forward-headers-strategy} (variável FORWARD_HEADERS_STRATEGY).
 */
@Slf4j
@Component
public class IpDoCliente {

    private final boolean registrarHeaders;

    public IpDoCliente(@Value("${kira.diagnostico.registrar-headers-ip:false}") boolean registrarHeaders) {
        this.registrarHeaders = registrarHeaders;
    }

    public String de(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        // TODO(LGPD): remover este log (e a variável REGISTRAR_HEADERS_IP) depois do primeiro deploy no Render.
        // Ele existe só para descobrir quais headers o proxy do Render manda e escolher o
        // FORWARD_HEADERS_STRATEGY certo. IP é dado pessoal: não deve ficar no log sem necessidade.
        if (registrarHeaders) {
            log.info("Diagnóstico de IP: remoteAddr={} X-Forwarded-For={} True-Client-IP={} CF-Connecting-IP={} X-Real-IP={}",
                    ip,
                    request.getHeader("X-Forwarded-For"),
                    request.getHeader("True-Client-IP"),
                    request.getHeader("CF-Connecting-IP"),
                    request.getHeader("X-Real-IP"));
        }
        return ip;
    }
}
