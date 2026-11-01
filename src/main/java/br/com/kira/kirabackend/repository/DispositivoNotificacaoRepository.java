package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.DispositivoNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispositivoNotificacaoRepository extends JpaRepository<DispositivoNotificacao, UUID> {

    Optional<DispositivoNotificacao> findByEndpoint(String endpoint);

    List<DispositivoNotificacao> findByUsuarioIdOrderByDataCriacaoAsc(UUID usuarioId);

    List<DispositivoNotificacao> findByUsuarioIdIn(Collection<UUID> usuarioIds);

    void deleteByEndpointAndUsuarioId(String endpoint, UUID usuarioId);
}
