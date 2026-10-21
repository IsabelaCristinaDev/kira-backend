package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Denuncia;
import br.com.kira.kirabackend.domain.entity.SuspensaoPerfil;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.SuspensaoPerfilRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional
public class SuspensaoPerfilService {

    private final SuspensaoPerfilRepository suspensaoPerfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public SuspensaoPerfil suspender(Usuario usuario, Denuncia denuncia, boolean preventiva) {
        SuspensaoPerfil suspensao = new SuspensaoPerfil();
        suspensao.setUsuario(usuario);
        suspensao.setDenuncia(denuncia);
        suspensao.setPreventiva(preventiva);
        suspensao.setAtiva(true);
        SuspensaoPerfil salva = suspensaoPerfilRepository.save(suspensao);

        // Reaproveita o "ativo" já existente: o login passa a recusar o usuário
        // e o refresh token deixa de existir, então a sessão atual não se renova.
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
        refreshTokenRepository.deleteByUsuarioId(usuario.getId());

        return salva;
    }

    public void revogar(UUID suspensaoId, UUID adminId) {
        SuspensaoPerfil suspensao = suspensaoPerfilRepository.findById(suspensaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Suspensão não encontrada"));
        if (!suspensao.getAtiva()) {
            throw new RegraDeNegocioException("Esta suspensão já foi revogada");
        }
        revogar(suspensao, adminId);
    }

    public void revogarDaDenuncia(UUID denunciaId, UUID adminId) {
        suspensaoPerfilRepository.findByDenunciaIdAndAtivaTrue(denunciaId)
                .forEach(suspensao -> revogar(suspensao, adminId));
    }

    public void confirmarDaDenuncia(UUID denunciaId) {
        suspensaoPerfilRepository.findByDenunciaIdAndAtivaTrue(denunciaId)
                .forEach(suspensao -> {
                    suspensao.setPreventiva(false);
                    suspensaoPerfilRepository.save(suspensao);
                });
    }

    private void revogar(SuspensaoPerfil suspensao, UUID adminId) {
        suspensao.setAtiva(false);
        suspensao.setDataFim(LocalDateTime.now(KiraTimeZone.DEFAULT));
        suspensao.setRevogadaPor(usuarioRepository.getReferenceById(adminId));
        suspensaoPerfilRepository.save(suspensao);

        // Só reativa o perfil se não restar outra suspensão em vigor contra ele.
        Usuario usuario = suspensao.getUsuario();
        if (!suspensaoPerfilRepository.existsByUsuarioIdAndAtivaTrue(usuario.getId())) {
            usuario.setAtivo(true);
            usuarioRepository.save(usuario);
        }
    }
}
