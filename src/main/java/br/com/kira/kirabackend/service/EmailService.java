package br.com.kira.kirabackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void enviarEmailRecuperacaoSenha(String destinatario, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destinatario);
        message.setSubject("KIRA — Recuperação de Senha");
        message.setText(
                "Olá!\n\n" +
                        "Recebemos uma solicitação para redefinir a senha da sua conta KIRA.\n\n" +
                        "Use o token abaixo para redefinir sua senha:\n\n" +
                        token + "\n\n" +
                        "Este token expira em 30 minutos.\n\n" +
                        "Se você não solicitou a recuperação de senha, ignore este email.\n\n" +
                        "Equipe KIRA"
        );
        mailSender.send(message);
    }
}