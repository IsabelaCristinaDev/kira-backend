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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @DisplayName("Edita o contato da própria cliente, sem espaços sobrando")
    void edita() {
        ContatoEmergencia contato = new ContatoEmergencia();
        contato.setId(UUID.randomUUID());
        contato.setCliente(cliente);
        contato.setNome("Rosa");
        contato.setTelefone("62988887777");
        contato.setParentesco("Mãe");
        when(contatoRepository.findByIdAndClienteId(contato.getId(), cliente.getId())).thenReturn(Optional.of(contato));

        ContatoEmergenciaResponse resposta = service.atualizar(
                cliente, contato.getId(), new ContatoEmergenciaRequest(" Rosa Lima ", " 62911112222 ", " "));

        assertEquals(contato.getId(), resposta.id());
        assertEquals("Rosa Lima", resposta.nome());
        assertEquals("62911112222", resposta.telefone());
        assertNull(resposta.parentesco());
        assertEquals("Rosa Lima", contato.getNome());
    }

    @Test
    @DisplayName("Editar contato de outra cliente responde como inexistente")
    void editaContatoDeOutra() {
        UUID id = UUID.randomUUID();
        when(contatoRepository.findByIdAndClienteId(id, cliente.getId())).thenReturn(Optional.empty());
        ContatoEmergenciaRequest request = new ContatoEmergenciaRequest("Rosa", "62988887777", null);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(cliente, id, request));
        verify(contatoRepository, never()).save(any());
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
        assertThrows(RegraDeNegocioException.class, () -> service.atualizar(empresa, id, request));
        verifyNoInteractions(contatoRepository);
    }

    @Test
    @DisplayName("Lista os contatos da própria cliente na ordem em que foram cadastrados")
    void lista() {
        ContatoEmergencia primeiro = contato("Rosa", "Mãe");
        ContatoEmergencia segundo = contato("João", null);
        when(contatoRepository.findByClienteIdOrderByDataCriacaoAsc(cliente.getId()))
                .thenReturn(List.of(primeiro, segundo));

        List<ContatoEmergenciaResponse> contatos = service.listar(cliente);

        assertEquals(List.of(primeiro.getId(), segundo.getId()),
                contatos.stream().map(ContatoEmergenciaResponse::id).toList());
        assertEquals("Rosa", contatos.get(0).nome());
        assertEquals("Mãe", contatos.get(0).parentesco());
        assertNull(contatos.get(1).parentesco());
    }

    @Test
    @DisplayName("Cliente sem contatos recebe lista vazia")
    void listaVazia() {
        when(contatoRepository.findByClienteIdOrderByDataCriacaoAsc(cliente.getId())).thenReturn(List.of());

        assertTrue(service.listar(cliente).isEmpty());
    }

    @Test
    @DisplayName("Remove o contato da própria cliente")
    void remove() {
        ContatoEmergencia contato = contato("Rosa", "Mãe");
        when(contatoRepository.findByIdAndClienteId(contato.getId(), cliente.getId())).thenReturn(Optional.of(contato));

        service.remover(cliente, contato.getId());

        verify(contatoRepository).delete(contato);
    }

    @Test
    @DisplayName("Parentesco preenchido é guardado sem os espaços das pontas")
    void parentescoPreenchido() {
        when(contatoRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        ContatoEmergenciaResponse resposta = service.cadastrar(
                cliente, new ContatoEmergenciaRequest("Rosa", "62988887777", "  Mãe  "));

        assertEquals("Mãe", resposta.parentesco());
    }

    private ContatoEmergencia contato(String nome, String parentesco) {
        ContatoEmergencia contato = new ContatoEmergencia();
        contato.setId(UUID.randomUUID());
        contato.setCliente(cliente);
        contato.setNome(nome);
        contato.setTelefone("62988887777");
        contato.setParentesco(parentesco);
        return contato;
    }
}
