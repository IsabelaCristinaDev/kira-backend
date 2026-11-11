package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.LimiteTentativasConfig;
import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.CustomUserDetailsService;
import br.com.kira.kirabackend.security.IpDoCliente;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import br.com.kira.kirabackend.service.EnvioRecuperacaoSenha;
import br.com.kira.kirabackend.service.RecuperacaoSenhaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Service e limite de verdade; o envio (em segundo plano) e o banco ficam simulados.
// Cada teste usa e-mails e IPs próprios, porque o contador é o mesmo durante toda a classe.
@WebMvcTest(controllers = RecuperacaoSenhaController.class)
@Import({SecurityConfig.class, LimiteTentativasConfig.class, IpDoCliente.class, RecuperacaoSenhaService.class})
class RecuperacaoSenhaControllerTest {

    private static final String MENSAGEM = "Se o e-mail estiver cadastrado, enviaremos as instruções.";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnvioRecuperacaoSenha envio;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    // Dependências do JwtAuthenticationFilter e do SecurityConfig
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private ResultActions pedirRecuperacao(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/recuperar-senha")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions redefinir(String ip, String token) throws Exception {
        return mockMvc.perform(post("/api/auth/redefinir-senha")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"novaSenha123\"}"));
    }

    @Test
    @DisplayName("E-mail cadastrado e e-mail sem conta recebem exatamente a mesma resposta")
    void respostaIdentica() throws Exception {
        Cliente ana = new Cliente();
        ana.setEmail("cadastrada@teste.com");
        when(usuarioRepository.findByEmailIgnoreCase("cadastrada@teste.com")).thenReturn(Optional.of(ana));
        when(usuarioRepository.findByEmailIgnoreCase("sem-conta@teste.com")).thenReturn(Optional.empty());

        MvcResult comConta = pedirRecuperacao("10.1.1.1", "cadastrada@teste.com").andReturn();
        MvcResult semConta = pedirRecuperacao("10.1.1.2", "sem-conta@teste.com").andReturn();

        assertEquals(200, comConta.getResponse().getStatus());
        assertEquals(comConta.getResponse().getStatus(), semConta.getResponse().getStatus());
        assertEquals(comConta.getResponse().getContentAsString(), semConta.getResponse().getContentAsString());
        pedirRecuperacao("10.1.1.3", "outra@teste.com").andExpect(jsonPath("$.mensagem").value(MENSAGEM));
    }

    @Test
    @DisplayName("O 4º pedido de recuperação do mesmo e-mail na hora recebe 429 com Retry-After")
    void quartoPedidoDoEmail429() throws Exception {
        for (int i = 0; i < 3; i++) {
            pedirRecuperacao("10.2.1." + i, "limite@teste.com").andExpect(status().isOk());
        }

        pedirRecuperacao("10.2.1.9", "limite@teste.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.erro").value("TENTATIVAS_EXCEDIDAS"));
    }

    @Test
    @DisplayName("Token inválido na redefinição é rejeitado com 400")
    void tokenInvalido400() throws Exception {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        redefinir("10.3.1.1", "token-inventado")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Token inválido"));
    }

    @Test
    @DisplayName("A 11ª tentativa de validar token do mesmo IP recebe 429")
    void validacaoDeTokenLimitada() throws Exception {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        for (int i = 0; i < 10; i++) {
            redefinir("10.4.1.1", "chute-" + i).andExpect(status().isBadRequest());
        }

        redefinir("10.4.1.1", "chute-10").andExpect(status().isTooManyRequests());
        redefinir("10.4.1.2", "chute-11").andExpect(status().isBadRequest());
    }
}
