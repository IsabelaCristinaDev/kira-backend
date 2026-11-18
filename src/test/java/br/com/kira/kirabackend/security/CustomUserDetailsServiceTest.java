package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    @DisplayName("Encontra a conta pelo e-mail")
    void encontraConta() {
        Cliente ana = new Cliente();
        when(usuarioRepository.findByEmailIgnoreCase("ana@teste.com")).thenReturn(Optional.of(ana));

        assertSame(ana, service.loadUserByUsername("ana@teste.com"));
    }

    @Test
    @DisplayName("Conta inexistente: o erro não leva o e-mail, que poderia acabar num log")
    void erroSemEmail() {
        when(usuarioRepository.findByEmailIgnoreCase("ninguem@teste.com")).thenReturn(Optional.empty());

        UsernameNotFoundException erro = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ninguem@teste.com"));

        assertFalse(erro.getMessage().contains("ninguem@teste.com"), erro.getMessage());
    }
}
