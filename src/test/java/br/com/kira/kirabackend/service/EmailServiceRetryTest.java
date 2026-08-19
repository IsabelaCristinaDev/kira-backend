package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.EnableRetry;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = {EmailService.class, EmailServiceRetryTest.RetryTestConfig.class},
        properties = {
                "kira.lembrete.retry.max-tentativas=3",
                "kira.lembrete.retry.delay-ms=1",
                "kira.lembrete.retry.multiplicador=1"
        }
)
class EmailServiceRetryTest {

    @Configuration
    @EnableRetry
    static class RetryTestConfig {
    }

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private EmailService emailService;

    private Agendamento novoAgendamento() {
        Cliente cliente = new Cliente();
        cliente.setNome("Cliente Teste");

        Empresa empresa = new Empresa();
        empresa.setNome("Empresa Teste");

        Servico servico = new Servico();
        servico.setNome("Corte de Cabelo");

        Agendamento agendamento = new Agendamento();
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setServico(servico);
        agendamento.setDataHoraInicio(LocalDateTime.now());
        return agendamento;
    }

    @Test
    @DisplayName("Deve reenviar automaticamente e ter sucesso antes de esgotar as tentativas")
    void deveReenviarAtéObterSucesso() {
        doThrow(new MailSendException("falha temporária"))
                .doThrow(new MailSendException("falha temporária"))
                .doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailService.enviarLembreteAgendamento("cliente@teste.com", novoAgendamento()));

        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Deve esgotar as tentativas e propagar a exceção quando todas as tentativas falham")
    void deveEsgotarTentativasEPropagarExcecao() {
        doThrow(new MailSendException("falha persistente")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class,
                () -> emailService.enviarLembreteAgendamento("cliente@teste.com", novoAgendamento()));

        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }
}