package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import br.com.kira.kirabackend.dto.response.FotoPortfolioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FotoPortfolioRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};

    @Mock
    private FotoPortfolioRepository fotoPortfolioRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ArmazenamentoArquivos armazenamento;

    private PortfolioService service;

    private final UUID empresaId = UUID.randomUUID();
    private Empresa empresa;
    private final MockMultipartFile png = new MockMultipartFile("arquivo", "foto.png", "image/png", PNG);

    @BeforeEach
    void setUp() {
        service = new PortfolioService(fotoPortfolioRepository, usuarioRepository, new ImagensEnviadas(armazenamento));
        empresa = new Empresa();
        empresa.setId(empresaId);
        empresa.setAtivo(true);
        when(usuarioRepository.findById(empresaId)).thenReturn(Optional.of(empresa));
        when(armazenamento.salvar(any(), any())).thenAnswer(i -> "novo." + i.getArgument(1));
        when(fotoPortfolioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private FotoPortfolio foto(String url) {
        FotoPortfolio foto = new FotoPortfolio();
        foto.setId(UUID.randomUUID());
        foto.setEmpresa(empresa);
        foto.setUrl(url);
        return foto;
    }

    @Test
    @DisplayName("Lista as fotos do estúdio")
    void lista() {
        when(fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId))
                .thenReturn(List.of(foto("/api/arquivos/a.png"), foto("/api/arquivos/b.png")));

        List<FotoPortfolioResponse> fotos = service.listar(empresaId);

        assertEquals(List.of("/api/arquivos/a.png", "/api/arquivos/b.png"),
                fotos.stream().map(FotoPortfolioResponse::url).toList());
    }

    @Test
    @DisplayName("Estúdio desativado responde como inexistente")
    void estudioDesativado() {
        empresa.setAtivo(false);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.listar(empresaId));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.adicionar(empresaId, png));
        verify(armazenamento, never()).salvar(any(), any());
    }

    @Test
    @DisplayName("Conta de cliente não tem portfólio")
    void contaDeCliente() {
        UUID clienteId = UUID.randomUUID();
        when(usuarioRepository.findById(clienteId)).thenReturn(Optional.of(new Cliente()));

        assertThrows(RecursoNaoEncontradoException.class, () -> service.adicionar(clienteId, png));
        verify(armazenamento, never()).salvar(any(), any());
    }

    @Test
    @DisplayName("Adiciona a foto ao estúdio")
    void adiciona() {
        assertEquals("/api/arquivos/novo.png", service.adicionar(empresaId, png).url());

        ArgumentCaptor<FotoPortfolio> salva = ArgumentCaptor.forClass(FotoPortfolio.class);
        verify(fotoPortfolioRepository).save(salva.capture());
        assertSame(empresa, salva.getValue().getEmpresa());
    }

    @Test
    @DisplayName("A vigésima foto entra, e a seguinte é recusada sem guardar o arquivo")
    void limiteDeFotos() {
        when(fotoPortfolioRepository.countByEmpresaId(empresaId)).thenReturn(PortfolioService.MAXIMO_DE_FOTOS - 1L);
        service.adicionar(empresaId, png);

        when(fotoPortfolioRepository.countByEmpresaId(empresaId)).thenReturn((long) PortfolioService.MAXIMO_DE_FOTOS);
        RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                () -> service.adicionar(empresaId, png));

        assertEquals("O portfólio pode ter no máximo 20 fotos", ex.getMessage());
        verify(armazenamento).salvar(any(), any());
    }

    @Test
    @DisplayName("Arquivo que não é imagem é recusado")
    void recusaArquivoFalso() {
        MockMultipartFile falso = new MockMultipartFile("arquivo", "f.png", "image/png", new byte[]{1, 2, 3});

        assertThrows(RegraDeNegocioException.class, () -> service.adicionar(empresaId, falso));
        verify(fotoPortfolioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Se o banco falhar, apaga o arquivo guardado")
    void falhaNoBanco() {
        when(fotoPortfolioRepository.save(any())).thenThrow(new IllegalStateException("banco fora"));

        assertThrows(IllegalStateException.class, () -> service.adicionar(empresaId, png));

        verify(armazenamento).apagar("novo.png");
    }

    @Test
    @DisplayName("Remover apaga o registro e o arquivo")
    void remove() {
        FotoPortfolio foto = foto("/api/arquivos/a.png");
        when(fotoPortfolioRepository.findByIdAndEmpresaId(foto.getId(), empresaId)).thenReturn(Optional.of(foto));

        service.remover(empresaId, foto.getId());

        verify(fotoPortfolioRepository).delete(foto);
        verify(armazenamento).apagar("a.png");
    }

    @Test
    @DisplayName("Foto de outro estúdio responde como inexistente e nada é apagado")
    void fotoDeOutroEstudio() {
        UUID fotoId = UUID.randomUUID();
        when(fotoPortfolioRepository.findByIdAndEmpresaId(fotoId, empresaId)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.remover(empresaId, fotoId));

        verify(fotoPortfolioRepository, never()).delete(any());
        verify(armazenamento, never()).apagar(any());
    }
}
