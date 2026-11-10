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

    interface ConversaResumo {
        Mensagem getUltimaMensagem();

        long getNaoLidas();
    }

    /**
     * Conversas da pessoa, da mais recente para a mais antiga. Uma consulta só, com as relações já carregadas,
     * para o custo não crescer com o número de conversas.
     */
    @Query("""
            select m as ultimaMensagem,
                   (select count(x) from Mensagem x
                    where x.agendamento = a and x.lida = false and x.remetente.id <> :usuarioId) as naoLidas
            from Mensagem m
            join fetch m.remetente
            join fetch m.agendamento a
            join fetch a.cliente
            join fetch a.empresa
            join fetch a.servico
            where (a.cliente.id = :usuarioId or a.empresa.id = :usuarioId)
              and m.dataEnvio = (select max(u.dataEnvio) from Mensagem u where u.agendamento = a)
            order by m.dataEnvio desc
            """)
    List<ConversaResumo> listarConversas(@Param("usuarioId") UUID usuarioId);

    List<Mensagem> findByAgendamentoIdOrderByDataEnvioAsc(UUID agendamentoId);

    long countByAgendamentoIdAndLidaFalseAndRemetenteIdNot(UUID agendamentoId, UUID remetenteId);

    @Modifying
    @Query("""
            update Mensagem m set m.lida = true
            where m.agendamento.id = :agendamentoId and m.lida = false and m.remetente.id <> :usuarioId
            """)
    int marcarRecebidasComoLidas(@Param("agendamentoId") UUID agendamentoId, @Param("usuarioId") UUID usuarioId);

    List<Mensagem> findByRemetenteIdOrderByDataEnvioAsc(UUID remetenteId);
}
