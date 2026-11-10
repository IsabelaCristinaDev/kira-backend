package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.dto.request.PreferenciasRequest;
import br.com.kira.kirabackend.dto.response.PreferenciasResponse;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.FotoPerfilService;
import br.com.kira.kirabackend.service.PerfilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilControllerTest {

    @Mock
    private PerfilService perfilService;

    @Mock
    private AcessoEmpresa acessoEmpresa;

    @Mock
    private FotoPerfilService fotoPerfilService;

    @InjectMocks
    private PerfilController controller;

    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Obter preferências usa a conta logada")
    void obterPreferencias() {
        PreferenciasResponse esperado = new PreferenciasResponse(List.of(CategoriaServico.CABELO));
        when(perfilService.obterPreferencias(cliente.getId(), cliente.getId())).thenReturn(esperado);

        ResponseEntity<PreferenciasResponse> resposta = controller.obterPreferencias(cliente.getId(), cliente);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(esperado, resposta.getBody());
    }

    @Test
    @DisplayName("Trocar preferências usa a conta logada e devolve a lista salva")
    void atualizarPreferencias() {
        PreferenciasRequest request = new PreferenciasRequest(List.of(CategoriaServico.UNHAS));
        PreferenciasResponse esperado = new PreferenciasResponse(List.of(CategoriaServico.UNHAS));
        when(perfilService.atualizarPreferencias(cliente.getId(), cliente.getId(), request)).thenReturn(esperado);

        ResponseEntity<PreferenciasResponse> resposta =
                controller.atualizarPreferencias(cliente.getId(), cliente, request);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(esperado, resposta.getBody());
    }
}
