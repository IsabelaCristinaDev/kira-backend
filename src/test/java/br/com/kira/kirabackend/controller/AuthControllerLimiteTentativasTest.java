package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.LimiteTentativasConfig;
import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.CustomUserDetailsService;
import br.com.kira.kirabackend.security.IpDoCliente;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import br.com.kira.kirabackend.security.ProtecaoLogin;
import br.com.kira.kirabackend.security.SemVerificacaoHumana;
import br.com.kira.kirabackend.service.AuthService;
import br.com.kira.kirabackend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O login de verdade fica simulado (senha "certa" entra); o limite, o IP e as respostas de erro são os reais.
// Cada teste usa e-mails e IPs próprios, porque o contador é o mesmo durante toda a classe.
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, LimiteTentativasConfig.class, ProtecaoLogin.class, IpDoCliente.class,
        SemVerificacaoHumana.class})
// Atrasos em milissegundos, para o teste não esperar segundos de verdade
@TestPropertySource(properties = {
        "kira.security.rate-limit.login.por-email.atraso-inicial=1ms",
        "kira.security.rate-limit.login.por-email.atraso-maximo=8ms"})
class AuthControllerLimiteTentativasTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private UsuarioRepository usuarioRepository;

    // Dependências do JwtAuthenticationFilter e do SecurityConfig
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        when(authService.login(any())).thenAnswer(chamada -> {
            LoginRequest dados = chamada.getArgument(0);
            if (!"certa".equals(dados.senha())) {
                throw new BadCredentialsException("senha errada");
            }
            return new LoginResponse("jwt", "refresh", "CLIENTE", "Ana", dados.email(), UUID.randomUUID());
        });
    }

    private ResultActions logar(String ip, String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(requisicao -> {
                    requisicao.setRemoteAddr(ip);
                    return requisicao;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    @Test
    @DisplayName("Senha errada responde 401 com a mensagem genérica, sem dizer se o e-mail existe")
    void mensagemGenerica() throws Exception {
        logar("10.0.1.1", "generica@teste.com", "errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"));
    }

    @Test
    @DisplayName("A 6ª tentativa do mesmo IP na mesma conta responde 429 com Retry-After")
    void sextaTentativa429() throws Exception {
        for (int i = 0; i < 5; i++) {
            logar("10.0.2.1", "bloqueio@teste.com", "errada").andExpect(status().isUnauthorized());
        }

        logar("10.0.2.1", "bloqueio@teste.com", "certa")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.erro").value("TENTATIVAS_EXCEDIDAS"));
    }

    @Test
    @DisplayName("Mesma conta vinda de outro IP ainda consegue logar")
    void outroIpConsegueLogar() throws Exception {
        for (int i = 0; i < 5; i++) {
            logar("10.0.3.1", "outro-ip@teste.com", "errada").andExpect(status().isUnauthorized());
        }

        logar("10.0.3.2", "outro-ip@teste.com", "certa").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Login com sucesso zera o contador do IP na conta")
    void sucessoZeraContador() throws Exception {
        for (int i = 0; i < 4; i++) {
            logar("10.0.4.1", "zera@teste.com", "errada").andExpect(status().isUnauthorized());
        }
        logar("10.0.4.1", "zera@teste.com", "certa").andExpect(status().isOk());

        // Depois de zerar, de novo 5 erros são permitidos: só a 6ª é bloqueada
        for (int i = 0; i < 5; i++) {
            logar("10.0.4.1", "zera@teste.com", "errada").andExpect(status().isUnauthorized());
        }
        logar("10.0.4.1", "zera@teste.com", "certa").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("20 erros do mesmo IP em contas diferentes bloqueiam esse IP")
    void ipBloqueadoEmTodasAsContas() throws Exception {
        for (int i = 0; i < 20; i++) {
            logar("10.0.5.1", "conta" + i + "@teste.com", "errada").andExpect(status().isUnauthorized());
        }

        logar("10.0.5.1", "nova-conta@teste.com", "certa").andExpect(status().isTooManyRequests());
        logar("10.0.5.2", "nova-conta@teste.com", "certa").andExpect(status().isOk());
    }
}
