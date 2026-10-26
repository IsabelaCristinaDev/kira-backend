package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.dto.request.ServicoRequest;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicoServiceTest {

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private ServicoService service;

    private Empresa empresa;

    @BeforeEach
    void setUp() {
        // Pelo construtor, como o Spring faz: garante que as dependências são realmente injetadas.
        service = new ServicoService(servicoRepository, usuarioRepository);

        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Cadastra o serviço ativo e ligado à empresa")
    void deveCadastrarServico() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(servicoRepository.save(any(Servico.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServicoResponse response = service.cadastrar(empresa.getId(),
                new ServicoRequest("Design de sobrancelha", null, 45, new BigDecimal("50.00")));

        ArgumentCaptor<Servico> captor = ArgumentCaptor.forClass(Servico.class);
        verify(servicoRepository).save(captor.capture());
        assertSame(empresa, captor.getValue().getEmpresa());
        assertTrue(response.ativo());
        assertEquals(45, response.duracaoMinutos());
        assertEquals(new BigDecimal("50.00"), response.preco());
    }

    @Test
    @DisplayName("Cadastrar para empresa inexistente dá 404")
    void empresaInexistente() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.cadastrar(empresa.getId(),
                        new ServicoRequest("Corte", null, 30, new BigDecimal("40"))));
        verify(servicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Desativar faz exclusão lógica, sem apagar o registro")
    void desativarFazExclusaoLogica() {
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setAtivo(true);
        servico.setEmpresa(empresa);
        when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));

        service.desativar(empresa.getId(), servico.getId());

        assertFalse(servico.getAtivo());
        verify(servicoRepository).save(servico);
        verify(servicoRepository, never()).delete(any());
        verify(servicoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Desativar um serviço inexistente dá 404")
    void desativarInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(servicoRepository.findById(id)).thenReturn(Optional.empty());

        UUID empresaId = empresa.getId();
        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.desativar(empresaId, id));

        assertEquals("Serviço não encontrado", ex.getMessage());
        verify(servicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Atualizar um serviço inexistente dá 404")
    void atualizarInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(servicoRepository.findById(id)).thenReturn(Optional.empty());

        UUID empresaId = empresa.getId();
        ServicoRequest request = new ServicoRequest("Corte", null, 30, new BigDecimal("40"));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(empresaId, id, request));
        verify(servicoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Serviço de outra empresa não é alterado nem desativado pela rota desta")
    void naoMexeEmServicoDeOutraEmpresa() {
        Empresa outra = new Empresa();
        outra.setId(UUID.randomUUID());
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setAtivo(true);
        servico.setEmpresa(outra);
        UUID servicoId = servico.getId();
        UUID empresaId = empresa.getId();
        ServicoRequest request = new ServicoRequest("Corte", null, 30, new BigDecimal("40"));
        when(servicoRepository.findById(servicoId)).thenReturn(Optional.of(servico));

        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(empresaId, servicoId, request));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.desativar(empresaId, servicoId));
        assertTrue(servico.getAtivo());
        verify(servicoRepository, never()).save(any());
    }
}
