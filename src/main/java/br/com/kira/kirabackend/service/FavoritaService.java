package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.response.EstudioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FavoritaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class FavoritaService {

    private final FavoritaRepository favoritaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstudioService estudioService;

    @Transactional(readOnly = true)
    public List<EstudioResponse> listar(Usuario usuario) {
        exigirCliente(usuario);
        return favoritaRepository.listarEmpresasAtivas(usuario.getId()).stream()
                .map(estudioService::toResponse)
                .toList();
    }

    @Transactional
    public void salvar(Usuario usuario, UUID empresaId) {
        exigirCliente(usuario);
        // Empresa desativada responde como inexistente, igual à vitrine
        if (!usuarioRepository.existsEmpresaAtivaById(empresaId)) {
            throw new RecursoNaoEncontradoException("Estúdio não encontrado");
        }
        favoritaRepository.salvarSeNaoExistir(
                UUID.randomUUID(), usuario.getId(), empresaId, LocalDateTime.now(KiraTimeZone.DEFAULT));
    }

    @Transactional
    public void remover(Usuario usuario, UUID empresaId) {
        exigirCliente(usuario);
        favoritaRepository.deleteByClienteIdAndEmpresaId(usuario.getId(), empresaId);
    }

    private static void exigirCliente(Usuario usuario) {
        if (!(usuario instanceof Cliente)) {
            throw new RegraDeNegocioException("Só clientes têm favoritas");
        }
    }
}
