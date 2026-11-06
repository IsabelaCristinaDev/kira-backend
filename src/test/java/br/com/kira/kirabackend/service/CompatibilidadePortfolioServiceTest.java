package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.dto.response.CompatibilidadeEstudioResponse;
import br.com.kira.kirabackend.exception.CompatibilidadeIndisponivelException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FotoPortfolioRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.service.ComparadorDePortfolio.Foto;
import br.com.kira.kirabackend.service.ComparadorDePortfolio.Semelhanca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompatibilidadePortfolioServiceTest {

    private static final byte[] BYTES_REFERENCIA = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9};

    @Mock
    private ServicoRepository servicoRepository;
    @Mock
    private FotoPortfolioRepository fotoPortfolioRepository;
    @Mock
    private ImagensEnviadas imagens;
    @Mock
    private ComparadorDePortfolio comparador;

    @InjectMocks
    private CompatibilidadePortfolioService service;

    private MultipartFile referencia;
    private Empresa bellaFlor;
    private Empresa jasmim;

    @BeforeEach
    void setUp() {
        referencia = new MockMultipartFile("referencia", "ref.jpg", "image/jpeg", BYTES_REFERENCIA);
        bellaFlor = empresa();
        jasmim = empresa();
    }

    private static Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        return empresa;
    }

    private static Servico servico(Empresa empresa, CategoriaServico categoria, String nome) {
        Servico servico = new Servico();
        servico.setEmpresa(empresa);
        servico.setCategoria(categoria);
        servico.setNome(nome);
        return servico;
    }

    private static FotoPortfolio foto(Empresa empresa, String arquivo) {
        FotoPortfolio foto = new FotoPortfolio();
        foto.setId(UUID.randomUUID());
        foto.setEmpresa(empresa);
        foto.setUrl(ImagensEnviadas.PREFIXO_URL + arquivo);
        return foto;
    }

    private void referenciaValida() {
        when(imagens.lerImagem(referencia)).thenReturn(BYTES_REFERENCIA);
    }

    private void fotosDe(Empresa empresa, FotoPortfolio... fotos) {
        when(fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresa.getId()))
                .thenReturn(List.of(fotos));
        for (FotoPortfolio foto : fotos) {
            when(imagens.lerGuardada(foto.getUrl())).thenReturn(Optional.of(new byte[]{1}));
        }
    }

    private static List<Foto> fotosChamadas(String... nomes) {
        return argThat(fotos -> fotos.stream().map(Foto::nome).toList().equals(Arrays.asList(nomes)));
    }

    @Test
    @DisplayName("Ordena os estúdios pela foto mais parecida de cada um")
    void ordenaPelaMelhorFoto() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue()).thenReturn(List.of(
                servico(bellaFlor, CategoriaServico.CABELO, "Coloração"),
                servico(jasmim, CategoriaServico.CABELO, "Box braids")));
        fotosDe(bellaFlor, foto(bellaFlor, "bf-1.jpg"), foto(bellaFlor, "bf-2.jpg"));
        fotosDe(jasmim, foto(jasmim, "j-1.jpg"));
        when(comparador.comparar(eq(BYTES_REFERENCIA), fotosChamadas("bf-1.jpg", "bf-2.jpg")))
                .thenReturn(List.of(new Semelhanca("bf-1.jpg", 63.71), new Semelhanca("bf-2.jpg", 77.96)));
        when(comparador.comparar(eq(BYTES_REFERENCIA), fotosChamadas("j-1.jpg")))
                .thenReturn(List.of(new Semelhanca("j-1.jpg", 80.5)));

        List<CompatibilidadeEstudioResponse> ranking = service.ranquear(referencia);

        assertEquals(List.of(
                new CompatibilidadeEstudioResponse(jasmim.getId(), 80.5),
                new CompatibilidadeEstudioResponse(bellaFlor.getId(), 77.96)), ranking);
    }

    @Test
    @DisplayName("Compara o portfólio inteiro em grupos de 3: a foto mais antiga também conta")
    void comparaPortfolioInteiroEmGrupos() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue())
                .thenReturn(List.of(servico(bellaFlor, CategoriaServico.CABELO, "Coloração")));
        fotosDe(bellaFlor, foto(bellaFlor, "f1.jpg"), foto(bellaFlor, "f2.jpg"), foto(bellaFlor, "f3.jpg"),
                foto(bellaFlor, "f4.jpg"), foto(bellaFlor, "f5.jpg"), foto(bellaFlor, "f6.jpg"),
                foto(bellaFlor, "ruiva.jpg"));
        when(comparador.comparar(eq(BYTES_REFERENCIA), fotosChamadas("f1.jpg", "f2.jpg", "f3.jpg")))
                .thenReturn(List.of(new Semelhanca("f1.jpg", 55.57)));
        when(comparador.comparar(eq(BYTES_REFERENCIA), fotosChamadas("f4.jpg", "f5.jpg", "f6.jpg")))
                .thenReturn(List.of());
        when(comparador.comparar(eq(BYTES_REFERENCIA), fotosChamadas("ruiva.jpg")))
                .thenReturn(List.of(new Semelhanca("ruiva.jpg", 90.12)));

        List<CompatibilidadeEstudioResponse> ranking = service.ranquear(referencia);

        assertEquals(List.of(new CompatibilidadeEstudioResponse(bellaFlor.getId(), 90.12)), ranking);
    }

    @Test
    @DisplayName("Arquivo sumido sai antes de formar os grupos: 4 fotos com 1 sumida dão uma chamada só")
    void arquivoSumidoNaoAbreGrupoNovo() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue())
                .thenReturn(List.of(servico(jasmim, CategoriaServico.CABELO, "Box braids")));
        FotoPortfolio sumida = foto(jasmim, "sumida.jpg");
        List<FotoPortfolio> existentes = List.of(foto(jasmim, "a.jpg"), foto(jasmim, "b.jpg"), foto(jasmim, "c.jpg"));
        when(fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(jasmim.getId()))
                .thenReturn(List.of(existentes.get(0), sumida, existentes.get(1), existentes.get(2)));
        when(imagens.lerGuardada(sumida.getUrl())).thenReturn(Optional.empty());
        existentes.forEach(f -> when(imagens.lerGuardada(f.getUrl())).thenReturn(Optional.of(new byte[]{1})));
        when(comparador.comparar(eq(BYTES_REFERENCIA), fotosChamadas("a.jpg", "b.jpg", "c.jpg")))
                .thenReturn(List.of(new Semelhanca("b.jpg", 61.0)));

        assertEquals(List.of(new CompatibilidadeEstudioResponse(jasmim.getId(), 61.0)), service.ranquear(referencia));
        verify(comparador).comparar(eq(BYTES_REFERENCIA), anyList());
    }

    @Test
    @DisplayName("Só entram estúdios com serviço de cabelo, inclusive serviço antigo sem categoria")
    void soEstudiosDeCabelo() {
        referenciaValida();
        Empresa unhas = empresa();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue()).thenReturn(List.of(
                servico(unhas, CategoriaServico.UNHAS, "Manicure"),
                servico(jasmim, null, "Corte feminino")));
        fotosDe(jasmim, foto(jasmim, "j-1.jpg"));
        when(comparador.comparar(eq(BYTES_REFERENCIA), anyList()))
                .thenReturn(List.of(new Semelhanca("j-1.jpg", 70.0)));

        List<CompatibilidadeEstudioResponse> ranking = service.ranquear(referencia);

        assertEquals(List.of(new CompatibilidadeEstudioResponse(jasmim.getId(), 70.0)), ranking);
        verify(fotoPortfolioRepository, never()).findByEmpresaIdOrderByDataCriacaoDesc(unhas.getId());
    }

    @Test
    @DisplayName("Estúdio com vários serviços de cabelo é comparado uma vez só")
    void estudioComparadoUmaVez() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue()).thenReturn(List.of(
                servico(jasmim, CategoriaServico.CABELO, "Box braids"),
                servico(jasmim, CategoriaServico.CABELO, "Corte para cachos")));
        fotosDe(jasmim, foto(jasmim, "j-1.jpg"));
        when(comparador.comparar(eq(BYTES_REFERENCIA), anyList()))
                .thenReturn(List.of(new Semelhanca("j-1.jpg", 70.0)));

        assertEquals(1, service.ranquear(referencia).size());
        verify(comparador).comparar(eq(BYTES_REFERENCIA), anyList());
    }

    @Test
    @DisplayName("Estúdio sem nenhuma foto reconhecida como cabelo fica fora")
    void semFotoDeCabeloFicaFora() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue())
                .thenReturn(List.of(servico(jasmim, CategoriaServico.CABELO, "Box braids")));
        fotosDe(jasmim, foto(jasmim, "j-1.jpg"));
        when(comparador.comparar(eq(BYTES_REFERENCIA), anyList())).thenReturn(List.of());

        assertTrue(service.ranquear(referencia).isEmpty());
    }

    @Test
    @DisplayName("Estúdio sem portfólio não chama a IA")
    void semPortfolioNaoChama() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue())
                .thenReturn(List.of(servico(jasmim, CategoriaServico.CABELO, "Box braids")));
        when(fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(jasmim.getId())).thenReturn(List.of());

        assertTrue(service.ranquear(referencia).isEmpty());
        verifyNoInteractions(comparador);
    }

    @Test
    @DisplayName("Foto cujo arquivo sumiu da pasta é pulada; as outras seguem")
    void arquivoSumidoEPulado() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue())
                .thenReturn(List.of(servico(jasmim, CategoriaServico.CABELO, "Box braids")));
        FotoPortfolio sumida = foto(jasmim, "sumida.jpg");
        FotoPortfolio existe = foto(jasmim, "existe.jpg");
        when(fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(jasmim.getId()))
                .thenReturn(List.of(sumida, existe));
        when(imagens.lerGuardada(sumida.getUrl())).thenReturn(Optional.empty());
        when(imagens.lerGuardada(existe.getUrl())).thenReturn(Optional.of(new byte[]{1}));
        when(comparador.comparar(eq(BYTES_REFERENCIA), fotosChamadas("existe.jpg")))
                .thenReturn(List.of(new Semelhanca("existe.jpg", 55.0)));

        assertEquals(List.of(new CompatibilidadeEstudioResponse(jasmim.getId(), 55.0)), service.ranquear(referencia));
    }

    @Test
    @DisplayName("Nenhum estúdio de cabelo: lista vazia sem chamar a IA")
    void nenhumEstudio() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue()).thenReturn(List.of());

        assertTrue(service.ranquear(referencia).isEmpty());
        verifyNoInteractions(comparador);
    }

    @Test
    @DisplayName("Sem foto de referência responde 400 antes de consultar o banco")
    void semReferencia() {
        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class, () -> service.ranquear(null));

        assertEquals("Envie a foto de referência", erro.getMessage());
        verifyNoInteractions(servicoRepository, comparador);
    }

    @Test
    @DisplayName("Referência em formato inválido não chega à IA")
    void referenciaInvalida() {
        when(imagens.lerImagem(referencia)).thenThrow(new RegraDeNegocioException("A foto precisa ser JPG, PNG ou WEBP"));

        assertThrows(RegraDeNegocioException.class, () -> service.ranquear(referencia));
        verifyNoInteractions(servicoRepository, comparador);
    }

    @Test
    @DisplayName("IA fora do ar interrompe o ranking com indisponível")
    void iaForaDoAr() {
        referenciaValida();
        when(servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue())
                .thenReturn(List.of(servico(jasmim, CategoriaServico.CABELO, "Box braids")));
        fotosDe(jasmim, foto(jasmim, "j-1.jpg"));
        when(comparador.comparar(any(), anyList()))
                .thenThrow(new CompatibilidadeIndisponivelException("fora do ar", null));

        assertThrows(CompatibilidadeIndisponivelException.class, () -> service.ranquear(referencia));
    }
}
