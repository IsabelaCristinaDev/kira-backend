package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.DisponibilidadeEstudio;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisponibilidadeEstudioRepository extends JpaRepository<DisponibilidadeEstudio, UUID> {

    List<DisponibilidadeEstudio> findByEmpresaId(UUID empresaId);

    Optional<DisponibilidadeEstudio> findByEmpresaIdAndDiaSemana(UUID empresaId, DiaSemana diaSemana);
}