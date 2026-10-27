package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.TokenRecuperacaoSenha;
import br.com.kira.kirabackend.dto.request.RecuperacaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RecuperacaoSenhaService service;

    private Cliente usuario;

    @BeforeEach
    void setUp() {
        usuario = new Cliente();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("ana@teste.com");
        usuario.setSenhaHash("hash-antigo");
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT);
    }

    private TokenRecuperacaoSenha token(LocalDateTime expiracao, boolean usado) {
        TokenRecuperacaoSenha token = new TokenRecuperacaoSenha();
        token.setToken("token-123");
        token.setUsuario(usuario);
        token.setDataExpiracao(expiracao);
        token.setUsado(usado);
        return token;
    }

    // ---------- solicitar recuperação ----------

    @Nested
    @DisplayName("solicitarRecuperacao")
    class SolicitarRecuperacao {

        @Test
        @DisplayName("E-mail inexistente não dá erro nem envia nada, para não revelar quais e-mails estão cadastrados")
        void emailInexistenteNaoRevelaNada() {
            when(usuarioRepository.findByEmailIgnoreCase("ninguem@teste.com")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> service.solicitarRecuperacao(new RecuperacaoSenhaRequest("ninguem@teste.com")));

            verifyNoInteractions(tokenRepository, emailService);
        }

        @Test
        @DisplayName("Gera token válido por 30 minutos, apaga os anteriores e envia o e-mail")
        void geraTokenApagaAnterioresEEnviaEmail() {
            when(usuarioRepository.findByEmailIgnoreCase("ana@teste.com")).thenReturn(Optional.of(usuario));
            LocalDateTime antes = agora();

            service.solicitarRecuperacao(new RecuperacaoSenhaRequest("ana@teste.com"));

            LocalDateTime depois = agora();

            // Apaga os tokens anteriores antes de salvar o novo.
            InOrder ordem = inOrder(tokenRepository, emailService);
            ordem.verify(tokenRepository).deleteByUsuarioId(usuario.getId());
            ArgumentCaptor<TokenRecuperacaoSenha> captor = ArgumentCaptor.forClass(TokenRecuperacaoSenha.class);
            ordem.verify(tokenRepository).save(captor.capture());
            TokenRecuperacaoSenha salvo = captor.getValue();
            ordem.verify(emailService).enviarEmailRecuperacaoSenha("ana@teste.com", salvo.getToken());

            assertSame(usuario, salvo.getUsuario());
            assertFalse(salvo.getUsado());
            assertFalse(salvo.isExpirado());
            assertDoesNotThrow(() -> UUID.fromString(salvo.getToken()));

            // Expira em 30 minutos a partir de agora.
            assertFalse(salvo.getDataExpiracao().isBefore(antes.plusMinutes(30)));
            assertFalse(salvo.getDataExpiracao().isAfter(depois.plusMinutes(30)));
        }

        @Test
        @DisplayName("Cada solicitação gera um token diferente")
        void cadaSolicitacaoGeraTokenDiferente() {
            when(usuarioRepository.findByEmailIgnoreCase("ana@teste.com")).thenReturn(Optional.of(usuario));

            service.solicitarRecuperacao(new RecuperacaoSenhaRequest("ana@teste.com"));
            service.solicitarRecuperacao(new RecuperacaoSenhaRequest("ana@teste.com"));

            ArgumentCaptor<TokenRecuperacaoSenha> captor = ArgumentCaptor.forClass(TokenRecuperacaoSenha.class);
            verify(tokenRepository, times(2)).save(captor.capture());
            assertNotEquals(captor.getAllValues().get(0).getToken(), captor.getAllValues().get(1).getToken());
        }
    }

    // ---------- redefinir senha ----------

    @Nested
    @DisplayName("redefinirSenha")
    class RedefinirSenha {

        @Test
        @DisplayName("Redefinir criptografa a nova senha e marca o token como usado")
        void redefineSenhaEMarcaTokenComoUsado() {
            TokenRecuperacaoSenha token = token(agora().plusMinutes(10), false);
            when(tokenRepository.findByToken("token-123")).thenReturn(Optional.of(token));
            when(passwordEncoder.encode("novaSenha123")).thenReturn("$2a$10$hash-novo");

            service.redefinirSenha(new RedefinicaoSenhaRequest("token-123", "novaSenha123"));

            assertEquals("$2a$10$hash-novo", usuario.getSenhaHash());
            assertNotEquals("novaSenha123", usuario.getSenhaHash());
            verify(usuarioRepository).save(usuario);

            assertTrue(token.getUsado());
            verify(tokenRepository).save(token);
        }

        @Test
        @DisplayName("Redefinir com token inválido dá erro")
        void tokenInvalido() {
            when(tokenRepository.findByToken("nao-existe")).thenReturn(Optional.empty());

            RedefinicaoSenhaRequest req = new RedefinicaoSenhaRequest("nao-existe", "novaSenha123");

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.redefinirSenha(req));

            assertEquals("Token inválido", ex.getMessage());
            assertNadaAlterado();
        }

        @Test
        @DisplayName("Redefinir com token expirado dá erro")
        void tokenExpirado() {
            TokenRecuperacaoSenha token = token(agora().minus(Duration.ofSeconds(1)), false);
            when(tokenRepository.findByToken("token-123")).thenReturn(Optional.of(token));

            RedefinicaoSenhaRequest req = new RedefinicaoSenhaRequest("token-123", "novaSenha123");

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.redefinirSenha(req));

            assertEquals("Token expirado", ex.getMessage());
            assertFalse(token.getUsado());
            assertNadaAlterado();
        }

        @Test
        @DisplayName("Redefinir com token já usado dá erro")
        void tokenJaUsado() {
            TokenRecuperacaoSenha token = token(agora().plusMinutes(10), true);
            when(tokenRepository.findByToken("token-123")).thenReturn(Optional.of(token));

            RedefinicaoSenhaRequest req = new RedefinicaoSenhaRequest("token-123", "novaSenha123");

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.redefinirSenha(req));

            assertEquals("Token já utilizado", ex.getMessage());
            assertNadaAlterado();
        }

        private void assertNadaAlterado() {
            assertEquals("hash-antigo", usuario.getSenhaHash());
            verifyNoInteractions(passwordEncoder);
            verify(usuarioRepository, never()).save(any());
            verify(tokenRepository, never()).save(any());
        }
    }
}
