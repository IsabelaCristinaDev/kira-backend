package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Favorita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FavoritaRepository extends JpaRepository<Favorita, UUID> {

    /** Numa operação só do banco, para dois toques seguidos no mesmo estúdio não entrarem em conflito. */
    @Modifying
    @Query(value = """
            INSERT INTO favorita (id, cliente_id, empresa_id, data_criacao)
            VALUES (:id, :clienteId, :empresaId, :dataCriacao)
            ON CONFLICT (cliente_id, empresa_id) DO NOTHING
            """, nativeQuery = true)
    void salvarSeNaoExistir(@Param("id") UUID id,
                            @Param("clienteId") UUID clienteId,
                            @Param("empresaId") UUID empresaId,
                            @Param("dataCriacao") LocalDateTime dataCriacao);

    // Estúdio desativado sai da lista, igual à vitrine, e volta se for reativado
    @Query("""
            SELECT f.empresa FROM Favorita f
            WHERE f.cliente.id = :clienteId AND f.empresa.ativo = true
            ORDER BY f.dataCriacao DESC
            """)
    List<Empresa> listarEmpresasAtivas(@Param("clienteId") UUID clienteId);

    void deleteByClienteIdAndEmpresaId(UUID clienteId, UUID empresaId);

    // Todas, inclusive de estúdio desativado: é o que a exportação de dados mostra
    List<Favorita> findByClienteIdOrderByDataCriacaoDesc(UUID clienteId);

    void deleteByClienteId(UUID clienteId);

    void deleteByEmpresaId(UUID empresaId);
}
