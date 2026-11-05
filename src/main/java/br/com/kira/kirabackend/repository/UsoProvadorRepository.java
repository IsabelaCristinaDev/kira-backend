package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.UsoProvador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

public interface UsoProvadorRepository extends JpaRepository<UsoProvador, UUID> {

    /**
     * Reserva uma geração do dia e devolve 1, ou 0 se a pessoa já chegou ao limite.
     * Numa operação só do banco, para pedidos ao mesmo tempo não passarem do limite.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO uso_provador (usuario_id, dia, quantidade)
            VALUES (:usuarioId, :dia, 1)
            ON CONFLICT (usuario_id) DO UPDATE
            SET quantidade = CASE WHEN uso_provador.dia = EXCLUDED.dia THEN uso_provador.quantidade + 1 ELSE 1 END,
                dia = EXCLUDED.dia
            WHERE uso_provador.dia <> EXCLUDED.dia OR uso_provador.quantidade < :limite
            """, nativeQuery = true)
    int reservar(@Param("usuarioId") UUID usuarioId, @Param("dia") LocalDate dia, @Param("limite") int limite);

    /** Devolve a reserva de uma geração que falhou. Depois da virada do dia, não há o que devolver. */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE uso_provador SET quantidade = quantidade - 1
            WHERE usuario_id = :usuarioId AND dia = :dia AND quantidade > 0
            """, nativeQuery = true)
    int devolver(@Param("usuarioId") UUID usuarioId, @Param("dia") LocalDate dia);
}
