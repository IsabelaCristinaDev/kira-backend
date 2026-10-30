package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import br.com.kira.kirabackend.dto.request.AvaliacaoRequest;
import br.com.kira.kirabackend.dto.response.AvaliacaoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.com.kira.kirabackend.repository.UsuarioRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AvaliacaoServiceTest {

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private AvaliacaoService service;

    private Cliente cliente;
    private Empresa empresa;
    private Agendamento agendamento;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");

        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio Kira");

        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);

        // Nos testes, a empresa é ativa por padrão; os casos de empresa desativada sobrescrevem
        lenient().when(usuarioRepository.existsEmpresaAtivaById(empresa.getId())).thenReturn(true);
    }

    // ---------- helpers ----------

    private AvaliacaoRequest request(int nota) {
        return new AvaliacaoRequest(agendamento.getId(), nota, "Ótimo atendimento");
    }

    private void mockarAgendamento() {
        when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
    }

    private void mockarJaAvaliado(TipoAvaliador tipo, boolean jaAvaliado) {
        when(avaliacaoRepository.existsByAgendamentoIdAndTipoAvaliador(agendamento.getId(), tipo))
                .thenReturn(jaAvaliado);
    }

    private void mockarSaveDevolvendoArgumento() {
        when(avaliacaoRepository.save(any(Avaliacao.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Avaliacao avaliacao(TipoAvaliador tipo) {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setId(UUID.randomUUID());
        avaliacao.setAgendamento(agendamento);
        avaliacao.setCliente(cliente);
        avaliacao.setEmpresa(empresa);
        avaliacao.setTipoAvaliador(tipo);
        avaliacao.setNota(5);
        return avaliacao;
    }

    // ---------- avaliar como cliente ----------

    @Nested
    @DisplayName("avaliarComoCliente")
    class AvaliarComoCliente {

        @Test
        @DisplayName("Cliente avalia um agendamento concluído")
        void clienteAvaliaAgendamentoConcluido() {
            mockarAgendamento();
            mockarJaAvaliado(TipoAvaliador.CLIENTE, false);
            mockarSaveDevolvendoArgumento();

            AvaliacaoResponse response = service.avaliarComoCliente(cliente.getId(), request(5));

            ArgumentCaptor<Avaliacao> captor = ArgumentCaptor.forClass(Avaliacao.class);
            verify(avaliacaoRepository).save(captor.capture());
            Avaliacao salva = captor.getValue();
            assertEquals(TipoAvaliador.CLIENTE, salva.getTipoAvaliador());
            assertSame(agendamento, salva.getAgendamento());
            assertSame(cliente, salva.getCliente());
            assertSame(empresa, salva.getEmpresa());
            assertEquals(5, salva.getNota());
            assertEquals("Ótimo atendimento", salva.getComentario());

            assertEquals(TipoAvaliador.CLIENTE, response.tipoAvaliador());
            assertEquals("Ana", response.nomeCliente());
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = "CONCLUIDO", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Só agendamentos concluídos podem ser avaliados pelo cliente")
        void clienteNaoAvaliaAgendamentoNaoConcluido(StatusAgendamento status) {
            agendamento.setStatus(status);
            mockarAgendamento();

            UUID clienteId = cliente.getId();
            AvaliacaoRequest req = request(5);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.avaliarComoCliente(clienteId, req));
            verify(avaliacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cliente avalia uma única vez")
        void clienteAvaliaUmaUnicaVez() {
            mockarAgendamento();
            mockarJaAvaliado(TipoAvaliador.CLIENTE, true);

            UUID clienteId = cliente.getId();
            AvaliacaoRequest req = request(5);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.avaliarComoCliente(clienteId, req));
            assertEquals("Você já avaliou este atendimento", ex.getMessage());
            verify(avaliacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cliente que não participou do atendimento não pode avaliar")
        void clienteDeOutroAtendimentoNaoAvalia() {
            mockarAgendamento();

            UUID idDeFora = UUID.randomUUID();
            AvaliacaoRequest req = request(1);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.avaliarComoCliente(idDeFora, req));
            verify(avaliacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Avaliar agendamento inexistente dá 404")
        void agendamentoInexistente() {
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.empty());

            UUID clienteId = cliente.getId();
            AvaliacaoRequest req = request(5);

            assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.avaliarComoCliente(clienteId, req));
        }
    }

    // ---------- avaliar como empresa ----------

    @Nested
    @DisplayName("avaliarComoEmpresa")
    class AvaliarComoEmpresa {

        @Test
        @DisplayName("Empresa avalia um agendamento concluído")
        void empresaAvaliaAgendamentoConcluido() {
            mockarAgendamento();
            mockarJaAvaliado(TipoAvaliador.EMPRESA, false);
            mockarSaveDevolvendoArgumento();

            AvaliacaoResponse response = service.avaliarComoEmpresa(empresa.getId(), request(4));

            ArgumentCaptor<Avaliacao> captor = ArgumentCaptor.forClass(Avaliacao.class);
            verify(avaliacaoRepository).save(captor.capture());
            assertEquals(TipoAvaliador.EMPRESA, captor.getValue().getTipoAvaliador());
            assertEquals(4, captor.getValue().getNota());
            assertEquals(TipoAvaliador.EMPRESA, response.tipoAvaliador());
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = "CONCLUIDO", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Só agendamentos concluídos podem ser avaliados pela empresa")
        void empresaNaoAvaliaAgendamentoNaoConcluido(StatusAgendamento status) {
            agendamento.setStatus(status);
            mockarAgendamento();

            UUID empresaId = empresa.getId();
            AvaliacaoRequest req = request(5);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.avaliarComoEmpresa(empresaId, req));
            verify(avaliacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Empresa avalia uma única vez")
        void empresaAvaliaUmaUnicaVez() {
            mockarAgendamento();
            mockarJaAvaliado(TipoAvaliador.EMPRESA, true);

            UUID empresaId = empresa.getId();
            AvaliacaoRequest req = request(5);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.avaliarComoEmpresa(empresaId, req));
            verify(avaliacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Empresa que não atendeu não pode avaliar")
        void outraEmpresaNaoAvalia() {
            mockarAgendamento();

            UUID idDeFora = UUID.randomUUID();
            AvaliacaoRequest req = request(1);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.avaliarComoEmpresa(idDeFora, req));
            verify(avaliacaoRepository, never()).save(any());
        }
    }

    // ---------- cliente e empresa no mesmo atendimento ----------

    @Test
    @DisplayName("Cliente e empresa podem avaliar o mesmo atendimento")
    void clienteEEmpresaAvaliamOMesmoAtendimento() {
        when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
        // A empresa já avaliou, mas isso não impede a avaliação do cliente, e vice-versa.
        mockarJaAvaliado(TipoAvaliador.CLIENTE, false);
        mockarJaAvaliado(TipoAvaliador.EMPRESA, false);
        mockarSaveDevolvendoArgumento();

        service.avaliarComoCliente(cliente.getId(), request(5));
        service.avaliarComoEmpresa(empresa.getId(), request(4));

        ArgumentCaptor<Avaliacao> captor = ArgumentCaptor.forClass(Avaliacao.class);
        verify(avaliacaoRepository, times(2)).save(captor.capture());
        assertEquals(List.of(TipoAvaliador.CLIENTE, TipoAvaliador.EMPRESA),
                captor.getAllValues().stream().map(Avaliacao::getTipoAvaliador).toList());
    }

    // ---------- listagens ----------

    @Test
    @DisplayName("A listagem da empresa traz apenas as avaliações feitas por clientes")
    void listagemDaEmpresaSoTrazAvaliacoesDeClientes() {
        when(avaliacaoRepository.findByEmpresaIdAndTipoAvaliador(empresa.getId(), TipoAvaliador.CLIENTE))
                .thenReturn(List.of(avaliacao(TipoAvaliador.CLIENTE)));

        List<AvaliacaoResponse> avaliacoes = service.listarAvaliacoesEmpresa(empresa.getId());

        assertEquals(1, avaliacoes.size());
        assertEquals(TipoAvaliador.CLIENTE, avaliacoes.get(0).tipoAvaliador());
        verify(avaliacaoRepository, never()).findByEmpresaIdAndTipoAvaliador(empresa.getId(), TipoAvaliador.EMPRESA);
    }

    @Test
    @DisplayName("A listagem do cliente traz apenas as avaliações feitas por empresas")
    void listagemDoClienteSoTrazAvaliacoesDeEmpresas() {
        when(avaliacaoRepository.findByClienteIdAndTipoAvaliador(cliente.getId(), TipoAvaliador.EMPRESA))
                .thenReturn(List.of(avaliacao(TipoAvaliador.EMPRESA)));

        List<AvaliacaoResponse> avaliacoes = service.listarAvaliacoesCliente(cliente.getId());

        assertEquals(1, avaliacoes.size());
        assertEquals(TipoAvaliador.EMPRESA, avaliacoes.get(0).tipoAvaliador());
        verify(avaliacaoRepository, never()).findByClienteIdAndTipoAvaliador(cliente.getId(), TipoAvaliador.CLIENTE);
    }

    @Test
    @DisplayName("Avaliações e média de empresa desativada dão 404")
    void empresaDesativadaDa404() {
        UUID empresaId = empresa.getId();
        when(usuarioRepository.existsEmpresaAtivaById(empresaId)).thenReturn(false);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.listarAvaliacoesEmpresa(empresaId));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.mediaAvaliacaoEmpresa(empresaId));
        verifyNoInteractions(avaliacaoRepository);
    }
}
