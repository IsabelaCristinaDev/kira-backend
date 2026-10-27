package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Confere se o usuário logado é a empresa dona do recurso da rota
 * (serviços, funcionárias, bloqueios, agenda, comissões e painel).
 */
@RequiredArgsConstructor
@Component
public class AcessoEmpresa {

    static final String SO_O_SEU_ESTABELECIMENTO = "Você só pode acessar os dados do seu estabelecimento";

    private final FuncionariaRepository funcionariaRepository;

    public void exigirEmpresa(UUID empresaId, Usuario usuario) {
        if (!empresaId.equals(usuario.getId())) {
            throw new RegraDeNegocioException(SO_O_SEU_ESTABELECIMENTO);
        }
    }

    public void exigirFuncionariaDaEmpresa(UUID funcionariaId, Usuario usuario) {
        Funcionaria funcionaria = funcionariaRepository.findById(funcionariaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionária não encontrada"));
        exigirEmpresa(funcionaria.getEmpresa().getId(), usuario);
    }
}
