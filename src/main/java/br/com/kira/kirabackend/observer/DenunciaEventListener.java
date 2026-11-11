package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.repository.AdminRepository;
import br.com.kira.kirabackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DenunciaEventListener {

    private static final Logger log = LoggerFactory.getLogger(DenunciaEventListener.class);

    private final AdminRepository adminRepository;
    private final EmailService emailService;

    // Risco imediato não entra na fila comum: a equipe é avisada assim que a denúncia é gravada.
    @Async
    @TransactionalEventListener
    public void onDenunciaRiscoImediato(DenunciaRiscoImediatoEvent event) {
        log.warn("Denúncia de risco imediato registrada: {} - Motivo: {}", event.denunciaId(), event.motivo());
        for (Admin admin : adminRepository.findByAtivoTrue()) {
            try {
                emailService.enviarAlertaRiscoImediato(admin.getEmail(), event);
            } catch (MailException e) {
                log.error("Falha ao alertar admin {} sobre a denúncia {}", admin.getId(), event.denunciaId(), e);
            }
        }
    }

    @Async
    @TransactionalEventListener
    public void onPerfilSuspensoPreventivamente(PerfilSuspensoPreventivamenteEvent event) {
        try {
            emailService.enviarAvisoSuspensaoPreventiva(event.email(), event.nome());
        } catch (MailException e) {
            log.error("Falha ao avisar o perfil suspenso preventivamente", e);
        }
    }
}
