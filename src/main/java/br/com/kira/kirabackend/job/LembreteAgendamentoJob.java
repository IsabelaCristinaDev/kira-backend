package br.com.kira.kirabackend.job;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.service.EmailService;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class LembreteAgendamentoJob {

    private static final Logger log = LoggerFactory.getLogger(LembreteAgendamentoJob.class);

    private static final List<StatusAgendamento> STATUS_SEM_LEMBRETE = List.of(
            StatusAgendamento.CANCELADO,
            StatusAgendamento.CONCLUIDO
    );

    private final AgendamentoRepository agendamentoRepository;
    private final EmailService emailService;

    @Value("${kira.lembrete.antecedencia-horas:24}")
    private int antecedenciaHoras;

    public LembreteAgendamentoJob(AgendamentoRepository agendamentoRepository, EmailService emailService) {
        this.agendamentoRepository = agendamentoRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${kira.lembrete.cron:0 0 * * * *}")
    @Transactional
    public void enviarLembretes() {
        LocalDateTime agora = LocalDateTime.now(KiraTimeZone.DEFAULT);
        LocalDateTime limite = agora.plusHours(antecedenciaHoras);

        List<Agendamento> agendamentos = agendamentoRepository
                .findByStatusNotInAndLembreteEnviadoFalseAndDataHoraInicioBetween(
                        STATUS_SEM_LEMBRETE, agora, limite);

        if (agendamentos.isEmpty()) {
            return;
        }

        log.info("Enviando {} lembrete(s) de agendamento", agendamentos.size());

        for (Agendamento agendamento : agendamentos) {
            try {
                emailService.enviarLembreteAgendamento(agendamento.getCliente().getEmail(), agendamento);
                agendamento.setLembreteEnviado(true);
                agendamentoRepository.save(agendamento);
            } catch (Exception e) {
                log.error("Falha ao enviar lembrete do agendamento {}: {}",
                        agendamento.getId(), e.getMessage());
            }
        }
    }
}