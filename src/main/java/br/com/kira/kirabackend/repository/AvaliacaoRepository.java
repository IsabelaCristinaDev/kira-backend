package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

    List<Avaliacao> findByEmpresaIdAndTipoAvaliador(UUID empresaId, TipoAvaliador tipoAvaliador);

    List<Avaliacao> findByClienteIdAndTipoAvaliador(UUID clienteId, TipoAvaliador tipoAvaliador);

    Optional<Avaliacao> findByAgendamentoIdAndTipoAvaliador(UUID agendamentoId, TipoAvaliador tipoAvaliador);

    boolean existsByAgendamentoIdAndTipoAvaliador(UUID agendamentoId, TipoAvaliador tipoAvaliador);

    @Query("SELECT AVG(a.nota) FROM Avaliacao a WHERE a.empresa.id = :empresaId AND a.tipoAvaliador = 'CLIENTE'")
    Double calcularMediaAvaliacaoEmpresa(UUID empresaId);
}