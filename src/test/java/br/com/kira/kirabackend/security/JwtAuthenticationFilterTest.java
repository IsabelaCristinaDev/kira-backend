package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.domain.entity.Cliente;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
        request = new MockHttpServletRequest("GET", "/agendamentos");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication autenticacaoAtual() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("Sem o cabeçalho Authorization, a requisição segue sem autenticar")
    void semCabecalhoSegueSemAutenticar() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertNull(autenticacaoAtual());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider, userDetailsService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"token-sem-prefixo", "Basic dXNlcjpzZW5oYQ==", "bearer token-minusculo", "Bearertoken"})
    @DisplayName("Sem o prefixo \"Bearer \", a requisição segue sem autenticar")
    void semPrefixoBearerSegueSemAutenticar(String cabecalho) throws Exception {
        request.addHeader("Authorization", cabecalho);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(autenticacaoAtual());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider, userDetailsService);
    }

    @Test
    @DisplayName("Token válido autentica o usuário")
    void tokenValidoAutentica() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setEmail("ana@teste.com");
        request.addHeader("Authorization", "Bearer token-valido");
        when(jwtTokenProvider.tokenValido("token-valido")).thenReturn(true);
        when(jwtTokenProvider.extrairEmail("token-valido")).thenReturn("ana@teste.com");
        when(userDetailsService.loadUserByUsername("ana@teste.com")).thenReturn(cliente);

        filter.doFilterInternal(request, response, filterChain);

        Authentication autenticacao = autenticacaoAtual();
        assertSame(cliente, autenticacao.getPrincipal());
        assertTrue(autenticacao.isAuthenticated());
        assertTrue(autenticacao.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Token válido de conta desativada não autentica, mas a requisição segue")
    void tokenDeContaDesativadaNaoAutentica() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setEmail("ana@teste.com");
        cliente.setAtivo(false);
        request.addHeader("Authorization", "Bearer token-valido");
        when(jwtTokenProvider.tokenValido("token-valido")).thenReturn(true);
        when(jwtTokenProvider.extrairEmail("token-valido")).thenReturn("ana@teste.com");
        when(userDetailsService.loadUserByUsername("ana@teste.com")).thenReturn(cliente);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(autenticacaoAtual());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Token inválido não autentica, mas a requisição segue")
    void tokenInvalidoNaoAutentica() throws Exception {
        request.addHeader("Authorization", "Bearer token-invalido");
        when(jwtTokenProvider.tokenValido("token-invalido")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(autenticacaoAtual());
        verify(jwtTokenProvider, never()).extrairEmail("token-invalido");
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Requisição OPTIONS (pré-verificação do navegador) passa direto")
    void optionsPassaDireto() throws Exception {
        request.setMethod("OPTIONS");
        request.addHeader("Authorization", "Bearer token-valido");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(autenticacaoAtual());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtTokenProvider, userDetailsService);
    }
}
