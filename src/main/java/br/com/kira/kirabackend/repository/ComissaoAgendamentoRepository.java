package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.ComissaoAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface ComissaoAgendamentoRepository extends JpaRepository<ComissaoAgendamento, UUID> {

    List<ComissaoAgendamento> findByFuncionariaId(UUID funcionariaId);

    List<ComissaoAgendamento> findByFuncionariaIdAndAgendamento_Empresa_Id(
            UUID funcionariaId, UUID empresaId);

    @Query("SELECT SUM(c.valorComissao) FROM ComissaoAgendamento c WHERE c.funcionaria.id = :funcionariaId")
    BigDecimal calcularTotalComissaoFuncionaria(UUID funcionariaId);
}