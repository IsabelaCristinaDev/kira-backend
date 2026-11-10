package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.TokenRecuperacaoSenha;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.RecuperacaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * O código de redefinição vai só no e-mail. O banco guarda o SHA-256 dele, como faz com a senha:
 * um resumo simples basta porque o código é aleatório, e permite achar o registro pela busca direta.
 */
@RequiredArgsConstructor
@Service
@Transactional
public class RecuperacaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public void solicitarRecuperacao(RecuperacaoSenhaRequest request) {
        var usuario = usuarioRepository.findByEmailIgnoreCase(request.email())
                .orElse(null);

        if (usuario == null) {
            return;
        }

        tokenRepository.deleteByUsuarioId(usuario.getId());

        String token = UUID.randomUUID().toString();

        TokenRecuperacaoSenha tokenRecuperacao = new TokenRecuperacaoSenha();
        tokenRecuperacao.setToken(resumo(token));
        tokenRecuperacao.setUsuario(usuario);
        tokenRecuperacao.setDataExpiracao(LocalDateTime.now(KiraTimeZone.DEFAULT).plusMinutes(30));
        tokenRecuperacao.setUsado(false);

        tokenRepository.save(tokenRecuperacao);

        emailService.enviarEmailRecuperacaoSenha(usuario.getEmail(), token);
    }

    public void redefinirSenha(RedefinicaoSenhaRequest request) {
        TokenRecuperacaoSenha tokenRecuperacao = tokenRepository
                .findByToken(resumo(request.token()))
                .orElseThrow(() -> new RegraDeNegocioException("Token inválido"));

        if (tokenRecuperacao.isExpirado()) {
            throw new RegraDeNegocioException("Token expirado");
        }

        if (Boolean.TRUE.equals(tokenRecuperacao.getUsado())) {
            throw new RegraDeNegocioException("Token já utilizado");
        }

        Usuario usuario = tokenRecuperacao.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuarioRepository.save(usuario);

        tokenRecuperacao.setUsado(true);
        tokenRepository.save(tokenRecuperacao);
    }

    static String resumo(String codigo) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM traz o SHA-256: só chega aqui com o ambiente quebrado
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}