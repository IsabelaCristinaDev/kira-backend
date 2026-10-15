package br.com.kira.kirabackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String SECRET = "chave-de-teste-com-pelo-menos-32-bytes-para-hs256";
    private static final String OUTRO_SECRET = "outra-chave-de-teste-tambem-com-mais-de-32-bytes!!";
    private static final long UMA_HORA_MS = 3_600_000L;

    private JwtTokenProvider provider;
    private UserDetails usuario;

    @BeforeEach
    void setUp() {
        provider = novoProvider(SECRET, UMA_HORA_MS);
        usuario = User.withUsername("ana@teste.com").password("x").roles("USER").build();
    }

    private static JwtTokenProvider novoProvider(String secret, long expiracaoMs) {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", expiracaoMs);
        return jwtTokenProvider;
    }

    private static Claims lerClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    @DisplayName("Token gerado é válido e devolve o e-mail certo")
    void tokenGeradoEhValido() {
        String token = provider.gerarToken(usuario, "CLIENTE");

        assertTrue(provider.tokenValido(token));
        assertEquals("ana@teste.com", provider.extrairEmail(token));
    }

    @Test
    @DisplayName("Token carrega a informação tipoUsuario")
    void tokenCarregaTipoUsuario() {
        String token = provider.gerarToken(usuario, "EMPRESA");

        assertEquals("EMPRESA", lerClaims(token).get("tipoUsuario", String.class));
    }

    @Test
    @DisplayName("Token expira de acordo com o tempo configurado")
    void tokenExpiraNoTempoConfigurado() {
        Instant antes = Instant.now();

        Claims claims = lerClaims(provider.gerarToken(usuario, "CLIENTE"));

        long segundosAteExpirar = claims.getExpiration().toInstant().getEpochSecond() - antes.getEpochSecond();
        assertTrue(segundosAteExpirar >= 3599 && segundosAteExpirar <= 3601,
                "expiração deveria ser ~1h, foi " + segundosAteExpirar + "s");
    }

    @Test
    @DisplayName("Token expirado é inválido")
    void tokenExpiradoEhInvalido() {
        String token = novoProvider(SECRET, -1_000L).gerarToken(usuario, "CLIENTE");

        assertFalse(provider.tokenValido(token));
    }

    @Test
    @DisplayName("Token assinado com outra chave é inválido")
    void tokenDeOutraChaveEhInvalido() {
        String token = novoProvider(OUTRO_SECRET, UMA_HORA_MS).gerarToken(usuario, "CLIENTE");

        assertFalse(provider.tokenValido(token));
    }

    @Test
    @DisplayName("Token com o conteúdo alterado é inválido")
    void tokenAlteradoEhInvalido() {
        String token = provider.gerarToken(usuario, "CLIENTE");
        String[] partes = token.split("\\.");

        // Troca o payload por outro (outro e-mail e tipo ADMIN), mantendo a assinatura original.
        String payloadFalso = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"invasor@teste.com\",\"tipoUsuario\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String tokenAlterado = partes[0] + "." + payloadFalso + "." + partes[2];

        assertFalse(provider.tokenValido(tokenAlterado));
    }

    @Test
    @DisplayName("Token com a assinatura alterada é inválido")
    void assinaturaAlteradaEhInvalida() {
        String token = provider.gerarToken(usuario, "CLIENTE");
        char ultimo = token.charAt(token.length() - 1);
        String tokenAlterado = token.substring(0, token.length() - 1) + (ultimo == 'A' ? 'B' : 'A');

        assertFalse(provider.tokenValido(tokenAlterado));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"texto-aleatorio", "abc.def.ghi", "Bearer xyz"})
    @DisplayName("Texto aleatório, vazio ou nulo é inválido")
    void textoAleatorioEhInvalido(String token) {
        assertFalse(provider.tokenValido(token));
    }
}
