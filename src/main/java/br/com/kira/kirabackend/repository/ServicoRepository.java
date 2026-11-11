package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

    @Repository
    public interface ServicoRepository extends JpaRepository<Servico, UUID> {

        List<Servico> findByEmpresaId(UUID empresaId);

        List<Servico> findByEmpresaIdAndAtivoTrue(UUID empresaId);

        List<Servico> findByEmpresaIdInAndAtivoTrue(Collection<UUID> empresaIds);

        List<Servico> findByCategoriaIsNull();

        // Serviços que a cliente pode contratar hoje: o serviço e o estúdio estão ativos
        List<Servico> findByAtivoTrueAndEmpresaAtivoTrue();
    }
