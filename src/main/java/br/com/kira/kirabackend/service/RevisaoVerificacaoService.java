package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.entity.VerificacaoIdentidade;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.dto.response.VerificacaoEmAnaliseResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Revisão manual, pelo admin, das verificações que o serviço deixou em análise (IN_REVIEW).
 * Aprovar deixa o perfil verificado; reprovar conta como tentativa usada, como uma reprovação do serviço.
 */
@Service
@RequiredArgsConstructor
public class RevisaoVerificacaoService {

    private static final Logger log = LoggerFactory.getLogger(RevisaoVerificacaoService.class);

    private final VerificacaoIdentidadeRepository repository;

    @Transactional(readOnly = true)
    public List<VerificacaoEmAnaliseResponse> listarEmAnalise() {
        return repository.findByStatusOrderByDataCriacaoAsc(StatusVerificacao.IN_REVIEW).stream()
                .map(v -> new VerificacaoEmAnaliseResponse(v.getId(), v.getUsuario().getId(), v.getUsuario().getNome(),
                        v.getTipoUsuario(), v.getTipoDocumento(), List.copyOf(v.getMotivos()), v.getSimilaridade(),
                        v.getConfiancaOcr(), v.getRequestId(), v.getDataCriacao()))
                .toList();
    }

    @Transactional
    public void aprovar(UUID verificacaoId, Usuario admin) {
        decidir(verificacaoId, admin, StatusVerificacao.VERIFIED);
    }

    @Transactional
    public void reprovar(UUID verificacaoId, Usuario admin) {
        decidir(verificacaoId, admin, StatusVerificacao.REJECTED);
    }

    private void decidir(UUID verificacaoId, Usuario admin, StatusVerificacao decisao) {
        VerificacaoIdentidade verificacao = repository.travarParaRevisao(verificacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Verificação não encontrada"));
        if (verificacao.getStatus() != StatusVerificacao.IN_REVIEW) {
            throw new RegraDeNegocioException("Esta verificação não está em análise");
        }

        verificacao.setStatus(decisao);
        verificacao.setRevisadoPor(admin);
        verificacao.setRevisadoEm(LocalDateTime.now(KiraTimeZone.DEFAULT));
        repository.save(verificacao);
        // Quem decidiu e quando ficam na própria linha (revisado_por_id e revisado_em)
        log.info("Verificação revisada: {} (admin {})", decisao, admin.getId());
    }
}
