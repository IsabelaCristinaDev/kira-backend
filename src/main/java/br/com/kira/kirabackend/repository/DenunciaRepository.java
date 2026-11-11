package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Denuncia;
import br.com.kira.kirabackend.domain.enums.StatusDenuncia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DenunciaRepository extends JpaRepository<Denuncia, UUID> {

    List<Denuncia> findByStatusOrderByRiscoImediatoDescDataCriacaoAsc(StatusDenuncia status);

    boolean existsByDenuncianteIdAndRiscoImediatoTrueAndInfundadaTrue(UUID denuncianteId);

    boolean existsByDenuncianteIdAndDenunciadoIdAndStatus(UUID denuncianteId, UUID denunciadoId, StatusDenuncia status);
}
