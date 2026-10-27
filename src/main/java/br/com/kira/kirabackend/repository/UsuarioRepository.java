package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(c) > 0 from Cliente c where c.cpf = :cpf")
    boolean existsClienteByCpf(String cpf);

    @Query("select count(e) > 0 from Empresa e where e.cpf = :cpf")
    boolean existsEmpresaByCpf(String cpf);

    @Query("select count(e) > 0 from Empresa e where e.cnpj = :cnpj")
    boolean existsEmpresaByCnpj(String cnpj);
}