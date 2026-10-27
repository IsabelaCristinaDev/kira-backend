package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.dto.request.FuncionariaRequest;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
class FuncionariaServiceTest {

    @Mock
    private FuncionariaRepository funcionariaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private FuncionariaService service;

    private Empresa empresa;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
    }

    private FuncionariaRequest request(String nome) {
        return new FuncionariaRequest(nome, null, "Cílios", new BigDecimal("30"));
    }

    @Test
    @DisplayName("Cadastra a funcionária ativa e ligada à empresa")
    void deveCadastrarFuncionaria() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(funcionariaRepository.existsByEmpresaIdAndNomeIgnoreCaseAndAtivoTrue(empresa.getId(), "Bia"))
                .thenReturn(false);

        FuncionariaResponse response = service.cadastrar(empresa.getId(), request("Bia"));

        ArgumentCaptor<Funcionaria> captor = ArgumentCaptor.forClass(Funcionaria.class);
        verify(funcionariaRepository).save(captor.capture());
        assertSame(empresa, captor.getValue().getEmpresa());
        assertEquals("Bia", response.nome());
        assertTrue(response.ativo());
        assertEquals(new BigDecimal("30"), response.comissaoPercentual());
    }

    @Test
    @DisplayName("Não pode haver duas funcionárias ativas com o mesmo nome, sem diferenciar maiúsculas e minúsculas")
    void nomeRepetidoDaErro() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        // Já existe "Bia" ativa. A consulta IgnoreCase faz "BIA" bater com ela.
        when(funcionariaRepository.existsByEmpresaIdAndNomeIgnoreCaseAndAtivoTrue(empresa.getId(), "BIA"))
                .thenReturn(true);

        UUID empresaId = empresa.getId();
        FuncionariaRequest req = request("BIA");

        RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                () -> service.cadastrar(empresaId, req));

        assertEquals("Já existe uma funcionária ativa com esse nome", ex.getMessage());
        verify(funcionariaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cadastrar para empresa inexistente dá 404")
    void empresaInexistente() {
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.empty());

        UUID empresaId = empresa.getId();
        FuncionariaRequest req = request("Bia");

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.cadastrar(empresaId, req));
        verify(funcionariaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Desativar faz exclusão lógica, sem apagar o registro")
    void desativarFazExclusaoLogica() {
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setAtivo(true);
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));

        service.desativar(funcionaria.getId());

        assertFalse(funcionaria.getAtivo());
        verify(funcionariaRepository).save(funcionaria);
        verify(funcionariaRepository, never()).delete(any());
        verify(funcionariaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Desativar uma funcionária inexistente dá 404")
    void desativarInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(funcionariaRepository.findById(id)).thenReturn(Optional.empty());

        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.desativar(id));

        assertEquals("Funcionária não encontrada", ex.getMessage());
        verify(funcionariaRepository, never()).save(any());
    }
}
