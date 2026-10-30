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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller falso nas rotas de perfil: testa só a regra do SecurityConfig.
// O resto de GET /api/empresas/** é público, então o perfil precisa de regra própria.
@WebMvcTest(controllers = SecurityConfigPerfilTest.PerfilFalsoController.class)
@Import({SecurityConfig.class, SecurityConfigPerfilTest.PerfilFalsoController.class})
class SecurityConfigPerfilTest {

    private static final String ID = "3f1c2b8e-1d2a-4c55-9a77-0b6f2b0d9e11";

    @Autowired
    private MockMvc mockMvc;

    // Dependências do JwtAuthenticationFilter. Sem header Authorization o filtro não as usa.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @RestController
    static class PerfilFalsoController {

        @GetMapping("/api/empresas/{id}/perfil")
        String perfilEmpresa(@PathVariable String id) {
            return "ok";
        }

        @PutMapping("/api/empresas/{id}/perfil")
        String atualizarEmpresa(@PathVariable String id) {
            return "ok";
        }

        @GetMapping("/api/clientes/{id}/perfil")
        String perfilCliente(@PathVariable String id) {
            return "ok";
        }

        @GetMapping("/api/empresas/{id}/servicos")
        String servicos(@PathVariable String id) {
            return "ok";
        }
    }

    @Test
    @DisplayName("Sem login, o perfil da empresa e o da cliente são bloqueados")
    void semLoginBloqueado() throws Exception {
        mockMvc.perform(get("/api/empresas/" + ID + "/perfil")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/empresas/" + ID + "/perfil").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clientes/" + ID + "/perfil")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sem login, o resto de GET /api/empresas continua público")
    void restoDeEmpresasPublico() throws Exception {
        mockMvc.perform(get("/api/empresas/" + ID + "/servicos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Logado, chega ao controller (o dono é conferido lá)")
    void logadoPassa() throws Exception {
        mockMvc.perform(get("/api/empresas/" + ID + "/perfil")).andExpect(status().isOk());
        mockMvc.perform(get("/api/clientes/" + ID + "/perfil")).andExpect(status().isOk());
    }
}
