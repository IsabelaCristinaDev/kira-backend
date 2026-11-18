package br.com.kira.kirabackend.job;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LembreteAgendamentoJobTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private EmailService emailService;

    private LembreteAgendamentoJob job;

    @BeforeEach
    void setUp() {
        job = new LembreteAgendamentoJob(agendamentoRepository, emailService);
        ReflectionTestUtils.setField(job, "antecedenciaHoras", 24);
    }

    private Agendamento novoAgendamento(String email) {
        Cliente cliente = new Cliente();
        cliente.setEmail(email);

        Agendamento agendamento = new Agendamento();
        agendamento.setCliente(cliente);
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        agendamento.setLembreteEnviado(false);
        return agendamento;
    }

    @Test
    @DisplayName("Deve enviar lembrete e marcar como enviado para cada agendamento elegível")
    void deveEnviarLembretesParaAgendamentosElegiveis() {
        Agendamento agendamento1 = novoAgendamento("cliente1@teste.com");
        Agendamento agendamento2 = novoAgendamento("cliente2@teste.com");

        when(agendamentoRepository.findByStatusNotInAndLembreteEnviadoFalseAndDataHoraInicioBetween(
                anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(agendamento1, agendamento2));

        job.enviarLembretes();

        verify(emailService).enviarLembreteAgendamento("cliente1@teste.com", agendamento1);
        verify(emailService).enviarLembreteAgendamento("cliente2@teste.com", agendamento2);

        ArgumentCaptor<Agendamento> captor = ArgumentCaptor.forClass(Agendamento.class);
        verify(agendamentoRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(Agendamento::getLembreteEnviado));
    }

    @Test
    @DisplayName("Não deve consultar e-mail nem salvar quando não há agendamentos elegíveis")
    void naoDeveFazerNadaQuandoListaVazia() {
        when(agendamentoRepository.findByStatusNotInAndLembreteEnviadoFalseAndDataHoraInicioBetween(
                anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        job.enviarLembretes();

        verifyNoInteractions(emailService);
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("O log da falha de envio mascara o e-mail que vem na mensagem do SMTP")
    void logDaFalhaMascaraEmail(CapturedOutput saida) {
        Agendamento comFalha = novoAgendamento("falha.lembrete@teste.com");
        when(agendamentoRepository.findByStatusNotInAndLembreteEnviadoFalseAndDataHoraInicioBetween(
                anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(comFalha));
        doThrow(new MailSendException("550 <falha.lembrete@teste.com> recipient rejected"))
                .when(emailService).enviarLembreteAgendamento(eq("falha.lembrete@teste.com"), any());

        job.enviarLembretes();

        assertTrue(saida.getOut().contains("fa***@teste.com"), saida.getOut());
        assertFalse(saida.getOut().contains("falha.lembrete@teste.com"), saida.getOut());
    }

    @Test
    @DisplayName("Deve continuar processando os demais agendamentos quando o envio de um falha")
    void deveContinuarProcessandoAposFalhaDeEnvio() {
        Agendamento comFalha = novoAgendamento("falha@teste.com");
        Agendamento comSucesso = novoAgendamento("sucesso@teste.com");

        when(agendamentoRepository.findByStatusNotInAndLembreteEnviadoFalseAndDataHoraInicioBetween(
                anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(comFalha, comSucesso));

        doThrow(new RuntimeException("Falha no envio"))
                .when(emailService).enviarLembreteAgendamento(eq("falha@teste.com"), any());

        job.enviarLembretes();

        verify(emailService).enviarLembreteAgendamento("falha@teste.com", comFalha);
        verify(emailService).enviarLembreteAgendamento("sucesso@teste.com", comSucesso);

        verify(agendamentoRepository, never()).save(comFalha);
        verify(agendamentoRepository).save(comSucesso);
        assertEquals(Boolean.FALSE, comFalha.getLembreteEnviado());
        assertEquals(Boolean.TRUE, comSucesso.getLembreteEnviado());
    }
}