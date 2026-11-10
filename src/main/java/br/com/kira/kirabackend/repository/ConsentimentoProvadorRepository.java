package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.ConsentimentoProvador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsentimentoProvadorRepository extends JpaRepository<ConsentimentoProvador, UUID> {

    Optional<ConsentimentoProvador> findByUsuarioId(UUID usuarioId);

    void deleteByUsuarioId(UUID usuarioId);
}
