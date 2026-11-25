package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {

    List<Agendamento> findByClienteIdAndStatusOrderByDataHoraInicioDesc(
            UUID clienteId,
            StatusAgendamento status);

    /**
     * Trava o agendamento até o fim da transação: dois pedidos ao mesmo tempo sobre o código de chegada
     * (gerar duas vezes, validar duas vezes) passam um de cada vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agendamento a where a.id = :id")
    Optional<Agendamento> travarParaChegada(UUID id);

    List<Agendamento> findByClienteId(UUID clienteId);

    List<Agendamento> findByEmpresaId(UUID empresaId);

    /** Se a cliente tem ao menos um agendamento com a empresa, em qualquer status. */
    boolean existsByClienteIdAndEmpresaId(UUID clienteId, UUID empresaId);

    List<Agendamento> findByFuncionariaId(UUID funcionariaId);

    List<Agendamento> findByEmpresaIdAndStatus(UUID empresaId, StatusAgendamento status);

    List<Agendamento> findByFuncionariaIdAndDataHoraInicioBetween(
            UUID funcionariaId,
            LocalDateTime inicio,
            LocalDateTime fim);

    /**
     * Agendamentos da funcionária que começam em [inicio, fim), em ordem de horário. O fim é exclusivo
     * (meia-noite do dia seguinte): o "último instante do dia" com nanossegundos é arredondado para
     * microssegundos no Postgres e vira a meia-noite seguinte.
     */
    @Query("select a from Agendamento a where a.funcionaria.id = :funcionariaId"
            + " and a.dataHoraInicio >= :inicio and a.dataHoraInicio < :fim order by a.dataHoraInicio")
    List<Agendamento> listarDaFuncionariaNoPeriodo(UUID funcionariaId, LocalDateTime inicio, LocalDateTime fim);

    List<Agendamento> findByStatusNotInAndLembreteEnviadoFalseAndDataHoraInicioBetween(
            List<StatusAgendamento> statusExcluidos,
            LocalDateTime inicio,
            LocalDateTime fim);

    /**
     * Agendamentos da empresa que se sobrepõem ao intervalo [inicio, fim),
     * ignorando o status informado (normalmente CANCELADO).
     */
    List<Agendamento> findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
            UUID empresaId,
            StatusAgendamento statusIgnorado,
            LocalDateTime fim,
            LocalDateTime inicio);

    /** Agendamentos da empresa no status informado que começam em [inicio, fim), com o fim exclusivo. */
    @Query("select a from Agendamento a where a.empresa.id = :empresaId and a.status = :status"
            + " and a.dataHoraInicio >= :inicio and a.dataHoraInicio < :fim")
    List<Agendamento> listarDaEmpresaNoPeriodo(
            UUID empresaId, StatusAgendamento status, LocalDateTime inicio, LocalDateTime fim);

    /** Contagem de {@link #listarDaEmpresaNoPeriodo}, com o mesmo período [inicio, fim). */
    @Query("select count(a) from Agendamento a where a.empresa.id = :empresaId and a.status = :status"
            + " and a.dataHoraInicio >= :inicio and a.dataHoraInicio < :fim")
    long contarDaEmpresaNoPeriodo(
            UUID empresaId, StatusAgendamento status, LocalDateTime inicio, LocalDateTime fim);

    // Atendimentos que ainda vão acontecer: usados para cancelar os de uma conta excluída
    List<Agendamento> findByClienteIdAndStatusInAndDataHoraInicioAfter(
            UUID clienteId,
            Collection<StatusAgendamento> status,
            LocalDateTime agora);

    List<Agendamento> findByEmpresaIdAndStatusInAndDataHoraInicioAfter(
            UUID empresaId,
            Collection<StatusAgendamento> status,
            LocalDateTime agora);
}
