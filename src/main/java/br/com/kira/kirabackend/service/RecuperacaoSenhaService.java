package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.TokenRecuperacaoSenha;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.RecuperacaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.TentativasExcedidasException;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.LimiteDeTentativas;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@RequiredArgsConstructor
@Service
public class RecuperacaoSenhaService {

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimiteDeTentativas limite;
    private final EnvioRecuperacaoSenha envio;

    /**
     * Confere o limite agora (pode responder 429) e deixa o resto para o segundo plano. Daqui não se consulta
     * o banco: a resposta é a mesma, no mesmo tempo, exista a conta ou não. O limite também conta
     * e-mails sem conta, então o 429 não revela nada.
     */
    public void solicitarRecuperacao(RecuperacaoSenhaRequest request, String ip) {
        String email = LimiteDeTentativas.normalizar(request.email());
        limite.registrarPedidoDeRecuperacao(ip, email).ifPresent(espera -> {
            log.warn("Recuperação de senha bloqueada por excesso de pedidos: ip={} email={}",
                    ip, LimiteDeTentativas.mascarar(email));
            throw new TentativasExcedidasException(espera);
        });
        envio.gerarEEnviar(email);
    }

    @Transactional
    public void redefinirSenha(RedefinicaoSenhaRequest request, String ip) {
        limite.registrarValidacaoDeToken(ip).ifPresent(espera -> {
            log.warn("Redefinição de senha bloqueada por excesso de tentativas: ip={}", ip);
            throw new TentativasExcedidasException(espera);
        });

        TokenRecuperacaoSenha tokenRecuperacao = tokenRepository
                .findByTokenHash(TokenRecuperacaoSenha.hashDoToken(request.token()))
                .orElseThrow(() -> new RegraDeNegocioException("Token inválido"));

        if (tokenRecuperacao.isExpirado()) {
            throw new RegraDeNegocioException("Token expirado");
        }

        if (Boolean.TRUE.equals(tokenRecuperacao.getUsado())) {
            throw new RegraDeNegocioException("Token já utilizado");
        }

        Usuario usuario = tokenRecuperacao.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        // Encerra todas as sessões: o JWT emitido antes deste momento deixa de valer,
        // e sem refresh token ninguém consegue um JWT novo sem a senha nova
        usuario.setSenhaAlteradaEm(LocalDateTime.now(KiraTimeZone.DEFAULT).truncatedTo(ChronoUnit.SECONDS));
        usuarioRepository.save(usuario);
        refreshTokenRepository.deleteByUsuarioId(usuario.getId());

        // Uso único: este fica marcado, os outros links pendentes são apagados
        tokenRecuperacao.setUsado(true);
        tokenRepository.save(tokenRecuperacao);
        tokenRepository.deleteByUsuarioIdAndIdNot(usuario.getId(), tokenRecuperacao.getId());
    }
}
