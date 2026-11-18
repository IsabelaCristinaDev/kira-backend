package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.request.BuscaEstudioFiltro;
import br.com.kira.kirabackend.dto.response.EstudioBuscaResponse;
import br.com.kira.kirabackend.dto.response.PaginaResponse;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository.Linha;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        verify(buscaRepository, times(2)).contar(eq(SEM_FILTRO), isNull());
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

    @Test
    @DisplayName("Com resultados: monta cada estúdio com os dados da busca e da empresa, na ordem do ranking")
    void comResultados() {
        Empresa primeira = empresa("Estúdio Bella", "Goiânia");
        Empresa segunda = empresa("Studio Lash", "Anápolis");
        Linha linhaPrimeira = new Linha(primeira.getId(), new BigDecimal("40.00"), 4.8, 12, 2.5);
        Linha linhaSegunda = new Linha(segunda.getId(), new BigDecimal("55.00"), null, 0, null);
        when(buscaRepository.contar(SEM_FILTRO, null)).thenReturn(2L);
        when(buscaRepository.buscar(SEM_FILTRO, null, 0, 20)).thenReturn(List.of(linhaPrimeira, linhaSegunda));
        // O banco devolve as empresas fora da ordem do ranking: quem manda é a ordem das linhas
        when(usuarioRepository.findAllById(List.of(primeira.getId(), segunda.getId())))
                .thenReturn(List.of(segunda, primeira));
        when(servicoRepository.findByEmpresaIdInAndAtivoTrue(List.of(primeira.getId(), segunda.getId())))
                .thenReturn(List.of());

        List<EstudioBuscaResponse> itens = service.buscar(SEM_FILTRO, null, 0, 20).itens();

        assertEquals(List.of(primeira.getId(), segunda.getId()), itens.stream().map(EstudioBuscaResponse::id).toList());
        EstudioBuscaResponse estudio = itens.get(0);
        assertEquals("Estúdio Bella", estudio.nome());
        assertEquals("Descrição de Estúdio Bella", estudio.descricao());
        assertEquals("Goiânia", estudio.endereco().cidade());
        assertEquals("62999990000", estudio.telefone());
        assertEquals("/arquivos/Estúdio Bella.jpg", estudio.fotoUrl());
        assertEquals(TipoEstabelecimento.ESTABELECIMENTO, estudio.tipoEstabelecimento());
        assertEquals(-16.68, estudio.latitude());
        assertEquals(-49.25, estudio.longitude());
        assertEquals(4.8, estudio.mediaAvaliacoes());
        assertEquals(12, estudio.quantidadeAvaliacoes());
        assertEquals(new BigDecimal("40.00"), estudio.menorPreco());
        assertEquals(2.5, estudio.distanciaKm());
        // Estúdio sem avaliação e sem distância calculada vem com os campos nulos, sem erro
        assertNull(itens.get(1).mediaAvaliacoes());
        assertNull(itens.get(1).distanciaKm());
    }

    @Test
    @DisplayName("Os serviços de cada estúdio vêm do mais barato ao mais caro; estúdio sem serviço vem com lista vazia")
    void servicosOrdenadosPorPreco() {
        Empresa comServicos = empresa("Estúdio Bella", "Goiânia");
        Empresa semServicos = empresa("Studio Lash", "Goiânia");
        when(buscaRepository.contar(SEM_FILTRO, null)).thenReturn(2L);
        when(buscaRepository.buscar(SEM_FILTRO, null, 0, 20)).thenReturn(List.of(
                new Linha(comServicos.getId(), new BigDecimal("30.00"), null, 0, null),
                new Linha(semServicos.getId(), null, null, 0, null)));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(comServicos, semServicos));
        when(servicoRepository.findByEmpresaIdInAndAtivoTrue(any())).thenReturn(List.of(
                servico(comServicos, "Alongamento de cílios", "120.00"),
                servico(comServicos, "Design de sobrancelha", "30.00"),
                servico(comServicos, "Henna", "45.00")));

        List<EstudioBuscaResponse> itens = service.buscar(SEM_FILTRO, null, 0, 20).itens();

        assertEquals(List.of("Design de sobrancelha", "Henna", "Alongamento de cílios"),
                itens.get(0).servicos().stream().map(ServicoResponse::nome).toList());
        assertEquals(List.of(), itens.get(1).servicos());
    }

    @Test
    @DisplayName("A página pedida vai para a consulta e o total de páginas arredonda para cima")
    void paginacao() {
        Empresa empresa = empresa("Estúdio Bella", "Goiânia");
        when(buscaRepository.contar(SEM_FILTRO, null)).thenReturn(45L);
        when(buscaRepository.buscar(SEM_FILTRO, null, 2, 20))
                .thenReturn(List.of(new Linha(empresa.getId(), null, null, 0, null)));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(empresa));
        when(servicoRepository.findByEmpresaIdInAndAtivoTrue(any())).thenReturn(List.of());

        PaginaResponse<EstudioBuscaResponse> pagina = service.buscar(SEM_FILTRO, null, 2, 20);

        // 45 estúdios em páginas de 20: 20 + 20 + 5 = 3 páginas
        assertEquals(3, pagina.totalPaginas());
        assertEquals(45, pagina.totalItens());
        assertEquals(2, pagina.pagina());
        assertEquals(20, pagina.tamanho());
        assertEquals(1, pagina.itens().size());
    }

    @Test
    @DisplayName("Distância negativa, nota negativa e cidade ou serviço longos demais são recusados com a mensagem certa")
    void outrosInvalidos() {
        BuscaEstudioFiltro distanciaNegativa = new BuscaEstudioFiltro(null, null, null, null, null, null, null, -1.0, null);
        BuscaEstudioFiltro notaNegativa = new BuscaEstudioFiltro(null, null, null, null, null, null, -0.5, null, null);
        BuscaEstudioFiltro cidadeLonga = new BuscaEstudioFiltro(null, "a".repeat(101), null, null, null, null, null, null, null);
        BuscaEstudioFiltro servicoLongo = new BuscaEstudioFiltro(null, null, null, "a".repeat(101), null, null, null, null, null);

        assertEquals("A distância máxima não pode ser negativa", mensagemDoErro(distanciaNegativa));
        assertEquals("A nota mínima deve ser de 0 a 5", mensagemDoErro(notaNegativa));
        assertEquals("O texto da busca deve ter no máximo 100 caracteres", mensagemDoErro(cidadeLonga));
        assertEquals("O texto da busca deve ter no máximo 100 caracteres", mensagemDoErro(servicoLongo));
        verifyNoInteractions(buscaRepository);
    }

    @Test
    @DisplayName("Valores exatamente no limite são aceitos: 100 caracteres, nota 0 e 5, preço e distância 0, página de 50")
    void limitesSaoAceitos() {
        BuscaEstudioFiltro textoNoLimite = new BuscaEstudioFiltro("a".repeat(100), "b".repeat(100), null,
                "c".repeat(100), null, BigDecimal.ZERO, 0.0, 0.0, null);
        BuscaEstudioFiltro notaMaxima = new BuscaEstudioFiltro(null, null, null, null, null, null, 5.0, null, null);
        when(buscaRepository.contar(any(), isNull())).thenReturn(0L);

        assertDoesNotThrow(() -> service.buscar(textoNoLimite, null, 0, 20));
        assertDoesNotThrow(() -> service.buscar(notaMaxima, null, 0, 20));
        assertDoesNotThrow(() -> service.buscar(SEM_FILTRO, null, 0, BuscaEstudioService.TAMANHO_MAXIMO));
        verify(buscaRepository, times(3)).contar(any(), isNull());
    }

    // ---------- helpers ----------

    private String mensagemDoErro(BuscaEstudioFiltro filtro) {
        return assertThrows(RegraDeNegocioException.class, () -> service.buscar(filtro, null, 0, 20)).getMessage();
    }

    private static Empresa empresa(String nome, String cidade) {
        Endereco endereco = new Endereco();
        endereco.setCidade(cidade);
        Empresa empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome(nome);
        empresa.setDescricao("Descrição de " + nome);
        empresa.setEndereco(endereco);
        empresa.setTelefone("62999990000");
        empresa.setFotoUrl("/arquivos/" + nome + ".jpg");
        empresa.setTipoEstabelecimento(TipoEstabelecimento.ESTABELECIMENTO);
        empresa.setLatitude(-16.68);
        empresa.setLongitude(-49.25);
        return empresa;
    }

    private static Servico servico(Empresa empresa, String nome, String preco) {
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setNome(nome);
        servico.setPreco(new BigDecimal(preco));
        servico.setEmpresa(empresa);
        return servico;
    }
}
