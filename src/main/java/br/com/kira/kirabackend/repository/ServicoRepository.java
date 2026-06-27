package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

    @Repository
    public interface ServicoRepository extends JpaRepository<Servico, UUID> {

        List<Servico> findByEmpresaId(UUID empresaId);

        List<Servico> findByEmpresaIdAndAtivoTrue(UUID empresaId);
    }
