package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.TokenRecuperacaoSenha;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Gera e envia o link de recuperação, em segundo plano.
 * <p>
 * É uma classe separada do {@link RecuperacaoSenhaService} porque o @Async só funciona quando o método é
 * chamado de outro bean. Rodando à parte, a resposta da API sai no mesmo tempo exista a conta ou não:
 * a busca no banco e a gravação do token acontecem depois.
 */
@Service
@RequiredArgsConstructor
public class EnvioRecuperacaoSenha {

    private static final int BYTES_DO_TOKEN = 32;
    private static final int MINUTOS_DE_VALIDADE = 15;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final EmailService emailService;

    @Async
    @Transactional
    public void gerarEEnviar(String email) {
        var usuario = usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
        if (usuario == null) {
            return;
        }

        // Um link novo invalida os anteriores
        tokenRepository.deleteByUsuarioId(usuario.getId());

        String token = novoToken();
        TokenRecuperacaoSenha registro = new TokenRecuperacaoSenha();
        registro.setTokenHash(TokenRecuperacaoSenha.hashDoToken(token));
        registro.setUsuario(usuario);
        registro.setDataExpiracao(LocalDateTime.now(KiraTimeZone.DEFAULT).plusMinutes(MINUTOS_DE_VALIDADE));
        registro.setUsado(false);
        tokenRepository.save(registro);

        // Só o e-mail recebe o token original
        emailService.enviarEmailRecuperacaoSenha(usuario.getEmail(), token);
    }

    // 32 bytes do SecureRandom em Base64 próprio para URL (sem "+", "/" nem "="): vai direto no link
    private static String novoToken() {
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
