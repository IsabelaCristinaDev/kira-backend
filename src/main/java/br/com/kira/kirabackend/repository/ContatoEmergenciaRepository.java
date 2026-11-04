package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.ContatoEmergencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContatoEmergenciaRepository extends JpaRepository<ContatoEmergencia, UUID> {

    List<ContatoEmergencia> findByClienteIdOrderByDataCriacaoAsc(UUID clienteId);

    long countByClienteId(UUID clienteId);

    Optional<ContatoEmergencia> findByIdAndClienteId(UUID id, UUID clienteId);
}
