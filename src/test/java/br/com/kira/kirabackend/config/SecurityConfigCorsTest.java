package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.security.CustomUserDetailsService;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Simula o front num servidor: a origem vem da configuração, não do código
@WebMvcTest(controllers = SecurityConfigCorsTest.VitrineFalsaController.class)
@Import({SecurityConfig.class, SecurityConfigCorsTest.VitrineFalsaController.class})
@TestPropertySource(properties = "kira.cors.origens=https://kira.exemplo.com/,http://localhost:5173")
class SecurityConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    // Dependências do JwtAuthenticationFilter. Sem header Authorization o filtro não as usa.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @RestController
    static class VitrineFalsaController {

        @GetMapping("/api/studios")
        String studios() {
            return "ok";
        }
    }

    @Test
    @DisplayName("O navegador libera o front que está na configuração")
    void origemConfiguradaLiberada() throws Exception {
        mockMvc.perform(options("/api/studios")
                        .header("Origin", "https://kira.exemplo.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://kira.exemplo.com"));
    }

    @Test
    @DisplayName("O navegador bloqueia um site que não está na configuração")
    void origemDesconhecidaBloqueada() throws Exception {
        mockMvc.perform(options("/api/studios")
                        .header("Origin", "https://site-qualquer.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
