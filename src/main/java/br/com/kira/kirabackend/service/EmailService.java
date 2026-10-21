package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.observer.DenunciaRiscoImediatoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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

    @Retryable(
            retryFor = MailException.class,
            maxAttemptsExpression = "${kira.lembrete.retry.max-tentativas:3}",
            backoff = @Backoff(
                    delayExpression = "${kira.lembrete.retry.delay-ms:2000}",
                    multiplierExpression = "${kira.lembrete.retry.multiplicador:2}"
            )
    )
    public void enviarAlertaRiscoImediato(String destinatario, DenunciaRiscoImediatoEvent denuncia) {
        String telefone = denuncia.telefoneDenunciante() != null ? denuncia.telefoneDenunciante() : "não informado";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destinatario);
        message.setSubject("KIRA — URGENTE: denúncia de risco imediato");
        message.setText(
                "Uma denúncia de risco imediato acabou de ser registrada.\n\n" +
                        "Denúncia: " + denuncia.denunciaId() + "\n" +
                        "Motivo: " + denuncia.motivo() + "\n" +
                        "Registrada em: " + denuncia.dataCriacao().format(FORMATO_DATA_HORA) + "\n" +
                        "Denunciante: " + denuncia.nomeDenunciante() + " (telefone: " + telefone + ")\n" +
                        "Perfil denunciado: " + denuncia.nomeDenunciado() + "\n\n" +
                        (denuncia.perfilSuspenso()
                                ? "O perfil denunciado foi suspenso preventivamente. "
                                : "O perfil denunciado NÃO foi suspenso automaticamente (fora de um atendimento em andamento). ") +
                        "Analise a denúncia no painel administrativo o quanto antes.\n\n" +
                        "Equipe KIRA"
        );
        mailSender.send(message);
    }

    @Retryable(
            retryFor = MailException.class,
            maxAttemptsExpression = "${kira.lembrete.retry.max-tentativas:3}",
            backoff = @Backoff(
                    delayExpression = "${kira.lembrete.retry.delay-ms:2000}",
                    multiplierExpression = "${kira.lembrete.retry.multiplicador:2}"
            )
    )
    public void enviarAvisoSuspensaoPreventiva(String destinatario, String nome) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(destinatario);
        message.setSubject("KIRA — Sua conta foi suspensa preventivamente");
        message.setText(
                "Olá, " + nome + ".\n\n" +
                        "Sua conta KIRA foi suspensa de forma preventiva após um relato de segurança " +
                        "registrado durante um atendimento.\n\n" +
                        "Esta suspensão é temporária e NÃO é uma decisão final: a equipe KIRA está analisando " +
                        "o caso. Se o relato não se confirmar, " +
                        "sua conta será reativada automaticamente.\n\n" +
                        "Enquanto a análise estiver em andamento, não será possível acessar a conta.\n\n" +
                        "Equipe KIRA"
        );
        mailSender.send(message);
    }

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
        mailSender.send(message);
    }
}