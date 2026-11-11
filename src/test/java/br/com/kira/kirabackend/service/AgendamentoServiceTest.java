package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.DisponibilidadeEstudio;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
import br.com.kira.kirabackend.domain.enums.FormaPagamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoAvisoAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.dto.request.AgendamentoRequest;
import br.com.kira.kirabackend.dto.request.ReagendamentoRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.AcessoNegadoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoObrigatoriaException;
import br.com.kira.kirabackend.observer.AgendamentoCanceladoEvent;
import br.com.kira.kirabackend.observer.AgendamentoCriadoEvent;
import br.com.kira.kirabackend.observer.AtendimentoConcluidoEvent;
import br.com.kira.kirabackend.observer.AvisoAgendamentoEvent;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.BloqueioHorarioRepository;
import br.com.kira.kirabackend.repository.DisponibilidadeEstudioRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.service.strategy.CancelamentoStrategy;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    private static final int DURACAO_SERVICO = 90;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private FuncionariaRepository funcionariaRepository;

    @Mock
    private DisponibilidadeEstudioRepository disponibilidadeRepository;

    @Mock
    private CancelamentoStrategy cancelamentoClienteStrategy;

    @Mock
    private CancelamentoStrategy cancelamentoEmpresaStrategy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private BloqueioHorarioRepository bloqueioHorarioRepository;

    // Mock sem regra: não recusa nada, como com VERIFICACAO_OBRIGATORIA=false
    @Mock
    private ExigenciaVerificacao exigenciaVerificacao;

    private AgendamentoService service;

    private Cliente cliente;
    private Empresa empresa;
    private Servico servico;
    private Funcionaria funcionaria;

    /** Próxima segunda-feira às 10h: sempre no futuro e dentro do expediente padrão (09h–18h). */
    private LocalDateTime inicioValido;

    @BeforeEach
    void setUp() {
        lenient().when(cancelamentoClienteStrategy.getTipo()).thenReturn(TipoUsuario.CLIENTE);
        lenient().when(cancelamentoEmpresaStrategy.getTipo()).thenReturn(TipoUsuario.EMPRESA);

        service = new AgendamentoService(
                agendamentoRepository,
                usuarioRepository,
                servicoRepository,
                funcionariaRepository,
                disponibilidadeRepository,
                List.of(cancelamentoClienteStrategy, cancelamentoEmpresaStrategy),
                eventPublisher,
                new OcupacaoService(agendamentoRepository, funcionariaRepository, bloqueioHorarioRepository),
                exigenciaVerificacao);

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Cliente");

        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio");

        servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setNome("Design de sobrancelha");
        servico.setDuracaoMinutos(DURACAO_SERVICO);
        servico.setEmpresa(empresa);

        funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setNome("Funcionária");
        funcionaria.setEmpresa(empresa);

        inicioValido = LocalDate.now(KiraTimeZone.DEFAULT)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atTime(10, 0);
    }

    // ---------- helpers ----------

    private AgendamentoRequest request(LocalDateTime inicio) {
        return new AgendamentoRequest(empresa.getId(), funcionaria.getId(), servico.getId(),
                inicio, null, null);
    }

    private Agendamento agendamentoExistente(StatusAgendamento status) {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setServico(servico);
        agendamento.setDataHoraInicio(inicioValido);
        agendamento.setDataHoraFim(inicioValido.plusMinutes(DURACAO_SERVICO));
        agendamento.setStatus(status);
        agendamento.setLembreteEnviado(true);
        return agendamento;
    }

    private void mockarEntidadesDaCriacao() {
        when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));
    }

    private void mockarExpediente(LocalTime abertura, LocalTime fechamento) {
        DisponibilidadeEstudio disponibilidade = new DisponibilidadeEstudio();
        disponibilidade.setEmpresa(empresa);
        disponibilidade.setDiaSemana(DiaSemana.SEGUNDA);
        disponibilidade.setHoraInicio(abertura);
        disponibilidade.setHoraFim(fechamento);
        when(disponibilidadeRepository.findByEmpresaIdAndDiaSemana(empresa.getId(), DiaSemana.SEGUNDA))
                .thenReturn(Optional.of(disponibilidade));
    }

    private void mockarExpedientePadrao() {
        mockarExpediente(LocalTime.of(9, 0), LocalTime.of(18, 0));
    }

    private void mockarAgendamentosNoIntervalo(List<Agendamento> agendamentos) {
        when(agendamentoRepository.findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
                eq(empresa.getId()), eq(StatusAgendamento.CANCELADO),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(agendamentos);
    }

    private void mockarSaveDevolvendoArgumento() {
        when(agendamentoRepository.save(any(Agendamento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** A trava só protege se vier antes da leitura dos agendamentos do dia, e a gravação depois das duas. */
    private void conferirTravaAntesDaConferenciaDoHorario() {
        InOrder ordem = inOrder(usuarioRepository, agendamentoRepository);
        ordem.verify(usuarioRepository).travarEmpresaParaAgendar(empresa.getId());
        ordem.verify(agendamentoRepository).findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
                eq(empresa.getId()), eq(StatusAgendamento.CANCELADO),
                any(LocalDateTime.class), any(LocalDateTime.class));
        ordem.verify(agendamentoRepository).save(any(Agendamento.class));
    }

    // ---------- criar ----------

    @Nested
    @DisplayName("criar")
    class Criar {

        @Test
        @DisplayName("Deve salvar com horário de fim = início + duração do serviço e disparar o evento")
        void deveCriarAgendamento() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.criar(cliente.getId(), cliente.getId(), request(inicioValido));

            ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
            verify(agendamentoRepository).save(captor.capture());
            Agendamento salvo = captor.getValue();

            assertEquals(inicioValido, salvo.getDataHoraInicio());
            assertEquals(inicioValido.plusMinutes(DURACAO_SERVICO), salvo.getDataHoraFim());
            assertEquals(StatusAgendamento.PENDENTE, salvo.getStatus());
            assertSame(cliente, salvo.getCliente());
            assertSame(empresa, salvo.getEmpresa());
            assertSame(servico, salvo.getServico());
            assertSame(funcionaria, salvo.getFuncionaria());
            assertEquals(inicioValido.plusMinutes(DURACAO_SERVICO), response.dataHoraFim());

            ArgumentCaptor<ApplicationEvent> evento = ArgumentCaptor.forClass(ApplicationEvent.class);
            verify(eventPublisher).publishEvent(evento.capture());
            AgendamentoCriadoEvent criado = assertInstanceOf(AgendamentoCriadoEvent.class, evento.getValue());
            assertSame(salvo, criado.getAgendamento());
        }

        @Test
        @DisplayName("Trava a empresa antes de conferir o horário, para pedidos simultâneos não pegarem o mesmo horário")
        void deveTravarEmpresaAntesDeConferirOHorario() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            service.criar(cliente.getId(), cliente.getId(), request(inicioValido));

            conferirTravaAntesDaConferenciaDoHorario();
        }

        @Test
        @DisplayName("Guarda no agendamento o preço que o serviço tem no momento e devolve na resposta")
        void deveGuardarOValorDoServicoNoAgendamento() {
            servico.setPreco(new BigDecimal("80.00"));
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.criar(cliente.getId(), cliente.getId(), request(inicioValido));

            ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
            verify(agendamentoRepository).save(captor.capture());
            assertEquals(new BigDecimal("80.00"), captor.getValue().getValor());
            assertEquals(new BigDecimal("80.00"), response.valor());
        }

        @Test
        @DisplayName("Se o estúdio muda o preço do serviço depois, o valor do agendamento não muda")
        void mudarOPrecoDoServicoNaoMudaOValorDoAgendamento() {
            servico.setPreco(new BigDecimal("80.00"));
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            service.criar(cliente.getId(), cliente.getId(), request(inicioValido));
            servico.setPreco(new BigDecimal("120.00"));

            ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
            verify(agendamentoRepository).save(captor.capture());
            assertEquals(new BigDecimal("80.00"), captor.getValue().getValor());
        }

        @Test
        @DisplayName("Com a verificação obrigatória, cliente sem verificação não agenda e nada é salvo")
        void clienteSemVerificacaoNaoAgenda() {
            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);
            doThrow(new VerificacaoObrigatoriaException("Verifique sua identidade para agendar"))
                    .when(exigenciaVerificacao).exigirClienteVerificada(clienteId);

            assertThrows(VerificacaoObrigatoriaException.class, () -> service.criar(clienteId, clienteId, req));

            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Com a verificação obrigatória, estúdio sem verificação não recebe agendamento")
        void estudioSemVerificacaoNaoRecebe() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            doThrow(new RegraDeNegocioException("Este estúdio ainda não pode receber agendamentos"))
                    .when(exigenciaVerificacao).exigirEstudioVerificado(empresa.getId());
            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            assertThrows(RegraDeNegocioException.class, () -> service.criar(clienteId, clienteId, req));

            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Confere a cliente e o estúdio ao agendar")
        void confereAVerificacao() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            service.criar(cliente.getId(), cliente.getId(), request(inicioValido));

            verify(exigenciaVerificacao).exigirClienteVerificada(cliente.getId());
            verify(exigenciaVerificacao).exigirEstudioVerificado(empresa.getId());
        }

        @Test
        @DisplayName("Deve dar erro quando já existe agendamento sobreposto no estabelecimento")
        void deveRejeitarConflitoDeHorario() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of(agendamentoExistente(StatusAgendamento.PENDENTE)));

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido.plusMinutes(30));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));

            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve consultar os agendamentos do dia ignorando cancelados")
        void deveConsultarConflitoComIntervaloCorreto() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            service.criar(cliente.getId(), cliente.getId(), request(inicioValido));

            verify(agendamentoRepository).findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
                    empresa.getId(), StatusAgendamento.CANCELADO,
                    inicioValido.toLocalDate().plusDays(1).atStartOfDay(), inicioValido.toLocalDate().atStartOfDay());
        }

        @Test
        @DisplayName("Deve dar 404 quando o cliente não existe")
        void deveFalharClienteInexistente() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.empty());

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Cliente não encontrado", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar 404 quando a empresa não existe")
        void deveFalharEmpresaInexistente() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.empty());

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Empresa não encontrada", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar 404 quando a empresa está desativada")
        void deveFalharEmpresaDesativada() {
            empresa.setAtivo(false);
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Empresa não encontrada", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar 404 quando o ID da empresa é de um cliente")
        void deveFalharEmpresaQueEhCliente() {
            Cliente outroCliente = new Cliente();
            outroCliente.setId(empresa.getId());
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(outroCliente));

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Empresa não encontrada", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar 404 quando o serviço não existe")
        void deveFalharServicoInexistente() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.empty());

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Serviço não encontrado", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar 404 quando a funcionária não existe")
        void deveFalharFuncionariaInexistente() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
            when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.empty());

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Funcionária não encontrada", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando a funcionária é de outra empresa")
        void deveFalharFuncionariaDeOutraEmpresa() {
            Empresa outraEmpresa = new Empresa();
            outraEmpresa.setId(UUID.randomUUID());
            funcionaria.setEmpresa(outraEmpresa);
            mockarEntidadesDaCriacao();

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Funcionária não pertence a este estabelecimento", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando a funcionária foi desativada")
        void deveFalharFuncionariaInativa() {
            funcionaria.setAtivo(false);
            mockarEntidadesDaCriacao();

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Funcionária não está mais atendendo", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando o serviço pertence a outra empresa")
        void deveFalharServicoDeOutraEmpresa() {
            Empresa outraEmpresa = new Empresa();
            outraEmpresa.setId(UUID.randomUUID());
            servico.setEmpresa(outraEmpresa);

            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando o serviço não tem empresa cadastrada")
        void deveFalharServicoSemEmpresa() {
            servico.setEmpresa(null);
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("Serviço não pertence a este estabelecimento", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando a data está no passado")
        void deveFalharDataNoPassado() {
            mockarEntidadesDaCriacao();
            LocalDateTime ontem = LocalDateTime.now(KiraTimeZone.DEFAULT).minusDays(1);

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(ontem);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando começa antes da abertura do expediente")
        void deveFalharAntesDoExpediente() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido.withHour(8));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando termina depois do fechamento do expediente")
        void deveFalharDepoisDoExpediente() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();

            // 17h + 90min = 18h30, após o fechamento às 18h
            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido.withHour(17));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando a empresa não atende no dia da semana")
        void deveFalharDiaSemExpediente() {
            mockarEntidadesDaCriacao();
            when(disponibilidadeRepository.findByEmpresaIdAndDiaSemana(empresa.getId(), DiaSemana.SEGUNDA))
                    .thenReturn(Optional.empty());

            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            verify(agendamentoRepository, never()).save(any());
        }
    }

    /** O aviso à cliente vai por publishEvent(Object); os eventos antigos usam publishEvent(ApplicationEvent). */
    private AvisoAgendamentoEvent avisoPublicado() {
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(evento.capture());
        return assertInstanceOf(AvisoAgendamentoEvent.class, evento.getValue());
    }

    private void semAviso() {
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ---------- confirmar ----------

    @Nested
    @DisplayName("confirmar")
    class Confirmar {

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = {"PENDENTE", "REAGENDADO"})
        @DisplayName("Confirmar um agendamento novo ou remarcado muda o status")
        void deveConfirmar(StatusAgendamento status) {
            Agendamento agendamento = agendamentoExistente(status);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.confirmar(agendamento.getId(), empresa.getId());

            assertEquals(StatusAgendamento.CONFIRMADO, response.status());
            assertEquals(StatusAgendamento.CONFIRMADO, agendamento.getStatus());
            // O evento leva os dados prontos: o aviso à cliente sai em outra thread, sem ler relação LAZY
            assertEquals(new AvisoAgendamentoEvent(response, TipoAvisoAgendamento.CONFIRMADO), avisoPublicado());
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = {"CONFIRMADO", "CANCELADO", "CONCLUIDO"})
        @DisplayName("Não confirma agendamento já confirmado, cancelado ou concluído")
        void naoConfirmaOutroStatus(StatusAgendamento status) {
            Agendamento agendamento = agendamentoExistente(status);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.confirmar(agendamento.getId(), empresa.getId()));

            assertEquals(status, agendamento.getStatus());
            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Outro estúdio não confirma")
        void outroEstudioNaoConfirma() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            assertThrows(AcessoNegadoException.class,
                    () -> service.confirmar(agendamento.getId(), UUID.randomUUID()));

            assertEquals(StatusAgendamento.PENDENTE, agendamento.getStatus());
            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    // ---------- concluir ----------

    @Nested
    @DisplayName("concluir")
    class Concluir {

        @Test
        @DisplayName("Concluir um agendamento pendente muda o status e dispara o evento de atendimento concluído")
        void deveConcluirPendente() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.concluir(agendamento.getId(), empresa.getId(), null);

            assertEquals(StatusAgendamento.CONCLUIDO, response.status());
            assertEquals(StatusAgendamento.CONCLUIDO, agendamento.getStatus());

            ArgumentCaptor<ApplicationEvent> evento = ArgumentCaptor.forClass(ApplicationEvent.class);
            verify(eventPublisher).publishEvent(evento.capture());
            AtendimentoConcluidoEvent concluido =
                    assertInstanceOf(AtendimentoConcluidoEvent.class, evento.getValue());
            assertSame(agendamento, concluido.getAgendamento());
        }

        @Test
        @DisplayName("Ao concluir, a forma informada pelo estúdio substitui a do agendamento")
        void deveRegistrarFormaInformada() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            agendamento.setFormaPagamento(FormaPagamento.PIX);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.concluir(agendamento.getId(), empresa.getId(),
                    FormaPagamento.DINHEIRO);

            assertEquals(FormaPagamento.DINHEIRO, response.formaPagamento());
            assertEquals(FormaPagamento.DINHEIRO, agendamento.getFormaPagamento());
        }

        @Test
        @DisplayName("Ao concluir sem informar a forma, fica a que veio no agendamento")
        void deveManterFormaDoAgendamento() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            agendamento.setFormaPagamento(FormaPagamento.PIX);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.concluir(agendamento.getId(), empresa.getId(), null);

            assertEquals(FormaPagamento.PIX, response.formaPagamento());
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = {"CANCELADO", "CONCLUIDO"})
        @DisplayName("Concluir um agendamento cancelado ou já concluído deve dar erro")
        void deveRejeitarConclusaoInvalida(StatusAgendamento status) {
            Agendamento agendamento = agendamentoExistente(status);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            UUID agendamentoId = agendamento.getId();
            UUID empresaId = empresa.getId();

            assertThrows(RegraDeNegocioException.class, () -> service.concluir(agendamentoId, empresaId, null));

            assertEquals(status, agendamento.getStatus());
            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Concluir um agendamento inexistente dá 404")
        void deveFalharAgendamentoInexistente() {
            UUID id = UUID.randomUUID();
            when(agendamentoRepository.findById(id)).thenReturn(Optional.empty());
            UUID empresaId = empresa.getId();

            assertThrows(RecursoNaoEncontradoException.class, () -> service.concluir(id, empresaId, null));
        }

        @Test
        @DisplayName("Outra empresa não pode concluir o atendimento")
        void outraEmpresaNaoPodeConcluir() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            UUID agendamentoId = agendamento.getId();
            UUID outraEmpresa = UUID.randomUUID();
            when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

            AcessoNegadoException ex = assertThrows(AcessoNegadoException.class,
                    () -> service.concluir(agendamentoId, outraEmpresa, FormaPagamento.PIX));

            assertEquals("Este atendimento não pertence ao seu estabelecimento", ex.getMessage());
            assertEquals(StatusAgendamento.CONFIRMADO, agendamento.getStatus());
            assertNull(agendamento.getFormaPagamento());
            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    // ---------- corrigir forma de pagamento ----------

    @Nested
    @DisplayName("corrigirFormaPagamento")
    class CorrigirFormaPagamento {

        @Test
        @DisplayName("O estúdio corrige a forma de um atendimento concluído sem disparar evento")
        void deveCorrigirFormaDeConcluido() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONCLUIDO);
            agendamento.setFormaPagamento(FormaPagamento.PIX);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.corrigirFormaPagamento(agendamento.getId(), empresa.getId(),
                    FormaPagamento.CARTAO_CREDITO);

            assertEquals(FormaPagamento.CARTAO_CREDITO, response.formaPagamento());
            assertEquals(FormaPagamento.CARTAO_CREDITO, agendamento.getFormaPagamento());
            assertEquals(StatusAgendamento.CONCLUIDO, agendamento.getStatus());
            verify(agendamentoRepository).save(agendamento);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Atendimento concluído sem forma registrada passa a ter a forma informada")
        void deveRegistrarFormaQuandoNaoHavia() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONCLUIDO);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.corrigirFormaPagamento(agendamento.getId(), empresa.getId(),
                    FormaPagamento.DINHEIRO);

            assertEquals(FormaPagamento.DINHEIRO, response.formaPagamento());
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = "CONCLUIDO", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Só atendimento concluído tem a forma corrigida")
        void deveRejeitarStatusDiferenteDeConcluido(StatusAgendamento status) {
            Agendamento agendamento = agendamentoExistente(status);
            agendamento.setFormaPagamento(FormaPagamento.PIX);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            UUID agendamentoId = agendamento.getId();
            UUID empresaId = empresa.getId();

            assertThrows(RegraDeNegocioException.class,
                    () -> service.corrigirFormaPagamento(agendamentoId, empresaId, FormaPagamento.DINHEIRO));

            assertEquals(FormaPagamento.PIX, agendamento.getFormaPagamento());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Sem forma informada dá erro e nada muda")
        void deveExigirForma() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONCLUIDO);
            agendamento.setFormaPagamento(FormaPagamento.PIX);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            UUID agendamentoId = agendamento.getId();
            UUID empresaId = empresa.getId();

            assertThrows(RegraDeNegocioException.class,
                    () -> service.corrigirFormaPagamento(agendamentoId, empresaId, null));

            assertEquals(FormaPagamento.PIX, agendamento.getFormaPagamento());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Agendamento inexistente dá 404")
        void deveFalharAgendamentoInexistente() {
            UUID id = UUID.randomUUID();
            when(agendamentoRepository.findById(id)).thenReturn(Optional.empty());
            UUID empresaId = empresa.getId();

            assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.corrigirFormaPagamento(id, empresaId, FormaPagamento.PIX));
        }

        @Test
        @DisplayName("Outra empresa não corrige a forma do atendimento")
        void outraEmpresaNaoPodeCorrigir() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONCLUIDO);
            agendamento.setFormaPagamento(FormaPagamento.PIX);
            UUID agendamentoId = agendamento.getId();
            UUID outraEmpresa = UUID.randomUUID();
            when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

            AcessoNegadoException ex = assertThrows(AcessoNegadoException.class,
                    () -> service.corrigirFormaPagamento(agendamentoId, outraEmpresa, FormaPagamento.DINHEIRO));

            assertEquals("Este atendimento não pertence ao seu estabelecimento", ex.getMessage());
            assertEquals(FormaPagamento.PIX, agendamento.getFormaPagamento());
            verify(agendamentoRepository, never()).save(any());
        }
    }

    // ---------- reagendar ----------

    @Nested
    @DisplayName("reagendar")
    class Reagendar {

        @Test
        @DisplayName("Reagendar recalcula o fim, marca REAGENDADO e volta lembreteEnviado para falso")
        void deveReagendar() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            LocalDateTime novoInicio = inicioValido.withHour(14);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.reagendarPeloCliente(agendamento.getId(), cliente.getId(),
                    new ReagendamentoRequest(novoInicio, "Imprevisto"));
            // Quem remarcou foi a própria cliente: não há o que avisar
            semAviso();

            assertEquals(novoInicio, agendamento.getDataHoraInicio());
            assertEquals(novoInicio.plusMinutes(DURACAO_SERVICO), agendamento.getDataHoraFim());
            assertEquals(StatusAgendamento.REAGENDADO, agendamento.getStatus());
            assertFalse(agendamento.getLembreteEnviado());
            assertEquals(StatusAgendamento.REAGENDADO, response.status());
            verify(agendamentoRepository).save(agendamento);
        }

        @Test
        @DisplayName("Reagendar também trava a empresa antes de conferir o novo horário")
        void deveTravarEmpresaAntesDeConferirONovoHorario() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            service.reagendarPelaEmpresa(agendamento.getId(), empresa.getId(),
                    new ReagendamentoRequest(inicioValido.withHour(14), null));

            conferirTravaAntesDaConferenciaDoHorario();
        }

        @Test
        @DisplayName("Cliente não remarca com menos de 2 horas de antecedência")
        void clienteNaoRemarcaEmCimaDaHora() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            LocalDateTime daquiAUmaHora = LocalDateTime.now(KiraTimeZone.DEFAULT).plusHours(1);
            agendamento.setDataHoraInicio(daquiAUmaHora);
            UUID agendamentoId = agendamento.getId();
            UUID clienteId = cliente.getId();
            var request = new ReagendamentoRequest(inicioValido.withHour(14), null);
            when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamentoId, clienteId, request));

            assertEquals("Remarcação pelo cliente deve ser feita com no mínimo 2 horas de antecedência",
                    ex.getMessage());
            assertEquals(daquiAUmaHora, agendamento.getDataHoraInicio());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("A empresa pode remarcar mesmo em cima da hora")
        void empresaRemarcaEmCimaDaHora() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            agendamento.setDataHoraInicio(LocalDateTime.now(KiraTimeZone.DEFAULT).plusHours(1));
            LocalDateTime novoInicio = inicioValido.withHour(14);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.reagendarPelaEmpresa(agendamento.getId(), empresa.getId(),
                    new ReagendamentoRequest(novoInicio, null));

            assertEquals(novoInicio, agendamento.getDataHoraInicio());
            assertEquals(new AvisoAgendamentoEvent(response, TipoAvisoAgendamento.REMARCADO_PELO_ESTUDIO),
                    avisoPublicado());
        }

        @Test
        @DisplayName("Reagendar não acusa conflito com o próprio agendamento")
        void deveIgnorarOProprioAgendamentoNoConflito() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of(agendamento));
            mockarSaveDevolvendoArgumento();

            service.reagendarPelaEmpresa(agendamento.getId(), empresa.getId(),
                    new ReagendamentoRequest(inicioValido.plusMinutes(30), null));

            assertEquals(StatusAgendamento.REAGENDADO, agendamento.getStatus());
        }

        @Test
        @DisplayName("Reagendar para horário ocupado por outro agendamento dá erro")
        void deveRejeitarConflitoNoReagendamento() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            Agendamento outro = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            outro.setDataHoraInicio(inicioValido.withHour(14));
            outro.setDataHoraFim(inicioValido.withHour(14).plusMinutes(DURACAO_SERVICO));
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of(outro));

            UUID agendamentoId = agendamento.getId();
            UUID clienteId = cliente.getId();
            ReagendamentoRequest req = new ReagendamentoRequest(inicioValido.withHour(14), null);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamentoId, clienteId, req));
            verify(agendamentoRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = {"CANCELADO", "CONCLUIDO"})
        @DisplayName("Reagendar um agendamento cancelado ou concluído dá erro")
        void deveRejeitarReagendamentoInvalido(StatusAgendamento status) {
            Agendamento agendamento = agendamentoExistente(status);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            UUID agendamentoId = agendamento.getId();
            UUID clienteId = cliente.getId();
            ReagendamentoRequest req = new ReagendamentoRequest(inicioValido.withHour(14), null);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamentoId, clienteId, req));

            assertEquals(status, agendamento.getStatus());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Reagendar para uma data no passado dá erro")
        void deveRejeitarReagendamentoNoPassado() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            UUID agendamentoId = agendamento.getId();
            UUID clienteId = cliente.getId();
            ReagendamentoRequest req = new ReagendamentoRequest(LocalDateTime.now(KiraTimeZone.DEFAULT).minusHours(1), null);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamentoId, clienteId, req));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cliente não remarca agendamento de empresa desativada")
        void naoRemarcaComEmpresaDesativada() {
            empresa.setAtivo(false);
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            UUID agendamentoId = agendamento.getId();
            UUID clienteId = cliente.getId();
            ReagendamentoRequest req = new ReagendamentoRequest(inicioValido.withHour(14), null);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamentoId, clienteId, req));
            assertEquals("Este estabelecimento não está mais disponível", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }
    }

    // ---------- cancelar ----------

    @Nested
    @DisplayName("cancelar")
    class Cancelar {

        @Test
        @DisplayName("Cancelar pelo cliente usa a estratégia do cliente e dispara o evento")
        void deveCancelarPeloCliente() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.cancelarPeloCliente(agendamento.getId(), cliente.getId());

            verify(cancelamentoClienteStrategy).validarCancelamento(agendamento);
            verify(cancelamentoEmpresaStrategy, never()).validarCancelamento(any());
            assertEquals(StatusAgendamento.CANCELADO, response.status());
            assertCanceladoComEvento(agendamento);
            semAviso();
        }

        @Test
        @DisplayName("Cliente ainda cancela agendamento de empresa desativada")
        void clienteCancelaComEmpresaDesativada() {
            empresa.setAtivo(false);
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.cancelarPeloCliente(agendamento.getId(), cliente.getId());

            assertEquals(StatusAgendamento.CANCELADO, response.status());
            assertCanceladoComEvento(agendamento);
        }

        @Test
        @DisplayName("Cancelar pela empresa usa a estratégia da empresa e dispara o evento")
        void deveCancelarPelaEmpresa() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarSaveDevolvendoArgumento();

            AgendamentoResponse response = service.cancelarPelaEmpresa(agendamento.getId(), empresa.getId());

            verify(cancelamentoEmpresaStrategy).validarCancelamento(agendamento);
            verify(cancelamentoClienteStrategy, never()).validarCancelamento(any());
            assertEquals(StatusAgendamento.CANCELADO, response.status());
            assertCanceladoComEvento(agendamento);
            assertEquals(new AvisoAgendamentoEvent(response, TipoAvisoAgendamento.CANCELADO_PELO_ESTUDIO),
                    avisoPublicado());
        }

        @Test
        @DisplayName("Quando a estratégia recusa o cancelamento, nada é salvo nem publicado")
        void naoDeveCancelarQuandoEstrategiaRecusa() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            doThrow(new RegraDeNegocioException("Antecedência mínima"))
                    .when(cancelamentoClienteStrategy).validarCancelamento(agendamento);

            UUID agendamentoId = agendamento.getId();
            UUID clienteId = cliente.getId();

            assertThrows(RegraDeNegocioException.class,
                    () -> service.cancelarPeloCliente(agendamentoId, clienteId));

            assertEquals(StatusAgendamento.PENDENTE, agendamento.getStatus());
            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Outra empresa não pode cancelar o atendimento como empresa")
        void outraEmpresaNaoPodeCancelar() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            UUID agendamentoId = agendamento.getId();
            UUID outraEmpresa = UUID.randomUUID();
            when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

            AcessoNegadoException ex = assertThrows(AcessoNegadoException.class,
                    () -> service.cancelarPelaEmpresa(agendamentoId, outraEmpresa));

            assertEquals("Este atendimento não pertence ao seu estabelecimento", ex.getMessage());
            verify(cancelamentoEmpresaStrategy, never()).validarCancelamento(any());
            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        private void assertCanceladoComEvento(Agendamento agendamento) {
            assertEquals(StatusAgendamento.CANCELADO, agendamento.getStatus());
            verify(agendamentoRepository).save(agendamento);

            ArgumentCaptor<ApplicationEvent> evento = ArgumentCaptor.forClass(ApplicationEvent.class);
            verify(eventPublisher).publishEvent(evento.capture());
            AgendamentoCanceladoEvent cancelado =
                    assertInstanceOf(AgendamentoCanceladoEvent.class, evento.getValue());
            assertSame(agendamento, cancelado.getAgendamento());
        }
    }

    // ---------- dono do recurso ----------

    @Nested
    @DisplayName("acesso só do próprio usuário")
    class AcessoDoProprioUsuario {

        private final UUID outroUsuario = UUID.randomUUID();

        @Test
        @DisplayName("Não agenda em nome de outra cliente")
        void naoAgendaEmNomeDeOutraCliente() {
            AgendamentoRequest request = request(inicioValido);
            UUID clienteId = cliente.getId();

            AcessoNegadoException ex = assertThrows(AcessoNegadoException.class,
                    () -> service.criar(clienteId, outroUsuario, request));

            assertEquals("Você só pode agendar em seu próprio nome", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Não lista os agendamentos de outra cliente nem de outra empresa")
        void naoListaAgendamentosDeOutros() {
            UUID clienteId = cliente.getId();
            UUID empresaId = empresa.getId();

            assertThrows(AcessoNegadoException.class, () -> service.listarPorCliente(clienteId, outroUsuario));
            assertThrows(AcessoNegadoException.class, () -> service.listarPorEmpresa(empresaId, outroUsuario));
            assertThrows(AcessoNegadoException.class, () -> service.historicoCliente(clienteId, outroUsuario));
            verifyNoInteractions(agendamentoRepository);
        }

        @Test
        @DisplayName("Não cancela o agendamento de outra cliente")
        void naoCancelaAgendamentoDeOutraCliente() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            UUID agendamentoId = agendamento.getId();
            when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

            AcessoNegadoException ex = assertThrows(AcessoNegadoException.class,
                    () -> service.cancelarPeloCliente(agendamentoId, outroUsuario));

            assertEquals("Você não participou deste atendimento", ex.getMessage());
            assertEquals(StatusAgendamento.PENDENTE, agendamento.getStatus());
            verify(cancelamentoClienteStrategy, never()).validarCancelamento(any());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Não reagenda o agendamento de outra cliente nem de outra empresa")
        void naoReagendaAgendamentoDeOutros() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            UUID agendamentoId = agendamento.getId();
            var reagendamento = new ReagendamentoRequest(inicioValido.plusDays(1), null);
            when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

            assertThrows(AcessoNegadoException.class,
                    () -> service.reagendarPeloCliente(agendamentoId, outroUsuario, reagendamento));
            assertThrows(AcessoNegadoException.class,
                    () -> service.reagendarPelaEmpresa(agendamentoId, outroUsuario, reagendamento));
            assertEquals(inicioValido, agendamento.getDataHoraInicio());
            verify(agendamentoRepository, never()).save(any());
        }
    }

    // ---------- listagens ----------

    @Nested
    @DisplayName("listagens")
    class Listagens {

        @Test
        @DisplayName("A cliente vê os próprios agendamentos convertidos em resposta")
        void clienteListaOsProprios() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            agendamento.setFuncionaria(funcionaria);
            when(agendamentoRepository.findByClienteId(cliente.getId())).thenReturn(List.of(agendamento));

            List<AgendamentoResponse> lista = service.listarPorCliente(cliente.getId(), cliente.getId());

            assertEquals(1, lista.size());
            AgendamentoResponse resposta = lista.get(0);
            assertEquals(agendamento.getId(), resposta.id());
            assertEquals(cliente.getId(), resposta.clienteId());
            assertEquals(empresa.getId(), resposta.empresaId());
            assertEquals(funcionaria.getId(), resposta.funcionariaId());
            assertEquals(servico.getNome(), resposta.nomeServico());
            assertEquals(StatusAgendamento.PENDENTE, resposta.status());
        }

        @Test
        @DisplayName("A empresa vê os agendamentos do próprio estabelecimento, mesmo sem funcionária")
        void empresaListaOsProprios() {
            Agendamento semFuncionaria = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            Agendamento comFuncionaria = agendamentoExistente(StatusAgendamento.PENDENTE);
            comFuncionaria.setFuncionaria(funcionaria);
            when(agendamentoRepository.findByEmpresaId(empresa.getId()))
                    .thenReturn(List.of(semFuncionaria, comFuncionaria));

            List<AgendamentoResponse> lista = service.listarPorEmpresa(empresa.getId(), empresa.getId());

            assertEquals(2, lista.size());
            assertEquals(semFuncionaria.getId(), lista.get(0).id());
            assertNull(lista.get(0).funcionariaId());
            assertEquals(comFuncionaria.getId(), lista.get(1).id());
            assertEquals(funcionaria.getId(), lista.get(1).funcionariaId());
        }

        @Test
        @DisplayName("O histórico da cliente busca só os atendimentos concluídos, mantendo a ordem do banco")
        void historicoBuscaSoConcluidos() {
            Agendamento recente = agendamentoExistente(StatusAgendamento.CONCLUIDO);
            Agendamento antigo = agendamentoExistente(StatusAgendamento.CONCLUIDO);
            antigo.setDataHoraInicio(inicioValido.minusDays(30));
            when(agendamentoRepository.findByClienteIdAndStatusOrderByDataHoraInicioDesc(
                    cliente.getId(), StatusAgendamento.CONCLUIDO)).thenReturn(List.of(recente, antigo));

            List<AgendamentoResponse> historico = service.historicoCliente(cliente.getId(), cliente.getId());

            assertEquals(List.of(recente.getId(), antigo.getId()),
                    historico.stream().map(AgendamentoResponse::id).toList());
            verify(agendamentoRepository).findByClienteIdAndStatusOrderByDataHoraInicioDesc(
                    cliente.getId(), StatusAgendamento.CONCLUIDO);
        }

        @Test
        @DisplayName("Sem agendamentos, as listagens devolvem lista vazia em vez de erro")
        void semAgendamentosDevolveListaVazia() {
            when(agendamentoRepository.findByClienteId(cliente.getId())).thenReturn(List.of());
            when(agendamentoRepository.findByEmpresaId(empresa.getId())).thenReturn(List.of());
            when(agendamentoRepository.findByClienteIdAndStatusOrderByDataHoraInicioDesc(
                    cliente.getId(), StatusAgendamento.CONCLUIDO)).thenReturn(List.of());

            assertEquals(List.of(), service.listarPorCliente(cliente.getId(), cliente.getId()));
            assertEquals(List.of(), service.listarPorEmpresa(empresa.getId(), empresa.getId()));
            assertEquals(List.of(), service.historicoCliente(cliente.getId(), cliente.getId()));
        }
    }

    // ---------- horário por funcionária ----------

    @Nested
    @DisplayName("horário por funcionária")
    class HorarioPorFuncionaria {

        private Funcionaria bia;

        @BeforeEach
        void setUp() {
            funcionaria.setNome("Ana");
            bia = new Funcionaria();
            bia.setId(UUID.randomUUID());
            bia.setNome("Bia");
            bia.setEmpresa(empresa);
            when(funcionariaRepository.findByEmpresaIdAndAtivoTrue(empresa.getId()))
                    .thenReturn(List.of(funcionaria, bia));
            when(bloqueioHorarioRepository.findByFuncionaria_Empresa_IdAndDataHoraInicioBeforeAndDataHoraFimAfter(
                    eq(empresa.getId()), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
            mockarExpedientePadrao();
        }

        private Agendamento daAna() {
            Agendamento a = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            a.setFuncionaria(funcionaria);
            return a;
        }

        @Test
        @DisplayName("Sem preferência, o horário fica com a funcionária livre")
        void semPreferenciaAtribuiFuncionariaLivre() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
            mockarAgendamentosNoIntervalo(List.of(daAna()));
            mockarSaveDevolvendoArgumento();
            AgendamentoRequest semPreferencia = new AgendamentoRequest(empresa.getId(), null, servico.getId(),
                    inicioValido, null, null);

            service.criar(cliente.getId(), cliente.getId(), semPreferencia);

            ArgumentCaptor<Agendamento> salvo = ArgumentCaptor.forClass(Agendamento.class);
            verify(agendamentoRepository).save(salvo.capture());
            assertSame(bia, salvo.getValue().getFuncionaria());
        }

        @Test
        @DisplayName("Duas funcionárias atendem no mesmo horário")
        void duasNoMesmoHorario() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
            when(funcionariaRepository.findById(bia.getId())).thenReturn(Optional.of(bia));
            mockarAgendamentosNoIntervalo(List.of(daAna()));
            mockarSaveDevolvendoArgumento();
            AgendamentoRequest comBia = new AgendamentoRequest(empresa.getId(), bia.getId(), servico.getId(),
                    inicioValido, null, null);

            AgendamentoResponse criado = service.criar(cliente.getId(), cliente.getId(), comBia);

            assertEquals(bia.getId(), criado.funcionariaId());
        }

        @Test
        @DisplayName("Dá erro quando a funcionária escolhida já tem atendimento no horário")
        void escolhidaOcupada() {
            mockarEntidadesDaCriacao();
            mockarAgendamentosNoIntervalo(List.of(daAna()));
            UUID clienteId = cliente.getId();
            AgendamentoRequest req = request(inicioValido);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, req));
            assertEquals("A funcionária não está livre neste horário", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Reagendar mantém a funcionária e confere a agenda dela")
        void reagendarMantemFuncionaria() {
            Agendamento agendamento = daAna();
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            Agendamento outroDaAna = daAna();
            outroDaAna.setDataHoraInicio(inicioValido.plusHours(3));
            outroDaAna.setDataHoraFim(inicioValido.plusHours(3).plusMinutes(DURACAO_SERVICO));
            mockarAgendamentosNoIntervalo(List.of(outroDaAna));
            UUID id = agendamento.getId();
            UUID empresaId = empresa.getId();
            ReagendamentoRequest req = new ReagendamentoRequest(inicioValido.plusHours(3), null);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPelaEmpresa(id, empresaId, req));
            assertEquals("A funcionária não está livre neste horário", ex.getMessage());
        }

        @Test
        @DisplayName("Reagendar com a funcionária desativada passa o horário para outra livre")
        void reagendarComFuncionariaDesativadaEscolheOutra() {
            Agendamento agendamento = daAna();
            funcionaria.setAtivo(false);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            // A Ana tem outro atendimento no dia: se ela continuasse escolhida, ficaria com o horário mesmo assim,
            // porque às 14h está livre. Só a regra da desativada faz a Bia (menos ocupada) assumir.
            Agendamento outroDaAna = daAna();
            mockarAgendamentosNoIntervalo(List.of(outroDaAna));
            mockarSaveDevolvendoArgumento();
            ReagendamentoRequest req = new ReagendamentoRequest(inicioValido.withHour(14), null);

            AgendamentoResponse resposta = service.reagendarPelaEmpresa(agendamento.getId(), empresa.getId(), req);

            assertSame(bia, agendamento.getFuncionaria());
            assertEquals(bia.getId(), resposta.funcionariaId());
        }

        @Test
        @DisplayName("Sem preferência e com todas as funcionárias ocupadas, avisa que o estabelecimento está cheio")
        void semPreferenciaTodasOcupadas() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
            Agendamento daBia = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            daBia.setFuncionaria(bia);
            mockarAgendamentosNoIntervalo(List.of(daAna(), daBia));
            UUID clienteId = cliente.getId();
            AgendamentoRequest semPreferencia = new AgendamentoRequest(empresa.getId(), null, servico.getId(),
                    inicioValido, null, null);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, clienteId, semPreferencia));
            assertEquals("Já existe um agendamento neste horário para este estabelecimento", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Cancelar os futuros de uma conta excluída")
    class CancelarFuturosDaContaExcluida {

        private static final List<StatusAgendamento> AINDA_VAO_ACONTECER = List.of(
                StatusAgendamento.PENDENTE, StatusAgendamento.CONFIRMADO, StatusAgendamento.REAGENDADO);

        @Test
        @DisplayName("Cliente: cancela os futuros, mesmo em cima da hora, sem avisar a cliente que saiu")
        void cliente() {
            Agendamento futuro = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            when(agendamentoRepository.findByClienteIdAndStatusInAndDataHoraInicioAfter(
                    eq(cliente.getId()), eq(AINDA_VAO_ACONTECER), any(LocalDateTime.class)))
                    .thenReturn(List.of(futuro));
            when(agendamentoRepository.save(futuro)).thenReturn(futuro);

            service.cancelarFuturosDaContaExcluida(cliente);

            assertEquals(StatusAgendamento.CANCELADO, futuro.getStatus());
            ArgumentCaptor<ApplicationEvent> evento = ArgumentCaptor.forClass(ApplicationEvent.class);
            verify(eventPublisher).publishEvent(evento.capture());
            assertInstanceOf(AgendamentoCanceladoEvent.class, evento.getValue());
            verify(eventPublisher, never()).publishEvent(any(AvisoAgendamentoEvent.class));
            verifyNoInteractions(cancelamentoClienteStrategy);
        }

        @Test
        @DisplayName("Estúdio: cancela os futuros e avisa cada cliente")
        void estudio() {
            Agendamento futuro = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findByEmpresaIdAndStatusInAndDataHoraInicioAfter(
                    eq(empresa.getId()), eq(AINDA_VAO_ACONTECER), any(LocalDateTime.class)))
                    .thenReturn(List.of(futuro));
            when(agendamentoRepository.save(futuro)).thenReturn(futuro);

            service.cancelarFuturosDaContaExcluida(empresa);

            assertEquals(StatusAgendamento.CANCELADO, futuro.getStatus());
            ArgumentCaptor<AvisoAgendamentoEvent> aviso = ArgumentCaptor.forClass(AvisoAgendamentoEvent.class);
            verify(eventPublisher).publishEvent(aviso.capture());
            assertEquals(TipoAvisoAgendamento.CANCELADO_PELO_ESTUDIO, aviso.getValue().tipo());
            assertEquals(futuro.getId(), aviso.getValue().agendamento().id());
            verifyNoInteractions(cancelamentoEmpresaStrategy);
        }
    }
}
