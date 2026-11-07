package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.exception.TentativasExcedidasException;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Envolve o login com o limite de tentativas: confere os bloqueios, aplica o atraso, chama o login
 * e registra o resultado.
 * <p>
 * Fica FORA do login de propósito: o {@code AuthService.login} é @Transactional, e um atraso de até 8 s
 * lá dentro seguraria uma conexão do banco (o pool tem 10). Assim, o atraso acontece antes de a
 * transação começar.
 */
@Slf4j
@Component
public class ProtecaoLogin {

    /** Espera do atraso progressivo. Nos testes, só anota o tempo em vez de esperar. */
    @FunctionalInterface
    public interface Pausa {
        void pausar(Duration tempo);
    }

    private final LimiteDeTentativas limite;
    private final VerificacaoHumana verificacaoHumana;
    private final EmailService emailService;
    private final UsuarioRepository usuarioRepository;
    private final Pausa pausa;

    public ProtecaoLogin(LimiteDeTentativas limite, VerificacaoHumana verificacaoHumana, EmailService emailService,
                         UsuarioRepository usuarioRepository, Pausa pausa) {
        this.limite = limite;
        this.verificacaoHumana = verificacaoHumana;
        this.emailService = emailService;
        this.usuarioRepository = usuarioRepository;
        this.pausa = pausa;
    }

    public <T> T proteger(String ip, String email, Supplier<T> login) {
        String conta = LimiteDeTentativas.normalizar(email);

        limite.bloqueio(ip, conta).ifPresent(espera -> {
            log.warn("Login bloqueado por excesso de tentativas: ip={} email={}", ip, LimiteDeTentativas.mascarar(conta));
            throw new TentativasExcedidasException(espera);
        });

        Duration atraso = limite.atraso(conta);
        if (!atraso.isZero()) {
            pausa.pausar(atraso);
        }

        try {
            if (limite.exigeVerificacao(conta) && !verificacaoHumana.aprovada(ip, conta)) {
                // Mesma resposta da senha errada: quem tenta não descobre o motivo
                throw new BadCredentialsException("Verificação extra reprovada");
            }
            T resultado = login.get();
            limite.registrarSucesso(ip, conta);
            return resultado;
        } catch (AuthenticationException falha) {
            limite.registrarFalha(ip, conta);
            if (limite.deveAvisar(conta)) {
                avisarDono(conta);
            }
            throw falha;
        }
    }

    // A consulta roda tenha a conta ou não: o tempo de resposta não revela se o e-mail existe
    private void avisarDono(String conta) {
        usuarioRepository.findByEmailIgnoreCase(conta).ifPresent(usuario -> {
            log.warn("Conta com muitas falhas de login, dono avisado por e-mail: email={}", LimiteDeTentativas.mascarar(conta));
            emailService.enviarAvisoTentativasSuspeitas(usuario.getEmail(), usuario.getNome());
        });
    }
}
