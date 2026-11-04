package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FavoritaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoritaServiceTest {

    @Mock
    private FavoritaRepository favoritaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EstudioService estudioService;

    @InjectMocks
    private FavoritaService service;

    private final UUID empresaId = UUID.randomUUID();
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Salva o estúdio na conta logada")
    void salva() {
        when(usuarioRepository.existsEmpresaAtivaById(empresaId)).thenReturn(true);

        service.salvar(cliente, empresaId);

        verify(favoritaRepository).salvarSeNaoExistir(any(), eq(cliente.getId()), eq(empresaId), any());
    }

    @Test
    @DisplayName("Estúdio inexistente ou desativado não é salvo")
    void estudioInexistente() {
        when(usuarioRepository.existsEmpresaAtivaById(empresaId)).thenReturn(false);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.salvar(cliente, empresaId));
        verifyNoInteractions(favoritaRepository);
    }

    @Test
    @DisplayName("Remove só da conta logada")
    void remove() {
        service.remover(cliente, empresaId);

        verify(favoritaRepository).deleteByClienteIdAndEmpresaId(cliente.getId(), empresaId);
    }

    @Test
    @DisplayName("Quem não é cliente não tem favoritas")
    void soCliente() {
        Empresa empresa = new Empresa();

        assertThrows(RegraDeNegocioException.class, () -> service.listar(empresa));
        assertThrows(RegraDeNegocioException.class, () -> service.salvar(empresa, empresaId));
        assertThrows(RegraDeNegocioException.class, () -> service.remover(empresa, empresaId));
        verifyNoInteractions(favoritaRepository);
    }
}
