package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.response.EstudioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstudioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @Mock
    private FuncionariaRepository funcionariaRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @InjectMocks
    private EstudioService service;

    private Empresa studioBelaVista;
    private Empresa espacoCilios;
    private Empresa semEndereco;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        studioBelaVista = empresa("Studio Bela Vista", TipoEstabelecimento.ESTABELECIMENTO, endereco("São Paulo"));
        espacoCilios = empresa("Espaço Cílios da Ju", TipoEstabelecimento.AUTONOMA, endereco("Campinas"));
        semEndereco = empresa("Bela Unhas", TipoEstabelecimento.AUTONOMA, null);

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Bela Cliente");

        lenient().when(usuarioRepository.findAll())
                .thenReturn(List.of(studioBelaVista, cliente, espacoCilios, semEndereco));
        lenient().when(avaliacaoRepository.calcularMediaAvaliacaoEmpresa(any())).thenReturn(null);
    }

    private static Empresa empresa(String nome, TipoEstabelecimento tipo, Endereco endereco) {
        Empresa empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome(nome);
        empresa.setTipoEstabelecimento(tipo);
        empresa.setEndereco(endereco);
        return empresa;
    }

    private static Endereco endereco(String cidade) {
        Endereco endereco = new Endereco();
        endereco.setCidade(cidade);
        return endereco;
    }

    private static List<String> nomes(List<EstudioResponse> estudios) {
        return estudios.stream().map(EstudioResponse::nome).toList();
    }

    @Test
    @DisplayName("Listar todos traz só as empresas, nunca os clientes")
    void listarTodosSoTrazEmpresas() {
        assertEquals(List.of("Studio Bela Vista", "Espaço Cílios da Ju", "Bela Unhas"),
                nomes(service.listarTodos()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bela", "BELA", "Bela", "bElA"})
    @DisplayName("A busca por nome ignora maiúsculas e minúsculas e não traz clientes")
    void buscaPorNomeIgnoraMaiusculas(String termo) {
        // "Bela Cliente" também contém "bela", mas é cliente e não pode aparecer.
        assertEquals(List.of("Studio Bela Vista", "Bela Unhas"), nomes(service.buscarPorNome(termo)));
    }

    @Test
    @DisplayName("A busca por nome encontra parte do nome")
    void buscaPorNomeEncontraParteDoNome() {
        assertEquals(List.of("Espaço Cílios da Ju"), nomes(service.buscarPorNome("cílios")));
    }

    @Test
    @DisplayName("O filtro por tipo traz só as empresas do tipo pedido")
    void filtroPorTipo() {
        assertEquals(List.of("Espaço Cílios da Ju", "Bela Unhas"),
                nomes(service.buscarPorTipo(TipoEstabelecimento.AUTONOMA)));
        assertEquals(List.of("Studio Bela Vista"),
                nomes(service.buscarPorTipo(TipoEstabelecimento.ESTABELECIMENTO)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"campinas", "CAMPINAS", "camp"})
    @DisplayName("O filtro por cidade ignora maiúsculas e não quebra com empresa sem endereço")
    void filtroPorCidade(String cidade) {
        assertEquals(List.of("Espaço Cílios da Ju"), nomes(service.buscarPorCidade(cidade)));
    }

    @Test
    @DisplayName("O filtro por cidade ignora empresa com endereço mas sem cidade")
    void filtroPorCidadeComEnderecoSemCidade() {
        semEndereco.setEndereco(new Endereco());

        assertEquals(List.of("Studio Bela Vista"), nomes(service.buscarPorCidade("são paulo")));
    }

    @Test
    @DisplayName("Buscar estúdio por ID devolve os dados, com endereço nulo quando não tem")
    void buscarPorIdDevolveEstudio() {
        when(usuarioRepository.findById(semEndereco.getId())).thenReturn(Optional.of(semEndereco));
        when(avaliacaoRepository.calcularMediaAvaliacaoEmpresa(semEndereco.getId())).thenReturn(4.5);

        EstudioResponse response = service.buscarPorId(semEndereco.getId());

        assertEquals("Bela Unhas", response.nome());
        assertNull(response.endereco());
        assertEquals(4.5, response.mediaAvaliacoes());
    }

    @Test
    @DisplayName("Buscar o ID de um cliente como se fosse estúdio dá 404")
    void buscarClienteComoEstudioDa404() {
        when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));

        UUID clienteId = cliente.getId();

        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.buscarPorId(clienteId));
        assertEquals("Estúdio não encontrado", ex.getMessage());
    }

    @Test
    @DisplayName("Buscar ID inexistente dá 404")
    void buscarIdInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscarPorId(id));
    }

    @Test
    @DisplayName("Busca sem resultado devolve lista vazia")
    void buscaSemResultado() {
        assertTrue(service.buscarPorNome("inexistente").isEmpty());
        assertTrue(service.buscarPorCidade("Recife").isEmpty());
    }
}
