package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.service.MensagemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAutenticacaoInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private MensagemService mensagemService;

    @Mock
    private ConexoesAoVivo conexoesAoVivo;

    @InjectMocks
    private WebSocketAutenticacaoInterceptor interceptor;

    @Mock
    private MessageChannel canal;

    private final UUID agendamentoId = UUID.randomUUID();
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setEmail("ana@example.com");
    }

    private Message<byte[]> connect(String authorization) {
        StompHeaderAccessor stomp = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            stomp.setNativeHeader("Authorization", authorization);
        }
        stomp.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], stomp.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand comando, String destino, Authentication usuario) {
        StompHeaderAccessor stomp = StompHeaderAccessor.create(comando);
        stomp.setDestination(destino);
        stomp.setUser(usuario);
        stomp.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], stomp.getMessageHeaders());
    }

    private Authentication autenticacao() {
        return new UsernamePasswordAuthenticationToken(cliente, null, List.of());
    }

    private String topico() {
        return "/topic/agendamentos/" + agendamentoId + "/mensagens";
    }

    @Test
    @DisplayName("CONNECT com token válido guarda o usuário na conexão")
    void connectComTokenValido() {
        Instant expira = Instant.now().plusSeconds(60);
        when(jwtTokenProvider.extrairExpiracao("abc")).thenReturn(expira);
        when(jwtTokenProvider.tokenValido("abc")).thenReturn(true);
        when(jwtTokenProvider.extrairEmail("abc")).thenReturn(cliente.getEmail());
        when(userDetailsService.loadUserByUsername(cliente.getEmail())).thenReturn(cliente);
        Message<byte[]> mensagem = connect("Bearer abc");

        Message<?> resultado = interceptor.preSend(mensagem, canal);

        Authentication usuario = (Authentication) StompHeaderAccessor.wrap(resultado).getUser();
        assertSame(cliente, usuario.getPrincipal());
        verify(conexoesAoVivo).registrar(null, cliente.getId(), expira);
    }

    @Test
    @DisplayName("CONNECT sem token é recusado")
    void connectSemToken() {
        Message<byte[]> mensagem = connect(null);

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(mensagem, canal));
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("CONNECT com token inválido é recusado")
    void connectComTokenInvalido() {
        when(jwtTokenProvider.tokenValido("ruim")).thenReturn(false);
        Message<byte[]> mensagem = connect("Bearer ruim");

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(mensagem, canal));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("CONNECT de conta desativada é recusado, mesmo com o token ainda válido")
    void connectDeContaDesativada() {
        cliente.setAtivo(false);
        when(jwtTokenProvider.tokenValido("abc")).thenReturn(true);
        when(jwtTokenProvider.extrairEmail("abc")).thenReturn(cliente.getEmail());
        when(userDetailsService.loadUserByUsername(cliente.getEmail())).thenReturn(cliente);
        Message<byte[]> mensagem = connect("Bearer abc");

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(mensagem, canal));
        verifyNoInteractions(conexoesAoVivo);
    }

    @Test
    @DisplayName("CONNECT de usuário que não existe mais é recusado")
    void connectDeUsuarioInexistente() {
        when(jwtTokenProvider.tokenValido("abc")).thenReturn(true);
        when(jwtTokenProvider.extrairEmail("abc")).thenReturn("sumiu@example.com");
        when(userDetailsService.loadUserByUsername("sumiu@example.com"))
                .thenThrow(new UsernameNotFoundException("não existe"));
        Message<byte[]> mensagem = connect("Bearer abc");

        assertThrows(UsernameNotFoundException.class, () -> interceptor.preSend(mensagem, canal));
    }

    @Test
    @DisplayName("Participante assina a conversa do agendamento")
    void participanteAssina() {
        Message<byte[]> mensagem = frame(StompCommand.SUBSCRIBE, topico(), autenticacao());

        assertEquals(mensagem, interceptor.preSend(mensagem, canal));
        verify(mensagemService).exigirParticipante(agendamentoId, cliente.getId());
    }

    @Test
    @DisplayName("Quem não participa do agendamento não assina a conversa")
    void naoParticipanteNaoAssina() {
        doThrow(new RegraDeNegocioException("Você não participa deste agendamento"))
                .when(mensagemService).exigirParticipante(agendamentoId, cliente.getId());
        Message<byte[]> mensagem = frame(StompCommand.SUBSCRIBE, topico(), autenticacao());

        assertThrows(RegraDeNegocioException.class, () -> interceptor.preSend(mensagem, canal));
    }

    @Test
    @DisplayName("Curinga e outros destinos não podem ser assinados")
    void destinoNaoPermitido() {
        for (String destino : List.of("/topic/**", "/topic/agendamentos/*/mensagens", "/topic/usuarios/*/conversas",
                "/topic/outro", "/user/queue/x")) {
            Message<byte[]> mensagem = frame(StompCommand.SUBSCRIBE, destino, autenticacao());

            assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(mensagem, canal), destino);
        }
        verifyNoInteractions(mensagemService);
    }

    @Test
    @DisplayName("A pessoa assina o próprio canal de conversas")
    void assinaOProprioCanal() {
        Message<byte[]> mensagem = frame(StompCommand.SUBSCRIBE,
                "/topic/usuarios/" + cliente.getId() + "/conversas", autenticacao());

        assertEquals(mensagem, interceptor.preSend(mensagem, canal));
    }

    @Test
    @DisplayName("Ninguém assina o canal de conversas de outra pessoa")
    void naoAssinaCanalDeOutraPessoa() {
        Message<byte[]> mensagem = frame(StompCommand.SUBSCRIBE,
                "/topic/usuarios/" + UUID.randomUUID() + "/conversas", autenticacao());

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(mensagem, canal));
    }

    @Test
    @DisplayName("SUBSCRIBE sem CONNECT autenticado é recusado")
    void assinaturaSemAutenticacao() {
        Message<byte[]> mensagem = frame(StompCommand.SUBSCRIBE, topico(), null);

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(mensagem, canal));
        verifyNoInteractions(mensagemService);
    }

    @Test
    @DisplayName("SEND é sempre recusado: ninguém publica direto no tópico")
    void sendRecusado() {
        Message<byte[]> mensagem = frame(StompCommand.SEND, topico(), autenticacao());

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(mensagem, canal));
    }
}
