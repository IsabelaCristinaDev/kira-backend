package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.dto.request.BuscaEstudioFiltro;
import br.com.kira.kirabackend.dto.response.EstudioBuscaResponse;
import br.com.kira.kirabackend.dto.response.PaginaResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscaEstudioServiceTest {

    private static final BuscaEstudioFiltro SEM_FILTRO =
            new BuscaEstudioFiltro(null, null, null, null, null, null, null, null, null);

    @Mock
    private BuscaEstudioRepository buscaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @InjectMocks
    private BuscaEstudioService service;

    @Test
    @DisplayName("Sem resultado: não faz as outras consultas e devolve zero páginas")
    void semResultado() {
        when(buscaRepository.contar(SEM_FILTRO, null)).thenReturn(0L);

        PaginaResponse<EstudioBuscaResponse> pagina = service.buscar(SEM_FILTRO, null, 0, 20);

        assertEquals(0, pagina.totalPaginas());
        assertEquals(List.of(), pagina.itens());
        verify(buscaRepository, never()).buscar(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Mede a distância só a partir de cliente com coordenadas")
    void origem() {
        Cliente comCoordenadas = new Cliente();
        comCoordenadas.setLatitude(-16.0);
        comCoordenadas.setLongitude(-49.0);
        Cliente semCoordenadas = new Cliente();
        Empresa empresa = new Empresa();
        empresa.setLatitude(-16.0);
        empresa.setLongitude(-49.0);

        service.buscar(SEM_FILTRO, comCoordenadas, 0, 20);
        verify(buscaRepository).contar(SEM_FILTRO, new BuscaEstudioRepository.Coordenadas(-16.0, -49.0));

        service.buscar(SEM_FILTRO, semCoordenadas, 0, 20);
        service.buscar(SEM_FILTRO, empresa, 0, 20);
        verify(buscaRepository, org.mockito.Mockito.times(2)).contar(eq(SEM_FILTRO), isNull());
    }

    @Test
    @DisplayName("Valores fora do limite são recusados antes de consultar o banco")
    void invalidos() {
        BuscaEstudioFiltro precoNegativo = new BuscaEstudioFiltro(null, null, null, null, null, new BigDecimal("-1"), null, null, null);
        BuscaEstudioFiltro notaAlta = new BuscaEstudioFiltro(null, null, null, null, null, null, 6.0, null, null);
        BuscaEstudioFiltro textoLongo = new BuscaEstudioFiltro("a".repeat(101), null, null, null, null, null, null, null, null);

        assertThrows(RegraDeNegocioException.class, () -> service.buscar(SEM_FILTRO, null, -1, 20));
        assertThrows(RegraDeNegocioException.class, () -> service.buscar(SEM_FILTRO, null, 0, 0));
        assertThrows(RegraDeNegocioException.class, () -> service.buscar(SEM_FILTRO, null, 0, 51));
        assertThrows(RegraDeNegocioException.class, () -> service.buscar(precoNegativo, null, 0, 20));
        assertThrows(RegraDeNegocioException.class, () -> service.buscar(notaAlta, null, 0, 20));
        assertThrows(RegraDeNegocioException.class, () -> service.buscar(textoLongo, null, 0, 20));
        verifyNoInteractions(buscaRepository);
    }
}
