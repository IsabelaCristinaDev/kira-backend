package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.CodigoChegada;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.dto.response.CodigoChegadaResponse;
import br.com.kira.kirabackend.exception.AcessoNegadoException;
import br.com.kira.kirabackend.exception.CodigoChegadaException;
import br.com.kira.kirabackend.exception.CodigoChegadaException.Motivo;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.CodigoChegadaRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChegadaServiceTest {

    private static final int VALIDADE_MINUTOS = 15;
    private static final int ANTECEDENCIA_HORAS = 2;
    private static final int TENTATIVAS_MAXIMAS = 5;

    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private CodigoChegadaRepository codigoRepository;

    private ChegadaService service;
    private Cliente cliente;
    private Empresa empresa;
    private Agendamento agendamento;

    @BeforeEach
    void setUp() {
        service = new ChegadaService(agendamentoRepository, codigoRepository,
                VALIDADE_MINUTOS, ANTECEDENCIA_HORAS, TENTATIVAS_MAXIMAS);

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setNome("Corte");
        servico.setPreco(new BigDecimal("80"));
        servico.setDuracaoMinutos(60);

        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setServico(servico);
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        comecaEm(agora().plusMinutes(30));
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT);
    }

    private void comecaEm(LocalDateTime inicio) {
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(inicio.plusMinutes(60));
    }

    private void agendamentoTravado() {
        when(agendamentoRepository.travarParaChegada(agendamento.getId())).thenReturn(Optional.of(agendamento));
    }

    private CodigoChegada codigoGuardado(String codigo, LocalDateTime expiraEm) {
        CodigoChegada guardado = new CodigoChegada(agendamento);
        guardado.renovar(codigo, expiraEm);
        when(codigoRepository.findByAgendamentoId(agendamento.getId())).thenReturn(Optional.of(guardado));
        return guardado;
    }

    @Nested
    @DisplayName("gerar o código (cliente)")
    class Gerar {

        @Test
        @DisplayName("Sem código guardado, gera um de 6 dígitos que vale pela validade configurada")
        void geraCodigoNovo() {
            agendamentoTravado();
            when(codigoRepository.findByAgendamentoId(agendamento.getId())).thenReturn(Optional.empty());
            when(codigoRepository.save(any(CodigoChegada.class))).thenAnswer(i -> i.getArgument(0));

            CodigoChegadaResponse resposta = service.gerar(agendamento.getId(), cliente.getId());

            assertTrue(resposta.codigo().matches("\\d{6}"), resposta.codigo());
            LocalDateTime expiraEm = resposta.expiraEm().atZoneSameInstant(KiraTimeZone.DEFAULT).toLocalDateTime();
            assertTrue(expiraEm.isAfter(agora().plusMinutes(VALIDADE_MINUTOS - 1L)), expiraEm.toString());
            assertTrue(!expiraEm.isAfter(agora().plusMinutes(VALIDADE_MINUTOS)), expiraEm.toString());
        }

        @Test
        @DisplayName("Com um código ainda válido, devolve o mesmo, sem gerar outro")
        void devolveOCodigoAtivo() {
            agendamentoTravado();
            codigoGuardado("123456", agora().plusMinutes(10));

            CodigoChegadaResponse resposta = service.gerar(agendamento.getId(), cliente.getId());

            assertEquals("123456", resposta.codigo());
            verify(codigoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Com o código vencido, gera outro e zera as tentativas")
        void trocaOCodigoVencido() {
            agendamentoTravado();
            CodigoChegada guardado = codigoGuardado("123456", agora().minusMinutes(1));
            guardado.setTentativasErradas(2);
            when(codigoRepository.save(guardado)).thenReturn(guardado);

            CodigoChegadaResponse resposta = service.gerar(agendamento.getId(), cliente.getId());

            assertTrue(resposta.expiraEm().toLocalDateTime().isAfter(agora()));
            assertEquals(0, guardado.getTentativasErradas());
        }

        @Test
        @DisplayName("Com o código bloqueado por tentativas erradas, gera outro")
        void trocaOCodigoBloqueado() {
            agendamentoTravado();
            CodigoChegada guardado = codigoGuardado("123456", agora().plusMinutes(10));
            guardado.setTentativasErradas(TENTATIVAS_MAXIMAS);
            when(codigoRepository.save(guardado)).thenReturn(guardado);

            service.gerar(agendamento.getId(), cliente.getId());

            assertEquals(0, guardado.getTentativasErradas());
            verify(codigoRepository).save(guardado);
        }

        @Test
        @DisplayName("Outra cliente não gera o código: 403")
        void outraClienteNaoGera() {
            agendamentoTravado();
            UUID id = agendamento.getId();
            UUID outra = UUID.randomUUID();

            assertThrows(AcessoNegadoException.class, () -> service.gerar(id, outra));
            verify(codigoRepository, never()).save(any());
        }

        @Test
        @DisplayName("O estúdio não gera o código da cliente: 403")
        void estudioNaoGera() {
            agendamentoTravado();
            UUID id = agendamento.getId();
            UUID estudio = empresa.getId();

            assertThrows(AcessoNegadoException.class, () -> service.gerar(id, estudio));
        }

        @ParameterizedTest
        @EnumSource(value = StatusAgendamento.class, names = "CONFIRMADO", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Só com o agendamento confirmado")
        void soConfirmado(StatusAgendamento status) {
            agendamento.setStatus(status);
            agendamentoTravado();
            UUID id = agendamento.getId();
            UUID clienteId = cliente.getId();

            RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                    () -> service.gerar(id, clienteId));
            assertEquals("A chegada só pode ser confirmada em um agendamento confirmado", erro.getMessage());
        }

        @Test
        @DisplayName("Antes da antecedência permitida dá erro")
        void cedoDemais() {
            comecaEm(agora().plusHours(ANTECEDENCIA_HORAS).plusMinutes(5));
            agendamentoTravado();
            UUID id = agendamento.getId();
            UUID clienteId = cliente.getId();

            RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                    () -> service.gerar(id, clienteId));
            assertEquals("O código de chegada fica disponível 2 horas antes do horário marcado", erro.getMessage());
        }

        @Test
        @DisplayName("Depois do fim do atendimento dá erro")
        void tardeDemais() {
            comecaEm(agora().minusMinutes(61));
            agendamentoTravado();
            UUID id = agendamento.getId();
            UUID clienteId = cliente.getId();

            RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                    () -> service.gerar(id, clienteId));
            assertEquals("O horário deste atendimento já terminou", erro.getMessage());
        }

        @Test
        @DisplayName("Agendamento inexistente: 404")
        void agendamentoInexistente() {
            UUID id = UUID.randomUUID();
            UUID clienteId = cliente.getId();
            when(agendamentoRepository.travarParaChegada(id)).thenReturn(Optional.empty());

            assertThrows(RecursoNaoEncontradoException.class, () -> service.gerar(id, clienteId));
        }
    }

    @Nested
    @DisplayName("ver o código ativo (cliente)")
    class Ativo {

        @BeforeEach
        void agendamentoExiste() {
            when(agendamentoRepository.findById(agendamento.getId())).thenReturn(Optional.of(agendamento));
        }

        @Test
        @DisplayName("Devolve o código que ainda vale")
        void devolveOCodigo() {
            codigoGuardado("654321", agora().plusMinutes(5));

            assertEquals("654321", service.ativo(agendamento.getId(), cliente.getId()).codigo());
        }

        @Test
        @DisplayName("Sem código, vencido, usado ou bloqueado: 404")
        void semCodigoAtivo() {
            UUID id = agendamento.getId();
            UUID clienteId = cliente.getId();

            when(codigoRepository.findByAgendamentoId(id)).thenReturn(Optional.empty());
            assertThrows(RecursoNaoEncontradoException.class, () -> service.ativo(id, clienteId));

            codigoGuardado("654321", agora().minusSeconds(1));
            assertThrows(RecursoNaoEncontradoException.class, () -> service.ativo(id, clienteId));

            codigoGuardado("654321", agora().plusMinutes(5)).setUsadoEm(agora());
            assertThrows(RecursoNaoEncontradoException.class, () -> service.ativo(id, clienteId));

            codigoGuardado("654321", agora().plusMinutes(5)).setTentativasErradas(TENTATIVAS_MAXIMAS);
            assertThrows(RecursoNaoEncontradoException.class, () -> service.ativo(id, clienteId));
        }

        @Test
        @DisplayName("Agendamento que deixou de estar confirmado (ex.: cancelado) não mostra o código")
        void agendamentoCancelado() {
            agendamento.setStatus(StatusAgendamento.CANCELADO);
            codigoGuardado("654321", agora().plusMinutes(5));
            UUID id = agendamento.getId();
            UUID clienteId = cliente.getId();

            assertThrows(RecursoNaoEncontradoException.class, () -> service.ativo(id, clienteId));
        }

        @Test
        @DisplayName("Outra pessoa não vê o código: 403")
        void outraPessoaNaoVe() {
            UUID id = agendamento.getId();
            UUID estudio = empresa.getId();

            assertThrows(AcessoNegadoException.class, () -> service.ativo(id, estudio));
            verify(codigoRepository, never()).findByAgendamentoId(any());
        }
    }

    @Nested
    @DisplayName("validar o código (estúdio)")
    class Validar {

        @Test
        @DisplayName("Código certo: marca o código como usado e o agendamento como CHEGADA_CONFIRMADA")
        void codigoCerto() {
            agendamentoTravado();
            CodigoChegada guardado = codigoGuardado("123456", agora().plusMinutes(10));
            when(agendamentoRepository.save(agendamento)).thenReturn(agendamento);

            AgendamentoResponse resposta = service.validar(agendamento.getId(), empresa.getId(), "123456");

            assertEquals(StatusAgendamento.CHEGADA_CONFIRMADA, resposta.status());
            assertEquals(StatusAgendamento.CHEGADA_CONFIRMADA, agendamento.getStatus());
            assertNotNull(guardado.getUsadoEm());
            verify(codigoRepository).save(guardado);
        }

        @Test
        @DisplayName("Código errado: TOKEN_INVALIDO e conta uma tentativa")
        void codigoErrado() {
            agendamentoTravado();
            CodigoChegada guardado = codigoGuardado("123456", agora().plusMinutes(10));

            CodigoChegadaException erro = validarComErro("000000");

            assertEquals(Motivo.TOKEN_INVALIDO, erro.getMotivo());
            assertEquals(1, guardado.getTentativasErradas());
            assertNull(guardado.getUsadoEm());
            assertEquals(StatusAgendamento.CONFIRMADO, agendamento.getStatus());
            verify(codigoRepository).save(guardado);
        }

        @Test
        @DisplayName("Na última tentativa errada, o código deixa de valer: TOKEN_EXPIRADO, mesmo com o código certo depois")
        void bloqueiaDepoisDoMaximo() {
            agendamentoTravado();
            CodigoChegada guardado = codigoGuardado("123456", agora().plusMinutes(10));
            guardado.setTentativasErradas(TENTATIVAS_MAXIMAS - 1);

            assertEquals(Motivo.TOKEN_EXPIRADO, validarComErro("000000").getMotivo());
            assertEquals(Motivo.TOKEN_EXPIRADO, validarComErro("123456").getMotivo());
            assertEquals(StatusAgendamento.CONFIRMADO, agendamento.getStatus());
        }

        @Test
        @DisplayName("Código vencido: TOKEN_EXPIRADO, sem contar tentativa")
        void codigoVencido() {
            agendamentoTravado();
            CodigoChegada guardado = codigoGuardado("123456", agora().minusSeconds(1));

            assertEquals(Motivo.TOKEN_EXPIRADO, validarComErro("123456").getMotivo());
            assertEquals(0, guardado.getTentativasErradas());
            verify(codigoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Código já usado: TOKEN_JA_UTILIZADO")
        void codigoUsado() {
            agendamentoTravado();
            codigoGuardado("123456", agora().plusMinutes(10)).setUsadoEm(agora().minusMinutes(1));

            assertEquals(Motivo.TOKEN_JA_UTILIZADO, validarComErro("123456").getMotivo());
        }

        @Test
        @DisplayName("Sem código gerado: TOKEN_INVALIDO")
        void semCodigo() {
            agendamentoTravado();
            when(codigoRepository.findByAgendamentoId(agendamento.getId())).thenReturn(Optional.empty());

            assertEquals(Motivo.TOKEN_INVALIDO, validarComErro("123456").getMotivo());
        }

        @Test
        @DisplayName("Outro estúdio não valida: 403, sem olhar o código")
        void outroEstudio() {
            agendamentoTravado();
            UUID id = agendamento.getId();
            UUID outro = UUID.randomUUID();

            assertThrows(AcessoNegadoException.class, () -> service.validar(id, outro, "123456"));
            verify(codigoRepository, never()).findByAgendamentoId(any());
        }

        @Test
        @DisplayName("A cliente não valida o próprio código: 403")
        void clienteNaoValida() {
            agendamentoTravado();
            UUID id = agendamento.getId();
            UUID clienteId = cliente.getId();

            assertThrows(AcessoNegadoException.class, () -> service.validar(id, clienteId, "123456"));
        }

        @Test
        @DisplayName("Chegada já confirmada (ex.: segundo envio): TOKEN_JA_UTILIZADO")
        void chegadaJaConfirmada() {
            agendamento.setStatus(StatusAgendamento.CHEGADA_CONFIRMADA);
            agendamentoTravado();

            assertEquals(Motivo.TOKEN_JA_UTILIZADO, validarComErro("123456").getMotivo());
            verify(codigoRepository, never()).findByAgendamentoId(any());
        }

        private CodigoChegadaException validarComErro(String digitado) {
            UUID id = agendamento.getId();
            UUID estudio = empresa.getId();
            return assertThrows(CodigoChegadaException.class, () -> service.validar(id, estudio, digitado));
        }
    }

    @Test
    @DisplayName("Códigos gerados variam (não é sempre o mesmo número)")
    void codigosVariam() {
        when(agendamentoRepository.travarParaChegada(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(codigoRepository.findByAgendamentoId(agendamento.getId())).thenReturn(Optional.empty());
        when(codigoRepository.save(any(CodigoChegada.class))).thenAnswer(i -> i.getArgument(0));

        String primeiro = service.gerar(agendamento.getId(), cliente.getId()).codigo();
        boolean variou = false;
        for (int i = 0; i < 20 && !variou; i++) {
            variou = !primeiro.equals(service.gerar(agendamento.getId(), cliente.getId()).codigo());
        }
        assertTrue(variou);
    }
}
