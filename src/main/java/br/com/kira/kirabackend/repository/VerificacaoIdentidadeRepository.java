package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.VerificacaoIdentidade;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface VerificacaoIdentidadeRepository extends JpaRepository<VerificacaoIdentidade, UUID> {

    /** A tentativa mais recente da pessoa: é a situação atual da verificação dela. */
    Optional<VerificacaoIdentidade> findFirstByUsuarioIdOrderByDataCriacaoDesc(UUID usuarioId);

    /** Quantas tentativas a pessoa já usou. Chamado com PENDING: tentativa sem resposta do serviço não conta. */
    long countByUsuarioIdAndStatusNot(UUID usuarioId, StatusVerificacao status);

    boolean existsByUsuarioIdAndStatus(UUID usuarioId, StatusVerificacao status);

    @Query("select distinct v.usuario.id from VerificacaoIdentidade v where v.status = :status")
    Set<UUID> idsDeUsuariosComStatus(@Param("status") StatusVerificacao status);

    List<VerificacaoIdentidade> findByUsuarioIdOrderByDataCriacaoDesc(UUID usuarioId);

    void deleteByUsuarioId(UUID usuarioId);

    /** Fila da revisão manual: a mais antiga primeiro, já com a conta para mostrar o nome. */
    @EntityGraph(attributePaths = "usuario")
    List<VerificacaoIdentidade> findByStatusOrderByDataCriacaoAsc(StatusVerificacao status);

    /** Trava a linha até o fim da transação: duas decisões ao mesmo tempo entram uma de cada vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VerificacaoIdentidade v where v.id = :id")
    Optional<VerificacaoIdentidade> travarParaRevisao(@Param("id") UUID id);
}
