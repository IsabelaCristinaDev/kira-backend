package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.dto.request.RecuperacaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.security.CustomUserDetailsService;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import br.com.kira.kirabackend.service.RecuperacaoSenhaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller de verdade com o SecurityConfig de verdade: confere que as rotas são públicas,
// que o @Valid recusa o corpo inválido e que o erro do service chega como 400.
@WebMvcTest(controllers = RecuperacaoSenhaController.class)
@Import(SecurityConfig.class)
class RecuperacaoSenhaControllerTest {

    private static final String REDEFINIR = "/api/auth/redefinir-senha";
    private static final String RECUPERAR = "/api/auth/recuperar-senha";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecuperacaoSenhaService recuperacaoSenhaService;

    // Dependências do JwtAuthenticationFilter. Sem header Authorization o filtro não as usa.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Redefinir a senha com token e senha válidos, sem estar logada, responde 200 e chama o service")
    void redefinirComDadosValidos() throws Exception {
        // Act + Assert
        mockMvc.perform(post(REDEFINIR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc-123\",\"novaSenha\":\"NovaSenha1\"}"))
                .andExpect(status().isOk());

        verify(recuperacaoSenhaService).redefinirSenha(new RedefinicaoSenhaRequest("abc-123", "NovaSenha1"));
    }

    @Test
    @DisplayName("Redefinir sem token e com senha curta responde 400 com o erro de cada campo, sem chamar o service")
    void redefinirComCorpoInvalido() throws Exception {
        // Act + Assert
        mockMvc.perform(post(REDEFINIR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\",\"novaSenha\":\"curta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO"))
                .andExpect(jsonPath("$.erros.token").value("Token é obrigatório"))
                .andExpect(jsonPath("$.erros.novaSenha").value("Senha deve ter no mínimo 8 caracteres"));

        verify(recuperacaoSenhaService, never()).redefinirSenha(any());
    }

    @Test
    @DisplayName("Token inválido, expirado ou já usado vira 400 com a mensagem do service")
    void redefinirComTokenRecusado() throws Exception {
        // Arrange
        doThrow(new RegraDeNegocioException("Token expirado"))
                .when(recuperacaoSenhaService).redefinirSenha(any());

        // Act + Assert
        mockMvc.perform(post(REDEFINIR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"velho\",\"novaSenha\":\"NovaSenha1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Token expirado"));
    }

    @Test
    @DisplayName("Pedir a recuperação com e-mail válido, sem estar logada, responde 200 e chama o service")
    void recuperarComEmailValido() throws Exception {
        // Act + Assert
        mockMvc.perform(post(RECUPERAR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.com\"}"))
                .andExpect(status().isOk());

        verify(recuperacaoSenhaService).solicitarRecuperacao(new RecuperacaoSenhaRequest("ana@example.com"));
    }

    @Test
    @DisplayName("Pedir a recuperação com e-mail inválido responde 400 sem chamar o service")
    void recuperarComEmailInvalido() throws Exception {
        // Act + Assert
        mockMvc.perform(post(RECUPERAR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.email").value("Email inválido"));

        verify(recuperacaoSenhaService, never()).solicitarRecuperacao(any());
    }
}
