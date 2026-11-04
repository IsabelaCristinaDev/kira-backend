package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.ContatoEmergencia;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.dto.request.ContatoEmergenciaRequest;
import br.com.kira.kirabackend.dto.response.ContatoEmergenciaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.ContatoEmergenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContatoEmergenciaServiceTest {

    @Mock
    private ContatoEmergenciaRepository contatoRepository;

    @InjectMocks
    private ContatoEmergenciaService service;

    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Cadastra sem espaços sobrando, e parentesco em branco vira vazio")
    void cadastra() {
        when(contatoRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        ContatoEmergenciaResponse resposta = service.cadastrar(
                cliente, new ContatoEmergenciaRequest(" Rosa ", " 62988887777 ", " "));

        assertEquals("Rosa", resposta.nome());
        assertEquals("62988887777", resposta.telefone());
        assertNull(resposta.parentesco());
    }

    @Test
    @DisplayName("No limite por cliente, não cadastra mais")
    void limite() {
        when(contatoRepository.countByClienteId(cliente.getId())).thenReturn(5L);
        ContatoEmergenciaRequest request = new ContatoEmergenciaRequest("Rosa", "62988887777", null);

        assertThrows(RegraDeNegocioException.class, () -> service.cadastrar(cliente, request));
        verify(contatoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Contato de outra cliente responde como inexistente")
    void contatoDeOutra() {
        UUID id = UUID.randomUUID();
        when(contatoRepository.findByIdAndClienteId(id, cliente.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.remover(cliente, id));
        verify(contatoRepository, never()).delete(any(ContatoEmergencia.class));
    }

    @Test
    @DisplayName("Quem não é cliente não tem contatos de emergência")
    void soCliente() {
        Empresa empresa = new Empresa();
        ContatoEmergenciaRequest request = new ContatoEmergenciaRequest("Rosa", "62988887777", null);
        UUID id = UUID.randomUUID();

        assertThrows(RegraDeNegocioException.class, () -> service.listar(empresa));
        assertThrows(RegraDeNegocioException.class, () -> service.cadastrar(empresa, request));
        assertThrows(RegraDeNegocioException.class, () -> service.remover(empresa, id));
        verifyNoInteractions(contatoRepository);
    }
}
