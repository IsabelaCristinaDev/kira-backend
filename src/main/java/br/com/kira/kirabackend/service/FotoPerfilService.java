package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.response.FotoPerfilResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FotoPerfilService {

    private final UsuarioRepository usuarioRepository;
    private final ImagensEnviadas imagens;

    public FotoPerfilResponse definir(UUID usuarioId, MultipartFile arquivo) {
        Usuario usuario = buscarUsuario(usuarioId);
        String fotoAnterior = usuario.getFotoUrl();
        String fotoNova = imagens.guardar(arquivo);
        usuario.setFotoUrl(fotoNova);
        try {
            usuarioRepository.save(usuario);
        } catch (RuntimeException e) {
            imagens.apagarSemFalhar(fotoNova);
            throw e;
        }
        imagens.apagarSemFalhar(fotoAnterior);
        return new FotoPerfilResponse(fotoNova);
    }

    public void remover(UUID usuarioId) {
        Usuario usuario = buscarUsuario(usuarioId);
        String fotoAnterior = usuario.getFotoUrl();
        usuario.setFotoUrl(null);
        usuarioRepository.save(usuario);
        imagens.apagarSemFalhar(fotoAnterior);
    }

    private Usuario buscarUsuario(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }
}
