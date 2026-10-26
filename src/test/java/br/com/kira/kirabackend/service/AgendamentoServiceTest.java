package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.DisponibilidadeEstudio;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.dto.request.AgendamentoRequest;
import br.com.kira.kirabackend.dto.request.ReagendamentoRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.observer.AgendamentoCanceladoEvent;
import br.com.kira.kirabackend.observer.AgendamentoCriadoEvent;
import br.com.kira.kirabackend.observer.AtendimentoConcluidoEvent;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
                eventPublisher);

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
        @DisplayName("Deve dar erro quando já existe agendamento sobreposto no estabelecimento")
        void deveRejeitarConflitoDeHorario() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of(agendamentoExistente(StatusAgendamento.PENDENTE)));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido.plusMinutes(30))));

            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Deve consultar conflitos no intervalo [início, fim) ignorando cancelados")
        void deveConsultarConflitoComIntervaloCorreto() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of());
            mockarSaveDevolvendoArgumento();

            service.criar(cliente.getId(), cliente.getId(), request(inicioValido));

            verify(agendamentoRepository).findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
                    empresa.getId(), StatusAgendamento.CANCELADO,
                    inicioValido.plusMinutes(DURACAO_SERVICO), inicioValido);
        }

        @Test
        @DisplayName("Deve dar 404 quando o cliente não existe")
        void deveFalharClienteInexistente() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.empty());

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido)));
            assertEquals("Cliente não encontrado", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar 404 quando a empresa não existe")
        void deveFalharEmpresaInexistente() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.empty());

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido)));
            assertEquals("Empresa não encontrada", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar 404 quando o serviço não existe")
        void deveFalharServicoInexistente() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.empty());

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido)));
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

            RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido)));
            assertEquals("Funcionária não encontrada", ex.getMessage());
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

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido)));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando a data está no passado")
        void deveFalharDataNoPassado() {
            mockarEntidadesDaCriacao();
            LocalDateTime ontem = LocalDateTime.now(KiraTimeZone.DEFAULT).minusDays(1);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(ontem)));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando começa antes da abertura do expediente")
        void deveFalharAntesDoExpediente() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido.withHour(8))));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando termina depois do fechamento do expediente")
        void deveFalharDepoisDoExpediente() {
            mockarEntidadesDaCriacao();
            mockarExpedientePadrao();

            // 17h + 90min = 18h30, após o fechamento às 18h
            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido.withHour(17))));
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve dar erro quando a empresa não atende no dia da semana")
        void deveFalharDiaSemExpediente() {
            mockarEntidadesDaCriacao();
            when(disponibilidadeRepository.findByEmpresaIdAndDiaSemana(empresa.getId(), DiaSemana.SEGUNDA))
                    .thenReturn(Optional.empty());

            assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(cliente.getId(), cliente.getId(), request(inicioValido)));
            verify(agendamentoRepository, never()).save(any());
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

            AgendamentoResponse response = service.concluir(agendamento.getId(), empresa.getId());

            assertEquals(StatusAgendamento.CONCLUIDO, response.status());
            assertEquals(StatusAgendamento.CONCLUIDO, agendamento.getStatus());

            ArgumentCaptor<ApplicationEvent> evento = ArgumentCaptor.forClass(ApplicationEvent.class);
            verify(eventPublisher).publishEvent(evento.capture());
            AtendimentoConcluidoEvent concluido =
                    assertInstanceOf(AtendimentoConcluidoEvent.class, evento.getValue());
            assertSame(agendamento, concluido.getAgendamento());
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = {"CANCELADO", "CONCLUIDO"})
        @DisplayName("Concluir um agendamento cancelado ou já concluído deve dar erro")
        void deveRejeitarConclusaoInvalida(StatusAgendamento status) {
            Agendamento agendamento = agendamentoExistente(status);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            assertThrows(RegraDeNegocioException.class, () -> service.concluir(agendamento.getId(), empresa.getId()));

            assertEquals(status, agendamento.getStatus());
            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Concluir um agendamento inexistente dá 404")
        void deveFalharAgendamentoInexistente() {
            UUID id = UUID.randomUUID();
            when(agendamentoRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(RecursoNaoEncontradoException.class, () -> service.concluir(id, empresa.getId()));
        }

        @Test
        @DisplayName("Outra empresa não pode concluir o atendimento")
        void outraEmpresaNaoPodeConcluir() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.CONFIRMADO);
            UUID agendamentoId = agendamento.getId();
            UUID outraEmpresa = UUID.randomUUID();
            when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.concluir(agendamentoId, outraEmpresa));

            assertEquals("Este atendimento não pertence ao seu estabelecimento", ex.getMessage());
            assertEquals(StatusAgendamento.CONFIRMADO, agendamento.getStatus());
            verify(agendamentoRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
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

            assertEquals(novoInicio, agendamento.getDataHoraInicio());
            assertEquals(novoInicio.plusMinutes(DURACAO_SERVICO), agendamento.getDataHoraFim());
            assertEquals(StatusAgendamento.REAGENDADO, agendamento.getStatus());
            assertFalse(agendamento.getLembreteEnviado());
            assertEquals(StatusAgendamento.REAGENDADO, response.status());
            verify(agendamentoRepository).save(agendamento);
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
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            mockarExpedientePadrao();
            mockarAgendamentosNoIntervalo(List.of(outro));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamento.getId(), cliente.getId(),
                            new ReagendamentoRequest(inicioValido.withHour(14), null)));
            verify(agendamentoRepository, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = {"CANCELADO", "CONCLUIDO"})
        @DisplayName("Reagendar um agendamento cancelado ou concluído dá erro")
        void deveRejeitarReagendamentoInvalido(StatusAgendamento status) {
            Agendamento agendamento = agendamentoExistente(status);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamento.getId(), cliente.getId(),
                            new ReagendamentoRequest(inicioValido.withHour(14), null)));

            assertEquals(status, agendamento.getStatus());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Reagendar para uma data no passado dá erro")
        void deveRejeitarReagendamentoNoPassado() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));

            assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamento.getId(), cliente.getId(),
                            new ReagendamentoRequest(LocalDateTime.now(KiraTimeZone.DEFAULT).minusHours(1), null)));
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
        }

        @Test
        @DisplayName("Quando a estratégia recusa o cancelamento, nada é salvo nem publicado")
        void naoDeveCancelarQuandoEstrategiaRecusa() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
            doThrow(new RegraDeNegocioException("Antecedência mínima"))
                    .when(cancelamentoClienteStrategy).validarCancelamento(agendamento);

            assertThrows(RegraDeNegocioException.class,
                    () -> service.cancelarPeloCliente(agendamento.getId(), cliente.getId()));

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

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
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

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.criar(clienteId, outroUsuario, request));

            assertEquals("Você só pode agendar em seu próprio nome", ex.getMessage());
            verify(agendamentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Não lista os agendamentos de outra cliente nem de outra empresa")
        void naoListaAgendamentosDeOutros() {
            UUID clienteId = cliente.getId();
            UUID empresaId = empresa.getId();

            assertThrows(RegraDeNegocioException.class, () -> service.listarPorCliente(clienteId, outroUsuario));
            assertThrows(RegraDeNegocioException.class, () -> service.listarPorEmpresa(empresaId, outroUsuario));
            assertThrows(RegraDeNegocioException.class, () -> service.historicoCliente(clienteId, outroUsuario));
            verifyNoInteractions(agendamentoRepository);
        }

        @Test
        @DisplayName("Não cancela o agendamento de outra cliente")
        void naoCancelaAgendamentoDeOutraCliente() {
            Agendamento agendamento = agendamentoExistente(StatusAgendamento.PENDENTE);
            UUID agendamentoId = agendamento.getId();
            when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
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

            assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPeloCliente(agendamentoId, outroUsuario, reagendamento));
            assertThrows(RegraDeNegocioException.class,
                    () -> service.reagendarPelaEmpresa(agendamentoId, outroUsuario, reagendamento));
            assertEquals(inicioValido, agendamento.getDataHoraInicio());
            verify(agendamentoRepository, never()).save(any());
        }
    }
}
