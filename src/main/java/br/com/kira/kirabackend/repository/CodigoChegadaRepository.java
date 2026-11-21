package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.CodigoChegada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CodigoChegadaRepository extends JpaRepository<CodigoChegada, UUID> {

    Optional<CodigoChegada> findByAgendamentoId(UUID agendamentoId);
}
