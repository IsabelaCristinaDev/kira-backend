package br.com.kira.kirabackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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
    @DisplayName("Sem usuário e senha do e-mail, a subida avisa no log quais variáveis faltam")
    void avisaSemCredenciais(CapturedOutput saida) {
        // Arrange
        ReflectionTestUtils.setField(service, "usuarioEmail", "");
        ReflectionTestUtils.setField(service, "senhaEmail", "");

        // Act
        service.avisarSeEmailNaoConfigurado();

        // Assert
        assertTrue(saida.getOut().contains("MAIL_USERNAME e MAIL_PASSWORD"), saida.getOut());
    }

    @Test
    @DisplayName("Só com o usuário preenchido, a falta da senha também gera o aviso")
    void avisaSemSenha(CapturedOutput saida) {
        ReflectionTestUtils.setField(service, "usuarioEmail", "kira@teste.com");
        ReflectionTestUtils.setField(service, "senhaEmail", "  ");

        service.avisarSeEmailNaoConfigurado();

        assertTrue(saida.getOut().contains("MAIL_USERNAME e MAIL_PASSWORD"), saida.getOut());
    }

    @Test
    @DisplayName("Com usuário e senha preenchidos, não há aviso")
    void semAvisoQuandoConfigurado(CapturedOutput saida) {
        ReflectionTestUtils.setField(service, "usuarioEmail", "kira@teste.com");
        ReflectionTestUtils.setField(service, "senhaEmail", "senha-de-app");

        service.avisarSeEmailNaoConfigurado();

        assertFalse(saida.getOut().contains("MAIL_USERNAME"), saida.getOut());
    }

    @Test
    @DisplayName("Falhas de envio vão para o log com o e-mail mascarado, inclusive o que vem na mensagem do SMTP")
    void logDeFalhaMascaraEmail(CapturedOutput saida) {
        doThrow(new MailSendException("550 <ana.souza@teste.com> recipient rejected"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        service.enviarEmailRecuperacaoSenha("ana.souza@teste.com", "abc-123");
        service.enviarEmailBoasVindas("ana.souza@teste.com", "Ana");
        service.enviarAvisoTentativasSuspeitas("ana.souza@teste.com", "Ana");

        assertTrue(saida.getOut().contains("an***@teste.com"), saida.getOut());
        assertFalse(saida.getOut().contains("ana.souza@teste.com"), saida.getOut());
    }

    @Test
    @DisplayName("Aviso de tentativas suspeitas vai para o dono da conta, sem dados do ataque")
    void avisoTentativasSuspeitas() {
        service.enviarAvisoTentativasSuspeitas("ana@teste.com", "Ana");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertArrayEquals(new String[]{"ana@teste.com"}, captor.getValue().getTo());
        String texto = captor.getValue().getText();
        assertTrue(texto.contains("Olá, Ana"), texto);
        assertTrue(texto.contains("https://app.kira.com\n"), texto);
        assertTrue(texto.contains("Esqueci minha senha"), texto);
    }

    @Test
    @DisplayName("Falha do SMTP no aviso de tentativas não derruba nada")
    void avisoTentativasFalhaSmtp() {
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> service.enviarAvisoTentativasSuspeitas("ana@teste.com", "Ana"));
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

    @ParameterizedTest
    @ValueSource(strings = {"https://app.kira.com", "https://app.kira.com///"})
    @DisplayName("O link sai sem barra duplicada, com ou sem barras no fim do endereço")
    void linkSemBarraDuplicada(String frontendUrl) {
        ReflectionTestUtils.setField(service, "frontendUrl", frontendUrl);

        service.enviarEmailRecuperacaoSenha("ana@teste.com", "abc-123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String texto = captor.getValue().getText();
        assertTrue(texto.contains("https://app.kira.com/redefinir-senha?token=abc-123"), texto);
    }

    @Test
    @DisplayName("Falha no SMTP não vira erro para quem pediu a recuperação")
    void falhaNoSmtpNaoPropaga() {
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> service.enviarEmailRecuperacaoSenha("ana@teste.com", "abc-123"));
    }
}
