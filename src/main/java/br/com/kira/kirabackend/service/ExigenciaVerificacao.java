package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoObrigatoriaException;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Quando a identidade verificada é exigida (VERIFICACAO_OBRIGATORIA=true): cliente sem verificação não agenda, e
 * estúdio sem verificação sai da vitrine e não recebe agendamento. Desligada, que é o padrão, nada muda.
 *
 * Verificada é quem tem uma tentativa VERIFIED: depois dela o backend não aceita outro envio.
 */
@Service
public class ExigenciaVerificacao {

    private static final Logger log = LoggerFactory.getLogger(ExigenciaVerificacao.class);

    private final VerificacaoIdentidadeRepository repository;
    private final boolean obrigatoria;

    public ExigenciaVerificacao(
            VerificacaoIdentidadeRepository repository,
            @Value("${kira.verificacao.obrigatoria:false}") boolean obrigatoria) {
        this.repository = repository;
        this.obrigatoria = obrigatoria;
    }

    public boolean obrigatoria() {
        return obrigatoria;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void avisarSeDesligada() {
        if (!obrigatoria) {
            log.warn("Verificação de identidade desligada (VERIFICACAO_OBRIGATORIA=false): contas sem verificação "
                    + "agendam e aparecem na vitrine");
        }
    }

    public void exigirClienteVerificada(UUID clienteId) {
        if (!verificada(clienteId)) {
            throw new VerificacaoObrigatoriaException("Verifique sua identidade para agendar");
        }
    }

    // A cliente não tem o que fazer aqui: é regra de negócio, não o 403 que pede a verificação dela
    public void exigirEstudioVerificado(UUID empresaId) {
        if (!verificada(empresaId)) {
            throw new RegraDeNegocioException("Este estúdio ainda não pode receber agendamentos");
        }
    }

    /** Para filtrar listas de estúdios: uma consulta só, e nenhuma com a exigência desligada. */
    public Predicate<UUID> naVitrine() {
        if (!obrigatoria) {
            return id -> true;
        }
        Set<UUID> verificados = repository.idsDeUsuariosComStatus(StatusVerificacao.VERIFIED);
        return verificados::contains;
    }

    private boolean verificada(UUID usuarioId) {
        return !obrigatoria || repository.existsByUsuarioIdAndStatus(usuarioId, StatusVerificacao.VERIFIED);
    }
}
