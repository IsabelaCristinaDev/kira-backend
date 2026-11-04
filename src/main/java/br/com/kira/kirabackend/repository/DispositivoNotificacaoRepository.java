package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.DispositivoNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DispositivoNotificacaoRepository extends JpaRepository<DispositivoNotificacao, UUID> {

    /** Numa operação só do banco, para registros simultâneos do mesmo navegador não entrarem em conflito. */
    @Modifying
    @Query(value = """
            INSERT INTO dispositivo_notificacao (id, usuario_id, endpoint, p256dh, auth, data_criacao)
            VALUES (:id, :usuarioId, :endpoint, :p256dh, :auth, :dataCriacao)
            ON CONFLICT (endpoint) DO UPDATE
            SET usuario_id = EXCLUDED.usuario_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth
            """, nativeQuery = true)
    void salvarPeloEndereco(@Param("id") UUID id,
                            @Param("usuarioId") UUID usuarioId,
                            @Param("endpoint") String endpoint,
                            @Param("p256dh") String p256dh,
                            @Param("auth") String auth,
                            @Param("dataCriacao") LocalDateTime dataCriacao);

    List<DispositivoNotificacao> findByUsuarioIdOrderByDataCriacaoAsc(UUID usuarioId);

    List<DispositivoNotificacao> findByUsuarioIdIn(Collection<UUID> usuarioIds);

    void deleteByEndpointAndUsuarioId(String endpoint, UUID usuarioId);
}
