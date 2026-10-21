package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.SuspensaoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SuspensaoPerfilRepository extends JpaRepository<SuspensaoPerfil, UUID> {

    List<SuspensaoPerfil> findByDenunciaIdAndAtivaTrue(UUID denunciaId);

    boolean existsByDenunciaIdAndAtivaTrue(UUID denunciaId);

    boolean existsByUsuarioIdAndAtivaTrue(UUID usuarioId);
}
