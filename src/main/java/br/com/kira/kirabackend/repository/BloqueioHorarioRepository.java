package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.BloqueioHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BloqueioHorarioRepository extends JpaRepository<BloqueioHorario, UUID> {

    List<BloqueioHorario> findByFuncionariaId(UUID funcionariaId);

    List<BloqueioHorario> findByFuncionariaIdAndDataHoraInicioBetween(
            UUID funcionariaId,
            LocalDateTime inicio,
            LocalDateTime fim);
}