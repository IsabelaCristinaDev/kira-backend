package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcessoEmpresaTest {

    @Mock
    private FuncionariaRepository funcionariaRepository;

    private AcessoEmpresa acesso;
    private Empresa empresa;
    private Empresa outraEmpresa;

    @BeforeEach
    void setUp() {
        acesso = new AcessoEmpresa(funcionariaRepository);
        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        outraEmpresa = new Empresa();
        outraEmpresa.setId(UUID.randomUUID());
    }

    private Funcionaria funcionariaDe(Empresa dona) {
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setEmpresa(dona);
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));
        return funcionaria;
    }

    @Test
    @DisplayName("A própria empresa passa")
    void propriaEmpresaPassa() {
        assertDoesNotThrow(() -> acesso.exigirEmpresa(empresa.getId(), empresa));
    }

    @Test
    @DisplayName("Outra empresa é recusada")
    void outraEmpresaRecusada() {
        UUID empresaId = empresa.getId();

        RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                () -> acesso.exigirEmpresa(empresaId, outraEmpresa));
        assertEquals(AcessoEmpresa.SO_O_SEU_ESTABELECIMENTO, ex.getMessage());
    }

    @Test
    @DisplayName("Funcionária da própria empresa passa")
    void funcionariaDaPropriaEmpresaPassa() {
        UUID funcionariaId = funcionariaDe(empresa).getId();

        assertDoesNotThrow(() -> acesso.exigirFuncionariaDaEmpresa(funcionariaId, empresa));
    }

    @Test
    @DisplayName("Funcionária de outra empresa é recusada")
    void funcionariaDeOutraEmpresaRecusada() {
        UUID funcionariaId = funcionariaDe(outraEmpresa).getId();

        assertThrows(RegraDeNegocioException.class,
                () -> acesso.exigirFuncionariaDaEmpresa(funcionariaId, empresa));
    }

    @Test
    @DisplayName("Funcionária inexistente dá 404")
    void funcionariaInexistente() {
        UUID funcionariaId = UUID.randomUUID();
        when(funcionariaRepository.findById(funcionariaId)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> acesso.exigirFuncionariaDaEmpresa(funcionariaId, empresa));
    }
}
