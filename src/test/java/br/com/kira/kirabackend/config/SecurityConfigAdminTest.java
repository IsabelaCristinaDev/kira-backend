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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Usa um controller falso em /api/admin só para testar a regra do SecurityConfig,
// já que ainda não existe controller de admin de verdade.
@WebMvcTest(controllers = SecurityConfigAdminTest.AdminFalsoController.class)
@Import({SecurityConfig.class, SecurityConfigAdminTest.AdminFalsoController.class})
class SecurityConfigAdminTest {

    private static final String ROTA = "/api/admin/teste";

    @Autowired
    private MockMvc mockMvc;

    // Dependências do JwtAuthenticationFilter. Sem header Authorization o filtro não as usa.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @RestController
    @RequestMapping(ROTA)
    static class AdminFalsoController {

        @GetMapping
        String get() {
            return "ok";
        }

        @PostMapping
        String post() {
            return "ok";
        }

        @PutMapping
        String put() {
            return "ok";
        }

        @PatchMapping
        String patch() {
            return "ok";
        }

        @DeleteMapping
        String delete() {
            return "ok";
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin acessa /api/admin em qualquer método HTTP")
    void adminAcessaTodosOsMetodos() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isOk());
        mockMvc.perform(post(ROTA)).andExpect(status().isOk());
        mockMvc.perform(put(ROTA)).andExpect(status().isOk());
        mockMvc.perform(patch(ROTA)).andExpect(status().isOk());
        mockMvc.perform(delete(ROTA)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Cliente logado é bloqueado em /api/admin em qualquer método HTTP")
    void clienteBloqueadoEmTodosOsMetodos() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(post(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(put(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(patch(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(delete(ROTA)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"EMPRESA", "USER"})
    @DisplayName("Empresa logada é bloqueada em /api/admin em qualquer método HTTP")
    void empresaBloqueadaEmTodosOsMetodos() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(post(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(put(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(patch(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(delete(ROTA)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sem login, /api/admin é bloqueado")
    void semLoginBloqueado() throws Exception {
        mockMvc.perform(get(ROTA)).andExpect(status().isForbidden());
        mockMvc.perform(delete(ROTA)).andExpect(status().isForbidden());
    }
}
