package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.security.CustomUserDetailsService;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller falso nas rotas de avaliação: testa só a regra do SecurityConfig.
// O resto de GET /api/avaliacoes/** é público, então /minhas e /cliente precisam de regra própria.
@WebMvcTest(controllers = SecurityConfigAvaliacoesTest.AvaliacoesFalsoController.class)
@Import({SecurityConfig.class, SecurityConfigAvaliacoesTest.AvaliacoesFalsoController.class})
class SecurityConfigAvaliacoesTest {

    private static final String ID = "3f1c2b8e-1d2a-4c55-9a77-0b6f2b0d9e11";

    @Autowired
    private MockMvc mockMvc;

    // Dependências do JwtAuthenticationFilter. Sem header Authorization o filtro não as usa.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @RestController
    static class AvaliacoesFalsoController {

        @GetMapping("/api/avaliacoes/minhas")
        String minhas() {
            return "ok";
        }

        @GetMapping("/api/avaliacoes/cliente/{id}")
        String daCliente(@PathVariable String id) {
            return "ok";
        }

        @GetMapping("/api/avaliacoes/empresa/{id}")
        String daEmpresa(@PathVariable String id) {
            return "ok";
        }
    }

    @Test
    @DisplayName("Sem login, as avaliações da pessoa são bloqueadas")
    void semLoginBloqueado() throws Exception {
        // 4xx: o código exato de quem não está logado é definido no SecurityConfig
        mockMvc.perform(get("/api/avaliacoes/minhas")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/avaliacoes/cliente/" + ID)).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Sem login, as avaliações da empresa continuam públicas")
    void avaliacoesDaEmpresaPublicas() throws Exception {
        mockMvc.perform(get("/api/avaliacoes/empresa/" + ID)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Logado, minhas avaliações chega ao controller")
    void logadoPassa() throws Exception {
        mockMvc.perform(get("/api/avaliacoes/minhas")).andExpect(status().isOk());
    }
}
