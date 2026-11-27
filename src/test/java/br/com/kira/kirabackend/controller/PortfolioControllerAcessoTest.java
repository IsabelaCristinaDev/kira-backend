package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.dto.response.FotoPortfolioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Ver o portfólio é público (vitrine); enviar e apagar fotos exigem a empresa dona (AcessoEmpresa real).
@WebMvcTest(controllers = PortfolioController.class)
@Import({SecurityConfig.class, AcessoEmpresa.class})
class PortfolioControllerAcessoTest extends BaseAcessoWeb {

    @MockBean
    private PortfolioService portfolioService;

    private Empresa empresa;
    private String envio;
    private UUID fotoId;
    private FotoPortfolioResponse foto;

    @BeforeEach
    void setUp() {
        empresa = novaEmpresa();
        envio = "/api/empresas/" + empresa.getId() + "/portfolio";
        fotoId = UUID.randomUUID();
        foto = new FotoPortfolioResponse(fotoId, "/api/arquivos/abc.jpg", LocalDateTime.of(2030, 1, 10, 10, 0));
    }

    private static MockMultipartFile arquivo() {
        return new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("Sem token, a vitrine do portfólio responde 200")
    void listarPublico200() throws Exception {
        when(portfolioService.listar(empresa.getId())).thenReturn(List.of(foto));

        mockMvc.perform(get("/api/studios/" + empresa.getId() + "/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fotoId.toString()))
                .andExpect(jsonPath("$[0].url").value("/api/arquivos/abc.jpg"));
    }

    @Test
    @DisplayName("Portfólio de estúdio inexistente: 404")
    void listarInexistente404() throws Exception {
        String caminho = "/api/studios/" + empresa.getId() + "/portfolio";
        when(portfolioService.listar(empresa.getId()))
                .thenThrow(new RecursoNaoEncontradoException("Estúdio não encontrado"));

        mockMvc.perform(get(caminho)).andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", caminho));
    }

    @Test
    @DisplayName("Sem token, enviar e apagar foto respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(multipart(envio).file(arquivo())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(envio + "/" + fotoId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(portfolioService);
    }

    @Test
    @DisplayName("Outra empresa ou cliente: 403 ao enviar e apagar")
    void outraConta403() throws Exception {
        mockMvc.perform(multipart(envio).file(arquivo()).with(token(novaEmpresa())))
                .andExpect(erro(403, "ACESSO_NEGADO", envio));
        mockMvc.perform(delete(envio + "/" + fotoId).with(token(novaCliente())))
                .andExpect(erro(403, "ACESSO_NEGADO", envio + "/" + fotoId));
        verifyNoInteractions(portfolioService);
    }

    @Test
    @DisplayName("Enviar: 201 com a foto; sem o arquivo, 400")
    void enviar() throws Exception {
        when(portfolioService.adicionar(eq(empresa.getId()), any())).thenReturn(foto);

        mockMvc.perform(multipart(envio).file(arquivo()).with(token(empresa)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(fotoId.toString()));
        mockMvc.perform(multipart(envio).with(token(empresa))).andExpect(erro(400, "REQUISICAO_INVALIDA", envio));
    }

    @Test
    @DisplayName("Portfólio cheio: 400 de regra de negócio")
    void portfolioCheio400() throws Exception {
        when(portfolioService.adicionar(eq(empresa.getId()), any()))
                .thenThrow(new RegraDeNegocioException("O portfólio pode ter no máximo 12 fotos"));

        mockMvc.perform(multipart(envio).file(arquivo()).with(token(empresa)))
                .andExpect(erro(400, "REGRA_DE_NEGOCIO", envio));
    }

    @Test
    @DisplayName("Apagar: 204; foto inexistente: 404")
    void apagar() throws Exception {
        mockMvc.perform(delete(envio + "/" + fotoId).with(token(empresa))).andExpect(status().isNoContent());
        verify(portfolioService).remover(empresa.getId(), fotoId);

        UUID inexistente = UUID.randomUUID();
        doThrow(new RecursoNaoEncontradoException("Foto não encontrada"))
                .when(portfolioService).remover(empresa.getId(), inexistente);
        mockMvc.perform(delete(envio + "/" + inexistente).with(token(empresa)))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", envio + "/" + inexistente));
    }
}
