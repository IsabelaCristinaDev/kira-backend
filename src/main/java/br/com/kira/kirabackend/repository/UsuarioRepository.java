package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
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

    @Query("select count(e) > 0 from Empresa e where e.id = :id and e.ativo = true")
    boolean existsEmpresaAtivaById(UUID id);

    @Query("select u.id from Usuario u where u.id in :ids and u.ativo = true")
    Set<UUID> findIdsAtivos(Collection<UUID> ids);

    /**
     * Trava a linha da empresa até o fim da transação (SELECT ... FOR UPDATE). Dois agendamentos
     * da mesma empresa passam a ser processados um depois do outro: o segundo só confere o horário
     * depois que o primeiro gravou, e não pega o mesmo horário.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.id = :id")
    Optional<Empresa> travarEmpresaParaAgendar(UUID id);
}