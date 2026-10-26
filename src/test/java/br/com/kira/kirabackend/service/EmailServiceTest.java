package br.com.kira.kirabackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender);
        // Barra no fim de propósito: o link não pode sair com "//"
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.kira.com/");
    }

    @Test
    @DisplayName("E-mail de recuperação traz o link para a tela de nova senha e o código")
    void recuperacaoTrazLinkECodigo() {
        service.enviarEmailRecuperacaoSenha("ana@teste.com", "abc-123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String texto = captor.getValue().getText();
        assertArrayEquals(new String[]{"ana@teste.com"}, captor.getValue().getTo());
        assertTrue(texto.contains("https://app.kira.com/redefinir-senha?token=abc-123"), texto);
        assertTrue(texto.contains("\nabc-123\n"), texto);
    }

    @Test
    @DisplayName("Falha no SMTP não vira erro para quem pediu a recuperação")
    void falhaNoSmtpNaoPropaga() {
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> service.enviarEmailRecuperacaoSenha("ana@teste.com", "abc-123"));
    }
}
