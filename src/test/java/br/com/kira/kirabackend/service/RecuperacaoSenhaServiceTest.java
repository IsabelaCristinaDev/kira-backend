package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.TokenRecuperacaoSenha;
import br.com.kira.kirabackend.dto.request.RecuperacaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.TentativasExcedidasException;
import br.com.kira.kirabackend.observer.SenhaRedefinidaEvent;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.LimiteDeTentativas;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    private static final String IP = "200.1.1.1";
    private static final String TOKEN = "token-que-chegou-por-email";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LimiteDeTentativas limite;

    @Mock
    private EnvioRecuperacaoSenha envio;

    @Mock
    private ApplicationEventPublisher eventos;

    private RecuperacaoSenhaService service;
    private Cliente usuario;

    @BeforeEach
    void setUp() {
        service = new RecuperacaoSenhaService(usuarioRepository, tokenRepository, refreshTokenRepository,
                passwordEncoder, limite, envio, eventos);
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
        token.setId(UUID.randomUUID());
        token.setTokenHash(TokenRecuperacaoSenha.hashDoToken(TOKEN));
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
        @DisplayName("Dentro do limite, entrega o pedido ao envio em segundo plano, exista o e-mail ou não")
        void entregaAoEnvio() {
            when(limite.registrarPedidoDeRecuperacao(IP, "ana@teste.com")).thenReturn(Optional.empty());

            service.solicitarRecuperacao(new RecuperacaoSenhaRequest("ana@teste.com"), IP);

            verify(envio).gerarEEnviar("ana@teste.com");
            // Quem responde não consulta o banco: o tempo de resposta é o mesmo para qualquer e-mail
            verifyNoInteractions(usuarioRepository, tokenRepository);
        }

        @Test
        @DisplayName("Fora do limite, responde 429 e não envia nada")
        void foraDoLimite() {
            when(limite.registrarPedidoDeRecuperacao(IP, "ana@teste.com")).thenReturn(Optional.of(Duration.ofMinutes(40)));
            RecuperacaoSenhaRequest pedido = new RecuperacaoSenhaRequest("ana@teste.com");

            TentativasExcedidasException erro = assertThrows(TentativasExcedidasException.class,
                    () -> service.solicitarRecuperacao(pedido, IP));

            assertEquals(Duration.ofMinutes(40), erro.getEspera());
            verifyNoInteractions(envio);
        }
    }

    // ---------- redefinir senha ----------

    @Nested
    @DisplayName("redefinirSenha")
    class RedefinirSenha {

        private void tokenEncontrado(TokenRecuperacaoSenha token) {
            when(limite.registrarValidacaoDeToken(IP)).thenReturn(Optional.empty());
            when(tokenRepository.findByTokenHash(TokenRecuperacaoSenha.hashDoToken(TOKEN))).thenReturn(Optional.of(token));
        }

        @Test
        @DisplayName("Redefinir troca a senha, marca o token como usado e apaga os outros pendentes")
        void redefineSenha() {
            TokenRecuperacaoSenha token = token(agora().plusMinutes(10), false);
            tokenEncontrado(token);
            when(passwordEncoder.encode("novaSenha123")).thenReturn("$2a$10$hash-novo");

            service.redefinirSenha(new RedefinicaoSenhaRequest(TOKEN, "novaSenha123"), IP);

            assertEquals("$2a$10$hash-novo", usuario.getSenhaHash());
            verify(usuarioRepository).save(usuario);
            assertTrue(token.getUsado());
            verify(tokenRepository).save(token);
            verify(tokenRepository).deleteByUsuarioIdAndIdNot(usuario.getId(), token.getId());
        }

        @Test
        @DisplayName("Redefinir encerra todas as sessões: apaga os refresh tokens e grava quando a senha mudou")
        void encerraSessoes() {
            tokenEncontrado(token(agora().plusMinutes(10), false));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash-novo");
            LocalDateTime antes = agora().truncatedTo(ChronoUnit.SECONDS);

            service.redefinirSenha(new RedefinicaoSenhaRequest(TOKEN, "novaSenha123"), IP);

            verify(refreshTokenRepository).deleteByUsuarioId(usuario.getId());
            // O ConexoesAoVivo escuta este evento e fecha o chat aberto (depois do commit)
            ArgumentCaptor<SenhaRedefinidaEvent> evento = ArgumentCaptor.forClass(SenhaRedefinidaEvent.class);
            verify(eventos).publishEvent(evento.capture());
            assertEquals(usuario.getId(), evento.getValue().getUsuarioId());
            LocalDateTime alteradaEm = usuario.getSenhaAlteradaEm();
            assertNotNull(alteradaEm);
            // Em segundos inteiros, como o "iat" do JWT
            assertEquals(0, alteradaEm.getNano());
            assertFalse(alteradaEm.isBefore(antes));
        }

        @Test
        @DisplayName("Token inválido é rejeitado")
        void tokenInvalido() {
            when(limite.registrarValidacaoDeToken(IP)).thenReturn(Optional.empty());
            when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
            RedefinicaoSenhaRequest pedido = new RedefinicaoSenhaRequest("nao-existe", "novaSenha123");

            RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                    () -> service.redefinirSenha(pedido, IP));

            assertEquals("Token inválido", erro.getMessage());
            assertNadaAlterado();
        }

        @Test
        @DisplayName("Token expirado é rejeitado")
        void tokenExpirado() {
            tokenEncontrado(token(agora().minusSeconds(1), false));
            RedefinicaoSenhaRequest pedido = new RedefinicaoSenhaRequest(TOKEN, "novaSenha123");

            RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                    () -> service.redefinirSenha(pedido, IP));

            assertEquals("Token expirado", erro.getMessage());
            assertNadaAlterado();
        }

        @Test
        @DisplayName("Token já usado (reutilizado) é rejeitado")
        void tokenReutilizado() {
            tokenEncontrado(token(agora().plusMinutes(10), true));
            RedefinicaoSenhaRequest pedido = new RedefinicaoSenhaRequest(TOKEN, "novaSenha123");

            RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                    () -> service.redefinirSenha(pedido, IP));

            assertEquals("Token já utilizado", erro.getMessage());
            assertNadaAlterado();
        }

        @Test
        @DisplayName("Tentativas demais de validar token pelo mesmo IP: 429, sem nem procurar o token")
        void limiteDeValidacao() {
            when(limite.registrarValidacaoDeToken(IP)).thenReturn(Optional.of(Duration.ofMinutes(15)));
            RedefinicaoSenhaRequest pedido = new RedefinicaoSenhaRequest(TOKEN, "novaSenha123");

            assertThrows(TentativasExcedidasException.class, () -> service.redefinirSenha(pedido, IP));

            verifyNoInteractions(tokenRepository);
            assertNadaAlterado();
        }

        private void assertNadaAlterado() {
            assertEquals("hash-antigo", usuario.getSenhaHash());
            assertEquals(null, usuario.getSenhaAlteradaEm());
            verifyNoInteractions(passwordEncoder, refreshTokenRepository, eventos);
            verify(usuarioRepository, never()).save(any());
            verify(tokenRepository, never()).save(any());
        }
    }
}
