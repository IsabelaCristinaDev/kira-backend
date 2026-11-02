package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Mensagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MensagemRepository extends JpaRepository<Mensagem, UUID> {

    List<Mensagem> findByAgendamentoIdOrderByDataEnvioAsc(UUID agendamentoId);

    long countByAgendamentoIdAndLidaFalseAndRemetenteIdNot(UUID agendamentoId, UUID remetenteId);

    @Modifying
    @Query("""
            update Mensagem m set m.lida = true
            where m.agendamento.id = :agendamentoId and m.lida = false and m.remetente.id <> :usuarioId
            """)
    int marcarRecebidasComoLidas(@Param("agendamentoId") UUID agendamentoId, @Param("usuarioId") UUID usuarioId);
}