package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.service.ArmazenamentoArquivos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /api/arquivos/* é público (fotos da vitrine); o resto do caminho segue exigindo login.
@WebMvcTest(controllers = ArquivoController.class)
@Import(SecurityConfig.class)
class ArquivoControllerAcessoTest extends BaseAcessoWeb {

    @MockBean
    private ArmazenamentoArquivos armazenamento;

    @Test
    @DisplayName("Sem token, a foto abre com o tipo e o cache certos")
    void abrir200() throws Exception {
        byte[] bytes = {1, 2, 3};
        when(armazenamento.abrir("abc.png")).thenReturn(Optional.of(new ByteArrayResource(bytes)));

        mockMvc.perform(get("/api/arquivos/abc.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
    }

    @Test
    @DisplayName("Arquivo inexistente: 404")
    void inexistente404() throws Exception {
        when(armazenamento.abrir("nao-existe.jpg")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/arquivos/nao-existe.jpg"))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", "/api/arquivos/nao-existe.jpg"));
    }

    @Test
    @DisplayName("Extensão fora da lista: 404 sem abrir o armazenamento")
    void extensaoNaoPermitida404() throws Exception {
        mockMvc.perform(get("/api/arquivos/pagina.html"))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", "/api/arquivos/pagina.html"));
        mockMvc.perform(get("/api/arquivos/semextensao"))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", "/api/arquivos/semextensao"));
        verifyNoInteractions(armazenamento);
    }

    @Test
    @DisplayName("Outros métodos no caminho dos arquivos exigem login: 401")
    void outrosMetodos401() throws Exception {
        mockMvc.perform(post("/api/arquivos/abc.png")).andExpect(status().isUnauthorized());
        verifyNoInteractions(armazenamento);
    }
}
