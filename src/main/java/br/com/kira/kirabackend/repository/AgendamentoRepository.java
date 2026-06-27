package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {

    List<Agendamento> findByClienteId(UUID clienteId);

    List<Agendamento> findByEmpresaId(UUID empresaId);

    List<Agendamento> findByFuncionariaId(UUID funcionariaId);

    List<Agendamento> findByEmpresaIdAndStatus(UUID empresaId, StatusAgendamento status);

    List<Agendamento> findByFuncionariaIdAndDataHoraInicioBetween(
            UUID funcionariaId,
            LocalDateTime inicio,
            LocalDateTime fim);
}