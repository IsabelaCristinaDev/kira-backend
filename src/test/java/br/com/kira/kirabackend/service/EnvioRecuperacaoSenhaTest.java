package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.TokenRecuperacaoSenha;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvioRecuperacaoSenhaTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EnvioRecuperacaoSenha envio;

    private Cliente usuario;

    @BeforeEach
    void setUp() {
        usuario = new Cliente();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("ana@teste.com");
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT);
    }

    @Test
    @DisplayName("E-mail sem conta: não grava nem envia nada")
    void emailInexistente() {
        when(usuarioRepository.findByEmailIgnoreCase("ninguem@teste.com")).thenReturn(Optional.empty());

        envio.gerarEEnviar("ninguem@teste.com");

        verifyNoInteractions(tokenRepository, emailService);
    }

    @Test
    @DisplayName("Apaga os tokens anteriores, guarda só o hash do novo e envia o token original por e-mail")
    void guardaHashEEnviaOriginal() {
        when(usuarioRepository.findByEmailIgnoreCase("ana@teste.com")).thenReturn(Optional.of(usuario));
        LocalDateTime antes = agora();

        envio.gerarEEnviar("ana@teste.com");

        LocalDateTime depois = agora();
        InOrder ordem = inOrder(tokenRepository, emailService);
        ordem.verify(tokenRepository).deleteByUsuarioId(usuario.getId());
        ArgumentCaptor<TokenRecuperacaoSenha> salvo = ArgumentCaptor.forClass(TokenRecuperacaoSenha.class);
        ordem.verify(tokenRepository).save(salvo.capture());
        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        ordem.verify(emailService).enviarEmailRecuperacaoSenha(eq("ana@teste.com"), enviado.capture());

        String token = enviado.getValue();
        TokenRecuperacaoSenha registro = salvo.getValue();
        // O banco nunca vê o token: só o SHA-256 dele, em hexadecimal
        assertNotEquals(token, registro.getTokenHash());
        assertEquals(TokenRecuperacaoSenha.hashDoToken(token), registro.getTokenHash());
        assertTrue(registro.getTokenHash().matches("[0-9a-f]{64}"), registro.getTokenHash());
        assertSame(usuario, registro.getUsuario());
        assertFalse(registro.getUsado());

        // Expira em 15 minutos
        assertFalse(registro.getDataExpiracao().isBefore(antes.plusMinutes(15)));
        assertFalse(registro.getDataExpiracao().isAfter(depois.plusMinutes(15)));
    }

    @Test
    @DisplayName("O token tem 32 bytes aleatórios, em Base64 próprio para URL")
    void tokenDe32Bytes() {
        when(usuarioRepository.findByEmailIgnoreCase("ana@teste.com")).thenReturn(Optional.of(usuario));

        envio.gerarEEnviar("ana@teste.com");

        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarEmailRecuperacaoSenha(eq("ana@teste.com"), enviado.capture());
        assertTrue(enviado.getValue().matches("[A-Za-z0-9_-]+"), enviado.getValue());
        assertEquals(32, Base64.getUrlDecoder().decode(enviado.getValue()).length);
    }

    @Test
    @DisplayName("Cada pedido gera um token diferente")
    void tokensDiferentes() {
        when(usuarioRepository.findByEmailIgnoreCase("ana@teste.com")).thenReturn(Optional.of(usuario));

        envio.gerarEEnviar("ana@teste.com");
        envio.gerarEEnviar("ana@teste.com");

        ArgumentCaptor<String> enviados = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).enviarEmailRecuperacaoSenha(anyString(), enviados.capture());
        assertNotEquals(enviados.getAllValues().get(0), enviados.getAllValues().get(1));
    }
}
