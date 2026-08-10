package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

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
}