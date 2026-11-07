package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.security.CustomUserDetailsService;
import br.com.kira.kirabackend.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Como o login confere a senha: sem Spring, com o BCrypt de verdade
class SecurityConfigAutenticacaoTest {

    private static final String SENHA = "senha12345";

    private final CustomUserDetailsService usuarios = mock(CustomUserDetailsService.class);
    private PasswordEncoder encoder;
    private DaoAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        SecurityConfig config = new SecurityConfig(mock(JwtAuthenticationFilter.class), List.of("http://localhost:5173"));
        encoder = spy(config.passwordEncoder());
        provider = config.authenticationProvider(usuarios, encoder);
    }

    private Cliente cliente(boolean ativo) {
        Cliente cliente = new Cliente();
        cliente.setEmail("ana@teste.com");
        cliente.setSenhaHash(encoder.encode(SENHA));
        cliente.setAtivo(ativo);
        return cliente;
    }

    private void autenticar(String senha) {
        provider.authenticate(new UsernamePasswordAuthenticationToken("ana@teste.com", senha));
    }

    @Test
    @DisplayName("As senhas usam BCrypt com custo 10")
    void bcryptCusto10() {
        assertTrue(encoder.encode(SENHA).startsWith("$2a$10$"));
    }

    @Test
    @DisplayName("E-mail sem conta: roda o BCrypt mesmo assim (tempo igual) e dá o mesmo erro de senha errada")
    void emailInexistenteRodaBcrypt() {
        when(usuarios.loadUserByUsername("ana@teste.com")).thenThrow(new UsernameNotFoundException("não existe"));

        assertThrows(BadCredentialsException.class, () -> autenticar(SENHA));

        // Hash fixo do Spring: a senha digitada é comparada com ele, como se a conta existisse
        verify(encoder).matches(eq(SENHA), anyString());
    }

    @Test
    @DisplayName("Conta inativa com senha errada responde como senha errada: não revela que a conta existe")
    void contaInativaSenhaErrada() {
        Cliente inativa = cliente(false);
        when(usuarios.loadUserByUsername("ana@teste.com")).thenReturn(inativa);

        assertThrows(BadCredentialsException.class, () -> autenticar("senha-errada"));
    }

    @Test
    @DisplayName("Conta inativa só é recusada depois da senha conferida, então o BCrypt sempre roda")
    void contaInativaConfereSenhaAntes() {
        Cliente inativa = cliente(false);
        when(usuarios.loadUserByUsername("ana@teste.com")).thenReturn(inativa);

        assertThrows(DisabledException.class, () -> autenticar(SENHA));

        verify(encoder).matches(SENHA, inativa.getSenhaHash());
    }

    @Test
    @DisplayName("Conta ativa com a senha certa entra")
    void contaAtivaEntra() {
        Cliente ativa = cliente(true);
        when(usuarios.loadUserByUsername("ana@teste.com")).thenReturn(ativa);

        assertDoesNotThrow(() -> autenticar(SENHA));
    }
}
