package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final JavaMailSender mailSender;

    // Endereço do app web: o link de redefinição de senha abre a tela de nova senha
    @Value("${kira.frontend-url}")
    private String frontendUrl;

    // Assíncrono e tolerante a falha: a resposta da API é a mesma exista o e-mail ou não,
    // sem esperar o SMTP nem devolver erro quando ele falha.
    @Async
    public void enviarEmailRecuperacaoSenha(String destinatario, String token) {
        String link = frontendUrl.replaceAll("/+$", "") + "/redefinir-senha?token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destinatario);
        message.setSubject("KIRA — Recuperação de Senha");
        message.setText(
                "Olá!\n\n" +
                        "Recebemos uma solicitação para redefinir a senha da sua conta KIRA.\n\n" +
                        "Abra o link abaixo para criar uma nova senha:\n\n" +
                        link + "\n\n" +
                        "Se o link não abrir, use este código na tela de nova senha:\n\n" +
                        token + "\n\n" +
                        "O link e o código expiram em 30 minutos.\n\n" +
                        "Se você não solicitou a recuperação de senha, ignore este email.\n\n" +
                        "Equipe KIRA"
        );
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Falha ao enviar e-mail de recuperação de senha para {}: {}", destinatario, e.getMessage());
        }
    }

    @Retryable(
            retryFor = MailException.class,
            maxAttemptsExpression = "${kira.lembrete.retry.max-tentativas:3}",
            backoff = @Backoff(
                    delayExpression = "${kira.lembrete.retry.delay-ms:2000}",
                    multiplierExpression = "${kira.lembrete.retry.multiplicador:2}"
            )
    )
    public void enviarLembreteAgendamento(String destinatario, Agendamento agendamento) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destinatario);
        message.setSubject("KIRA — Lembrete de Agendamento");
        message.setText(
                "Olá, " + agendamento.getCliente().getNome() + "!\n\n" +
                        "Este é um lembrete do seu agendamento:\n\n" +
                        "Serviço: " + agendamento.getServico().getNome() + "\n" +
                        "Local: " + agendamento.getEmpresa().getNome() + "\n" +
                        "Data e hora: " + agendamento.getDataHoraInicio().format(FORMATO_DATA_HORA) + "\n\n" +
                        "Caso não possa comparecer, cancele ou reagende com antecedência.\n\n" +
                        "Equipe KIRA"
        );
        mailSender.send(message);

    }

    // Assíncrono e tolerante a falha: o cadastro não deve travar nem falhar
    // por indisponibilidade do servidor SMTP.
    @Async
    public void enviarEmailBoasVindas(String destinatario, String nome) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destinatario);
        message.setSubject("Bem-vindo ao KIRA! 🎉");
        message.setText(
                "Olá, " + nome + "!\n\n" +
                        "Seja bem-vindo(a) ao KIRA — sua plataforma de agendamento de beleza!\n\n" +
                        "Agora você pode:\n" +
                        "- Encontrar profissionais perto de você\n" +
                        "- Agendar serviços de beleza com facilidade\n" +
                        "- Avaliar e ser avaliado(a)\n\n" +
                        "Acesse agora e comece a usar!\n\n" +
                        "Equipe KIRA"
        );
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Falha ao enviar e-mail de boas-vindas para {}: {}", destinatario, e.getMessage());
        }
    }
}