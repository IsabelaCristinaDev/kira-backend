package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConexoesAoVivoTest {

    @Mock
    private MessageChannel saida;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ConexoesAoVivo conexoes;

    private final UUID ana = UUID.randomUUID();
    private final Instant depois = Instant.now().plusSeconds(60);

    private StompHeaderAccessor erroEnviado() {
        ArgumentCaptor<Message<?>> mensagem = ArgumentCaptor.captor();
        verify(saida).send(mensagem.capture());
        return StompHeaderAccessor.wrap(mensagem.getValue());
    }

    @Test
    @DisplayName("Token vencido encerra a conexão com a mensagem que faz o app renovar")
    void tokenVencido() {
        conexoes.registrar("s1", ana, Instant.now().minusSeconds(1));

        conexoes.verificar();

        StompHeaderAccessor erro = erroEnviado();
        assertEquals(StompCommand.ERROR, erro.getCommand());
        assertEquals("s1", erro.getSessionId());
        assertEquals("Token expirado", erro.getMessage());
    }

    @Test
    @DisplayName("Conta desativada encerra a conexão, mesmo com o token ainda válido")
    void contaDesativada() {
        conexoes.registrar("s1", ana, depois);
        when(usuarioRepository.findIdsAtivos(anyCollection())).thenReturn(Set.of());

        conexoes.verificar();

        assertEquals("Conta desativada", erroEnviado().getMessage());
    }

    @Test
    @DisplayName("Token válido e conta ativa mantêm a conexão")
    void conexaoValida() {
        conexoes.registrar("s1", ana, depois);
        when(usuarioRepository.findIdsAtivos(Set.of(ana))).thenReturn(Set.of(ana));

        conexoes.verificar();

        verify(saida, never()).send(any());
    }

    @Test
    @DisplayName("Conexão encerrada não é encerrada de novo na verificação seguinte")
    void encerraUmaVez() {
        conexoes.registrar("s1", ana, Instant.now().minusSeconds(1));

        conexoes.verificar();
        conexoes.verificar();

        verify(saida, times(1)).send(any());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("Conexão fechada pelo cliente deixa de ser acompanhada")
    void clienteDesconectou() {
        conexoes.registrar("s1", ana, Instant.now().minusSeconds(1));
        Message<byte[]> fim = org.springframework.messaging.support.MessageBuilder.createMessage(
                new byte[0], StompHeaderAccessor.create(StompCommand.DISCONNECT).getMessageHeaders());

        conexoes.aoDesconectar(new SessionDisconnectEvent(this, fim, "s1", CloseStatus.NORMAL));
        conexoes.verificar();

        verifyNoInteractions(saida, usuarioRepository);
    }
}
