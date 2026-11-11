package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FotoPortfolioRepository extends JpaRepository<FotoPortfolio, UUID> {

    List<FotoPortfolio> findByEmpresaIdOrderByDataCriacaoDesc(UUID empresaId);

    long countByEmpresaId(UUID empresaId);

    Optional<FotoPortfolio> findByIdAndEmpresaId(UUID id, UUID empresaId);
}
