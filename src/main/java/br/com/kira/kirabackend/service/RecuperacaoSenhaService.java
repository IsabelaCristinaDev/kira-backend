package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.TokenRecuperacaoSenha;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.RecuperacaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class RecuperacaoSenhaService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TokenRecuperacaoSenhaRepository tokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public void solicitarRecuperacao(RecuperacaoSenhaRequest request) {
        var usuario = usuarioRepository.findByEmail(request.email())
                .orElse(null);

        if (usuario == null) {
            return;
        }

        tokenRepository.deleteByUsuarioId(usuario.getId());

        String token = UUID.randomUUID().toString();

        TokenRecuperacaoSenha tokenRecuperacao = new TokenRecuperacaoSenha();
        tokenRecuperacao.setToken(token);
        tokenRecuperacao.setUsuario(usuario);
        tokenRecuperacao.setDataExpiracao(LocalDateTime.now(KiraTimeZone.DEFAULT).plusMinutes(30));
        tokenRecuperacao.setUsado(false);

        tokenRepository.save(tokenRecuperacao);

        emailService.enviarEmailRecuperacaoSenha(usuario.getEmail(), token);
    }

    public void redefinirSenha(RedefinicaoSenhaRequest request) {
        TokenRecuperacaoSenha tokenRecuperacao = tokenRepository
                .findByToken(request.token())
                .orElseThrow(() -> new RegraDeNegocioException("Token inválido"));

        if (tokenRecuperacao.isExpirado()) {
            throw new RegraDeNegocioException("Token expirado");
        }

        if (tokenRecuperacao.getUsado()) {
            throw new RegraDeNegocioException("Token já utilizado");
        }

        Usuario usuario = tokenRecuperacao.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuarioRepository.save(usuario);

        tokenRecuperacao.setUsado(true);
        tokenRepository.save(tokenRecuperacao);
    }
}