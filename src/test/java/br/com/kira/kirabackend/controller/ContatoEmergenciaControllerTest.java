package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.dto.request.ContatoEmergenciaRequest;
import br.com.kira.kirabackend.dto.response.ContatoEmergenciaResponse;
import br.com.kira.kirabackend.service.ContatoEmergenciaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContatoEmergenciaControllerTest {

    @Mock
    private ContatoEmergenciaService contatoService;

    @InjectMocks
    private ContatoEmergenciaController controller;

    @Test
    @DisplayName("Editar usa a cliente logada e devolve o contato atualizado")
    void atualizar() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        ContatoEmergenciaRequest request = new ContatoEmergenciaRequest("Rosa", "62988887777", "Mãe");
        ContatoEmergenciaResponse esperado = new ContatoEmergenciaResponse(id, "Rosa", "62988887777", "Mãe");
        when(contatoService.atualizar(cliente, id, request)).thenReturn(esperado);

        ResponseEntity<ContatoEmergenciaResponse> resposta = controller.atualizar(cliente, id, request);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(esperado, resposta.getBody());
    }
}
