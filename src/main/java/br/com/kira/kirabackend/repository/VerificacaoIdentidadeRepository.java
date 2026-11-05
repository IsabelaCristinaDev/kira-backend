package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.VerificacaoIdentidade;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificacaoIdentidadeRepository extends JpaRepository<VerificacaoIdentidade, UUID> {

    /** A tentativa mais recente da pessoa: é a situação atual da verificação dela. */
    Optional<VerificacaoIdentidade> findFirstByUsuarioIdOrderByDataCriacaoDesc(UUID usuarioId);

    /** Quantas tentativas a pessoa já usou. Chamado com PENDING: tentativa sem resposta do serviço não conta. */
    long countByUsuarioIdAndStatusNot(UUID usuarioId, StatusVerificacao status);
}
