package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.DisponibilidadeEstudio;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.request.DisponibilidadeRequest;
import br.com.kira.kirabackend.dto.response.DisponibilidadeResponse;
import br.com.kira.kirabackend.dto.response.SlotDisponivel;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.DisponibilidadeEstudioRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DisponibilidadeServiceTest {

    /** 19/10/2026 é uma segunda-feira. O serviço não valida datas passadas, então uma data fixa é segura. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 19);

    @Mock
    private DisponibilidadeEstudioRepository disponibilidadeRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private DisponibilidadeService service;

    private Empresa empresa;
    private Servico servico;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());

        servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setDuracaoMinutos(60);
        servico.setEmpresa(empresa);

        // Nos testes, a empresa é ativa por padrão; os casos de empresa desativada sobrescrevem
        lenient().when(usuarioRepository.existsEmpresaAtivaById(empresa.getId())).thenReturn(true);
    }

    // ---------- helpers ----------

    private void mockarServico(Integer duracaoMinutos) {
        servico.setDuracaoMinutos(duracaoMinutos);
        when(servicoRepository.findById(servico.getId())).thenReturn(Optional.of(servico));
    }

    private void mockarExpediente(DiaSemana dia, LocalTime abertura, LocalTime fechamento) {
        DisponibilidadeEstudio disponibilidade = new DisponibilidadeEstudio();
        disponibilidade.setEmpresa(empresa);
        disponibilidade.setDiaSemana(dia);
        disponibilidade.setHoraInicio(abertura);
        disponibilidade.setHoraFim(fechamento);
        when(disponibilidadeRepository.findByEmpresaIdAndDiaSemana(empresa.getId(), dia))
                .thenReturn(Optional.of(disponibilidade));
    }

    private void mockarAgendamentos(Agendamento... agendamentos) {
        when(agendamentoRepository.findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
                eq(empresa.getId()), eq(StatusAgendamento.CANCELADO),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(agendamentos));
    }

    private Agendamento agendamento(LocalTime inicio, LocalTime fim) {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setDataHoraInicio(LocalDateTime.of(SEGUNDA, inicio));
        agendamento.setDataHoraFim(LocalDateTime.of(SEGUNDA, fim));
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        return agendamento;
    }

    private List<SlotDisponivel> calcular() {
        return service.calcularSlotsDisponiveis(empresa.getId(), servico.getId(), SEGUNDA);
    }

    private static List<LocalTime> horariosDeInicio(List<SlotDisponivel> slots) {
        return slots.stream().map(s -> s.dataHoraInicio().toLocalTime()).toList();
    }

    private static LocalTime hora(int h, int m) {
        return LocalTime.of(h, m);
    }

    // ---------- calcularSlotsDisponiveis ----------

    @Nested
    @DisplayName("calcularSlotsDisponiveis")
    class CalcularSlots {

        @Test
        @DisplayName("Das 9h às 12h com serviço de 60 min monta 3 horários")
        void deveMontarHorariosDoExpediente() {
            mockarServico(60);
            mockarExpediente(DiaSemana.SEGUNDA, hora(9, 0), hora(12, 0));
            mockarAgendamentos();

            List<SlotDisponivel> slots = calcular();

            assertEquals(List.of(
                    new SlotDisponivel(SEGUNDA.atTime(9, 0), SEGUNDA.atTime(10, 0)),
                    new SlotDisponivel(SEGUNDA.atTime(10, 0), SEGUNDA.atTime(11, 0)),
                    new SlotDisponivel(SEGUNDA.atTime(11, 0), SEGUNDA.atTime(12, 0))
            ), slots);
        }

        @Test
        @DisplayName("Esconde os horários ocupados por agendamentos")
        void deveEsconderHorariosOcupados() {
            mockarServico(60);
            mockarExpediente(DiaSemana.SEGUNDA, hora(9, 0), hora(12, 0));
            mockarAgendamentos(agendamento(hora(10, 0), hora(11, 0)));

            assertEquals(List.of(hora(9, 0), hora(11, 0)), horariosDeInicio(calcular()));
        }

        @Test
        @DisplayName("Agendamento que pega parte de dois horários bloqueia os dois")
        void deveBloquearHorariosParcialmenteOcupados() {
            mockarServico(60);
            mockarExpediente(DiaSemana.SEGUNDA, hora(9, 0), hora(12, 0));
            mockarAgendamentos(agendamento(hora(9, 30), hora(10, 30)));

            assertEquals(List.of(hora(11, 0)), horariosDeInicio(calcular()));
        }

        @Test
        @DisplayName("Busca os agendamentos do dia excluindo os cancelados, que liberam o horário")
        void deveIgnorarAgendamentosCancelados() {
            mockarServico(60);
            mockarExpediente(DiaSemana.SEGUNDA, hora(9, 0), hora(12, 0));
            // O repositório já filtra os cancelados: o agendamento cancelado das 10h não volta na consulta.
            mockarAgendamentos();

            assertEquals(List.of(hora(9, 0), hora(10, 0), hora(11, 0)), horariosDeInicio(calcular()));

            verify(agendamentoRepository).findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
                    empresa.getId(),
                    StatusAgendamento.CANCELADO,
                    SEGUNDA.plusDays(1).atStartOfDay(),
                    SEGUNDA.atStartOfDay());
        }

        @Test
        @DisplayName("Agendamento que encosta no limite não bloqueia o horário vizinho")
        void naoDeveBloquearHorarioVizinho() {
            mockarServico(60);
            mockarExpediente(DiaSemana.SEGUNDA, hora(9, 0), hora(12, 0));
            // Termina exatamente às 10h e outro começa exatamente às 11h.
            mockarAgendamentos(
                    agendamento(hora(9, 0), hora(10, 0)),
                    agendamento(hora(11, 0), hora(12, 0)));

            assertEquals(List.of(hora(10, 0)), horariosDeInicio(calcular()));
        }

        @Test
        @DisplayName("O último horário não passa do fim do expediente")
        void ultimoHorarioNaoPassaDoExpediente() {
            mockarServico(50);
            mockarExpediente(DiaSemana.SEGUNDA, hora(9, 0), hora(12, 0));
            mockarAgendamentos();

            List<SlotDisponivel> slots = calcular();

            // 9h00, 9h50, 10h40 (termina 11h30). Um 4º começaria 11h30 e terminaria 12h20.
            assertEquals(List.of(hora(9, 0), hora(9, 50), hora(10, 40)), horariosDeInicio(slots));
            assertTrue(slots.stream().noneMatch(s -> s.dataHoraFim().isAfter(SEGUNDA.atTime(12, 0))));
        }

        @Test
        @DisplayName("Expediente que termina perto da meia-noite não entra em loop")
        void expedienteAteQuaseMeiaNoiteNaoTrava() {
            mockarServico(60);
            mockarExpediente(DiaSemana.SEGUNDA, hora(22, 0), hora(23, 59));
            mockarAgendamentos();

            List<SlotDisponivel> slots = assertTimeoutPreemptively(Duration.ofSeconds(2),
                    DisponibilidadeServiceTest.this::calcular);

            assertEquals(List.of(hora(22, 0)), horariosDeInicio(slots));
        }

        @Test
        @DisplayName("Dia sem expediente cadastrado dá erro")
        void diaSemExpedienteDaErro() {
            mockarServico(60);
            when(disponibilidadeRepository.findByEmpresaIdAndDiaSemana(empresa.getId(), DiaSemana.SEGUNDA))
                    .thenReturn(Optional.empty());

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    DisponibilidadeServiceTest.this::calcular);
            assertEquals("Empresa não atende neste dia da semana", ex.getMessage());
        }

        @Test
        @DisplayName("Serviço inexistente dá 404")
        void servicoInexistenteDa404() {
            when(servicoRepository.findById(servico.getId())).thenReturn(Optional.empty());

            assertThrows(RecursoNaoEncontradoException.class, DisponibilidadeServiceTest.this::calcular);
        }

        @ParameterizedTest(name = "{0} é {1}")
        @CsvSource({
                "2026-10-18, DOMINGO",
                "2026-10-19, SEGUNDA",
                "2026-10-20, TERCA",
                "2026-10-21, QUARTA",
                "2026-10-22, QUINTA",
                "2026-10-23, SEXTA",
                "2026-10-24, SABADO"
        })
        @DisplayName("Mapeia a data para o dia da semana certo, inclusive o domingo")
        void deveMapearDiaDaSemana(LocalDate data, DiaSemana esperado) {
            mockarServico(60);
            when(disponibilidadeRepository.findByEmpresaIdAndDiaSemana(eq(empresa.getId()), any()))
                    .thenReturn(Optional.empty());

            UUID empresaId = empresa.getId();
            UUID servicoId = servico.getId();

            assertThrows(RegraDeNegocioException.class,
                    () -> service.calcularSlotsDisponiveis(empresaId, servicoId, data));

            ArgumentCaptor<DiaSemana> dia = ArgumentCaptor.forClass(DiaSemana.class);
            verify(disponibilidadeRepository).findByEmpresaIdAndDiaSemana(eq(empresa.getId()), dia.capture());
            assertEquals(esperado, dia.getValue());
        }

        @ParameterizedTest(name = "duração = {0}")
        @NullSource
        @ValueSource(ints = {0, -30})
        @DisplayName("Serviço com duração nula, zero ou negativa dá erro em vez de travar")
        void duracaoInvalidaNaoTrava(Integer duracao) {
            mockarServico(duracao);
            mockarExpediente(DiaSemana.SEGUNDA, hora(9, 0), hora(12, 0));
            mockarAgendamentos();

            RegraDeNegocioException ex = assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThrows(RegraDeNegocioException.class, DisponibilidadeServiceTest.this::calcular));
            assertEquals("Serviço com duração inválida", ex.getMessage());
        }
    }

    // ---------- cadastrar ----------

    @Nested
    @DisplayName("cadastrar")
    class Cadastrar {

        @Test
        @DisplayName("Cadastra o expediente com os dados enviados")
        void deveCadastrarExpediente() {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(disponibilidadeRepository.save(any(DisponibilidadeEstudio.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DisponibilidadeResponse response = service.cadastrar(empresa.getId(),
                    new DisponibilidadeRequest(DiaSemana.TERCA, hora(9, 0), hora(18, 0)));

            assertEquals(DiaSemana.TERCA, response.diaSemana());
            assertEquals(hora(9, 0), response.horaInicio());
            assertEquals(hora(18, 0), response.horaFim());

            ArgumentCaptor<DisponibilidadeEstudio> captor = ArgumentCaptor.forClass(DisponibilidadeEstudio.class);
            verify(disponibilidadeRepository).save(captor.capture());
            assertEquals(empresa, captor.getValue().getEmpresa());
        }

        @ParameterizedTest(name = "início {0}, fim {1}")
        @CsvSource({
                "18:00, 09:00",
                "09:00, 09:00"
        })
        @DisplayName("Fim antes ou igual ao início dá erro")
        void fimAntesDoInicioDaErro(LocalTime inicio, LocalTime fim) {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));

            UUID empresaId = empresa.getId();
            DisponibilidadeRequest req = new DisponibilidadeRequest(DiaSemana.SEGUNDA, inicio, fim);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.cadastrar(empresaId, req));
            assertEquals("Hora de fim deve ser após hora de início", ex.getMessage());
            verify(disponibilidadeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Empresa inexistente dá 404")
        void empresaInexistenteDa404() {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.empty());

            UUID empresaId = empresa.getId();
            DisponibilidadeRequest req = new DisponibilidadeRequest(DiaSemana.SEGUNDA, hora(9, 0), hora(18, 0));

            assertThrows(RecursoNaoEncontradoException.class,
                    () -> service.cadastrar(empresaId, req));
            verify(disponibilidadeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Dia que já tem expediente é atualizado, sem criar outro registro")
        void diaExistenteEhAtualizado() {
            DisponibilidadeEstudio existente = new DisponibilidadeEstudio();
            existente.setId(UUID.randomUUID());
            existente.setEmpresa(empresa);
            existente.setDiaSemana(DiaSemana.TERCA);
            existente.setHoraInicio(hora(8, 0));
            existente.setHoraFim(hora(12, 0));
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(disponibilidadeRepository.findByEmpresaIdAndDiaSemana(empresa.getId(), DiaSemana.TERCA))
                    .thenReturn(Optional.of(existente));
            when(disponibilidadeRepository.save(existente)).thenReturn(existente);

            DisponibilidadeResponse response = service.cadastrar(empresa.getId(),
                    new DisponibilidadeRequest(DiaSemana.TERCA, hora(9, 0), hora(18, 0)));

            assertEquals(existente.getId(), response.id());
            assertEquals(hora(9, 0), response.horaInicio());
            assertEquals(hora(18, 0), response.horaFim());
        }
    }

    // ---------- substituirSemana ----------

    @Nested
    @DisplayName("substituirSemana")
    class SubstituirSemana {

        private List<DisponibilidadeResponse> substituir(DisponibilidadeRequest... dias) {
            return service.substituirSemana(empresa.getId(), empresa.getId(), List.of(dias));
        }

        @Test
        @DisplayName("Apaga a semana antiga e grava os dias enviados, em ordem de dia")
        void deveTrocarASemana() {
            DisponibilidadeEstudio antiga = new DisponibilidadeEstudio();
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(disponibilidadeRepository.findByEmpresaId(empresa.getId())).thenReturn(List.of(antiga));
            when(disponibilidadeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            List<DisponibilidadeResponse> semana = substituir(
                    new DisponibilidadeRequest(DiaSemana.SEXTA, hora(9, 0), hora(18, 0)),
                    new DisponibilidadeRequest(DiaSemana.SEGUNDA, hora(10, 0), hora(16, 0)));

            assertEquals(List.of(DiaSemana.SEGUNDA, DiaSemana.SEXTA),
                    semana.stream().map(DisponibilidadeResponse::diaSemana).toList());
            var ordem = inOrder(disponibilidadeRepository);
            ordem.verify(disponibilidadeRepository).deleteAll(List.of(antiga));
            ordem.verify(disponibilidadeRepository).flush();
            ordem.verify(disponibilidadeRepository).saveAll(any());
        }

        @Test
        @DisplayName("Lista vazia fecha todos os dias")
        void listaVaziaFechaTudo() {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(disponibilidadeRepository.findByEmpresaId(empresa.getId())).thenReturn(List.of());
            when(disponibilidadeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertTrue(substituir().isEmpty());
            verify(disponibilidadeRepository).deleteAll(List.of());
        }

        @Test
        @DisplayName("Outro usuário não pode alterar o horário da empresa")
        void outroUsuarioNaoPodeAlterar() {
            UUID outroUsuario = UUID.randomUUID();
            List<DisponibilidadeRequest> dias = List.of();

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.substituirSemana(empresa.getId(), outroUsuario, dias));
            assertEquals("Você só pode alterar o horário do seu estabelecimento", ex.getMessage());
            verify(disponibilidadeRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Dia repetido dá erro e não apaga nada")
        void diaRepetidoDaErro() {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            var segunda = new DisponibilidadeRequest(DiaSemana.SEGUNDA, hora(9, 0), hora(12, 0));
            var segundaTarde = new DisponibilidadeRequest(DiaSemana.SEGUNDA, hora(14, 0), hora(18, 0));

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> substituir(segunda, segundaTarde));
            assertEquals("Dia da semana repetido: SEGUNDA", ex.getMessage());
            verify(disponibilidadeRepository, never()).deleteAll(any());
        }

        @Test
        @DisplayName("Fim antes do início dá erro e não apaga nada")
        void fimAntesDoInicioDaErro() {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            var invertido = new DisponibilidadeRequest(DiaSemana.SEGUNDA, hora(18, 0), hora(9, 0));

            assertThrows(RegraDeNegocioException.class, () -> substituir(invertido));
            verify(disponibilidadeRepository, never()).deleteAll(any());
        }
    }

    @Test
    @DisplayName("Expediente e horários livres de empresa desativada dão 404")
    void empresaDesativadaDa404() {
        UUID empresaId = empresa.getId();
        UUID servicoId = servico.getId();
        when(usuarioRepository.existsEmpresaAtivaById(empresaId)).thenReturn(false);

        assertThrows(RecursoNaoEncontradoException.class, () -> service.listarPorEmpresa(empresaId));
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.calcularSlotsDisponiveis(empresaId, servicoId, SEGUNDA));
        verifyNoInteractions(disponibilidadeRepository, servicoRepository, agendamentoRepository);
    }
}
