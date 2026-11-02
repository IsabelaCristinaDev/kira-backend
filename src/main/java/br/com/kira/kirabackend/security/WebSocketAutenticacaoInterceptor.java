package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.service.MensagemService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Segurança do WebSocket, comando a comando:
 * CONNECT exige um token válido, SUBSCRIBE só vale para a conversa de um agendamento de que a pessoa
 * participa, e SEND é sempre recusado, senão daria para publicar mensagem falsa direto no tópico.
 * Qualquer recusa aqui encerra a conexão com um frame ERROR.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAutenticacaoInterceptor implements ChannelInterceptor {

    private static final Pattern TOPICO_DA_CONVERSA = Pattern.compile(
            "^/topic/agendamentos/([0-9a-fA-F-]{36})/mensagens$");

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final MensagemService mensagemService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        StompCommand comando = stomp.getCommand();
        if (comando == StompCommand.CONNECT) {
            stomp.setUser(autenticar(stomp));
        } else if (comando == StompCommand.SUBSCRIBE) {
            validarAssinatura(stomp);
        } else if (comando == StompCommand.SEND) {
            throw new MessageDeliveryException("O envio de mensagens é feito pela API");
        }
        return message;
    }

    private Authentication autenticar(StompHeaderAccessor stomp) {
        String header = stomp.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Token ausente");
        }
        String token = header.substring(7);
        if (!jwtTokenProvider.tokenValido(token)) {
            throw new MessageDeliveryException("Token inválido");
        }
        UserDetails usuario = userDetailsService.loadUserByUsername(jwtTokenProvider.extrairEmail(token));
        if (!usuario.isEnabled()) {
            throw new MessageDeliveryException("Conta desativada");
        }
        return new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
    }

    private void validarAssinatura(StompHeaderAccessor stomp) {
        if (!(stomp.getUser() instanceof Authentication autenticacao)
                || !(autenticacao.getPrincipal() instanceof Usuario usuario)) {
            throw new MessageDeliveryException("Conexão sem autenticação");
        }
        String destino = stomp.getDestination();
        Matcher conversa = destino == null ? null : TOPICO_DA_CONVERSA.matcher(destino);
        if (conversa == null || !conversa.matches()) {
            throw new MessageDeliveryException("Destino não permitido");
        }
        mensagemService.exigirParticipante(UUID.fromString(conversa.group(1)), usuario.getId());
    }
}
