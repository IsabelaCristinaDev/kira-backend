package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Funcionaria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FuncionariaRepository extends JpaRepository<Funcionaria, UUID> {
    List<Funcionaria> findByEmpresaId(UUID empresaId);

    List<Funcionaria> findByEmpresaIdAndAtivoTrue(UUID empresaId);

}
