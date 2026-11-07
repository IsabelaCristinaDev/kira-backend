package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.dto.request.TrocaSenhaRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    @Test
    @DisplayName("Trocar a senha usa a conta logada e devolve a sessão nova")
    void trocarSenha() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        TrocaSenhaRequest request = new TrocaSenhaRequest("Atual1234", "Nova12345");
        LoginResponse sessao = new LoginResponse("jwt", "refresh", "CLIENTE", "Ana", "ana@teste.com", cliente.getId());
        when(authService.trocarSenha(cliente, request)).thenReturn(sessao);

        ResponseEntity<LoginResponse> resposta = controller.trocarSenha(cliente, request);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(sessao, resposta.getBody());
    }
}
