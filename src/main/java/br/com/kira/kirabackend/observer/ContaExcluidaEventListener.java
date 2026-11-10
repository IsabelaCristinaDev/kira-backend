package br.com.kira.kirabackend.observer;

import br.com.kira.kirabackend.service.ImagensEnviadas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ContaExcluidaEventListener {

    private final ImagensEnviadas imagens;

    // Só depois do commit: se a exclusão falhar, as fotos continuam no lugar
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContaExcluida(ContaExcluidaEvent event) {
        event.arquivos().forEach(imagens::apagarSemFalhar);
    }
}
