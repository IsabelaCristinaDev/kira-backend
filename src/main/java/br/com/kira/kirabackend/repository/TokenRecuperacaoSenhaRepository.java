package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.entity.TokenRecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenRecuperacaoSenhaRepository extends JpaRepository<TokenRecuperacaoSenha, UUID> {

    Optional<TokenRecuperacaoSenha> findByTokenHash(String tokenHash);

    void deleteByUsuarioId(UUID usuarioId);

    /** Depois de redefinir a senha: apaga os outros links pendentes e mantém o usado (para responder "já utilizado"). */
    void deleteByUsuarioIdAndIdNot(UUID usuarioId, UUID tokenUsadoId);
}
