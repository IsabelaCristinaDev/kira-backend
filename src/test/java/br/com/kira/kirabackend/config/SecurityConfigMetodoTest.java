package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.exception.GlobalExceptionHandler;
import br.com.kira.kirabackend.security.CustomUserDetailsService;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Rota fora de /api/admin protegida só pela anotação no método: a regra de URL não a cobre
@WebMvcTest(controllers = SecurityConfigMetodoTest.SoAdminFalsoController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SecurityConfigMetodoTest.SoAdminFalsoController.class})
class SecurityConfigMetodoTest {

    private static final String ROTA = "/api/teste-metodo";

    @Autowired
    private MockMvc mockMvc;

    // Dependências do JwtAuthenticationFilter. Sem header Authorization o filtro não as usa.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @RestController
    static class SoAdminFalsoController {

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping(ROTA)
        String soAdmin() {
            return "ok";
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin passa pelo @PreAuthorize")
    void adminPassa() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Cliente é barrada pelo @PreAuthorize com 403")
    void clienteBarrada() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("ACESSO_NEGADO"));
    }

    @Test
    @WithMockUser(roles = {"EMPRESA", "USER"})
    @DisplayName("Empresa é barrada pelo @PreAuthorize com 403")
    void empresaBarrada() throws Exception {
        mockMvc.perform(get(ROTA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("ACESSO_NEGADO"));
    }
}
