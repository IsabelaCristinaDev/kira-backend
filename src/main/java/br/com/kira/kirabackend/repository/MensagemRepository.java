package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Mensagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MensagemRepository extends JpaRepository<Mensagem, UUID> {

    List<Mensagem> findByAgendamentoIdOrderByDataEnvioAsc(UUID agendamentoId);

    long countByAgendamentoIdAndLidaFalseAndRemetenteIdNot(UUID agendamentoId, UUID remetenteId);
}