package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Denuncia;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.SuspensaoPerfil;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.SuspensaoPerfilRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuspensaoPerfilServiceTest {

    @Mock
    private SuspensaoPerfilRepository suspensaoPerfilRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private SuspensaoPerfilService service;

    private Empresa empresa;
    private Denuncia denuncia;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setAtivo(true);

        denuncia = new Denuncia();
        denuncia.setId(UUID.randomUUID());
    }

    private SuspensaoPerfil suspensaoAtiva() {
        SuspensaoPerfil s = new SuspensaoPerfil();
        s.setId(UUID.randomUUID());
        s.setUsuario(empresa);
        s.setDenuncia(denuncia);
        s.setPreventiva(true);
        s.setAtiva(true);
        empresa.setAtivo(false);
        return s;
    }

    @Test
    @DisplayName("Suspender desativa o perfil e derruba o refresh token")
    void suspender() {
        when(suspensaoPerfilRepository.save(any(SuspensaoPerfil.class))).thenAnswer(inv -> inv.getArgument(0));

        SuspensaoPerfil suspensao = service.suspender(empresa, denuncia, true);

        assertTrue(suspensao.getPreventiva());
        assertTrue(suspensao.getAtiva());
        assertFalse(empresa.getAtivo());
        verify(usuarioRepository).save(empresa);
        verify(refreshTokenRepository).deleteByUsuarioId(empresa.getId());
    }

    @Test
    @DisplayName("Revogar reativa o perfil quando não há outra suspensão")
    void revogarReativa() {
        SuspensaoPerfil s = suspensaoAtiva();
        when(suspensaoPerfilRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suspensaoPerfilRepository.existsByUsuarioIdAndAtivaTrue(empresa.getId())).thenReturn(false);

        service.revogar(s.getId(), adminId);

        assertFalse(s.getAtiva());
        assertNotNull(s.getDataFim());
        assertTrue(empresa.getAtivo());
        verify(usuarioRepository).save(empresa);
    }

    @Test
    @DisplayName("Revogar mantém o perfil inativo se houver outra suspensão ativa")
    void revogarComOutraSuspensao() {
        SuspensaoPerfil s = suspensaoAtiva();
        when(suspensaoPerfilRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suspensaoPerfilRepository.existsByUsuarioIdAndAtivaTrue(empresa.getId())).thenReturn(true);

        service.revogar(s.getId(), adminId);

        assertFalse(s.getAtiva());
        assertFalse(empresa.getAtivo());
        verify(usuarioRepository, never()).save(empresa);
    }

    @Test
    @DisplayName("Não revoga suspensão já revogada")
    void revogarJaRevogada() {
        SuspensaoPerfil s = suspensaoAtiva();
        s.setAtiva(false);
        when(suspensaoPerfilRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThrows(RegraDeNegocioException.class, () -> service.revogar(s.getId(), adminId));
    }

    @Test
    @DisplayName("Suspensão inexistente gera 404")
    void revogarInexistente() {
        UUID id = UUID.randomUUID();
        when(suspensaoPerfilRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.revogar(id, adminId));
    }

    @Test
    @DisplayName("Confirmar transforma a suspensão preventiva em definitiva")
    void confirmar() {
        SuspensaoPerfil s = suspensaoAtiva();
        when(suspensaoPerfilRepository.findByDenunciaIdAndAtivaTrue(denuncia.getId())).thenReturn(List.of(s));

        service.confirmarDaDenuncia(denuncia.getId());

        ArgumentCaptor<SuspensaoPerfil> captor = ArgumentCaptor.forClass(SuspensaoPerfil.class);
        verify(suspensaoPerfilRepository).save(captor.capture());
        assertFalse(captor.getValue().getPreventiva());
    }
}
