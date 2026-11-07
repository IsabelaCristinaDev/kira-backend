package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.config.LimiteTentativasPropriedades;
import br.com.kira.kirabackend.config.LimiteTentativasPropriedades.Limite;
import br.com.kira.kirabackend.config.LimiteTentativasPropriedades.PorEmail;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Conta as falhas de login. Só os números: quem decide o que fazer com eles é o {@link ProtecaoLogin}.
 * <ul>
 *   <li>IP + conta e IP: um "balde" (Bucket4j) com N fichas por janela; cada falha gasta uma, sem ficha é 429.</li>
 *   <li>Conta sozinha: só um contador, que gera atraso e verificação extra, mas nunca bloqueia
 *       (senão qualquer pessoa trancaria a conta de outra errando a senha dela).</li>
 * </ul>
 * Tudo em memória (Caffeine), com expiração e tamanho máximo. Reiniciar a API zera os contadores.
 */
public class LimiteDeTentativas {

    // Teto de chaves guardadas: um ataque com milhões de e-mails não esgota a memória
    private static final long MAXIMO_DE_CHAVES = 100_000;

    private final LimiteTentativasPropriedades.Login regras;
    private final TimeMeter relogioDosBaldes;
    private final Cache<String, Bucket> porIpEEmail;
    private final Cache<String, Bucket> porIp;
    private final Cache<String, Integer> falhasPorEmail;
    private final Cache<String, Boolean> avisosEnviados;

    /** @param relogio fonte de tempo em nanossegundos; nos testes é um relógio falso */
    public LimiteDeTentativas(LimiteTentativasPropriedades propriedades, Ticker relogio) {
        this.regras = propriedades.login();
        this.relogioDosBaldes = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return relogio.read();
            }

            @Override
            public boolean isWallClockBased() {
                return false;
            }
        };
        PorEmail porEmail = regras.porEmail();
        this.porIpEEmail = cache(relogio).expireAfterAccess(regras.porIpEEmail().janela()).build();
        this.porIp = cache(relogio).expireAfterAccess(regras.porIp().janela()).build();
        // Cada falha reescreve o valor: 15 min sem falhas zeram a conta
        this.falhasPorEmail = cache(relogio).expireAfterWrite(porEmail.janela()).build();
        this.avisosEnviados = cache(relogio).expireAfterWrite(porEmail.intervaloEntreAvisos()).build();
    }

    private static Caffeine<Object, Object> cache(Ticker relogio) {
        return Caffeine.newBuilder().ticker(relogio).maximumSize(MAXIMO_DE_CHAVES);
    }

    /** Quanto falta para poder tentar de novo, se este IP (ou este IP nesta conta) passou do limite. */
    public Optional<Duration> bloqueio(String ip, String email) {
        Optional<Duration> doPar = esperaSeVazio(porIpEEmail.getIfPresent(chavePar(ip, email)));
        return doPar.isPresent() ? doPar : esperaSeVazio(porIp.getIfPresent(ip));
    }

    public void registrarFalha(String ip, String email) {
        porIpEEmail.get(chavePar(ip, email), chave -> novoBalde(regras.porIpEEmail())).tryConsume(1);
        porIp.get(ip, chave -> novoBalde(regras.porIp())).tryConsume(1);
        falhasPorEmail.asMap().merge(normalizar(email), 1, Integer::sum);
    }

    /** Senha certa: aquele IP deixa de ter falhas naquela conta. O contador da conta continua. */
    public void registrarSucesso(String ip, String email) {
        porIpEEmail.invalidate(chavePar(ip, email));
    }

    /** 1 s na 5ª falha da conta, depois dobra a cada falha, até 8 s. */
    public Duration atraso(String email) {
        PorEmail regra = regras.porEmail();
        int acimaDoLimite = falhas(email) - regra.falhasParaAtraso();
        if (acimaDoLimite < 0) {
            return Duration.ZERO;
        }
        // Expoente limitado: 2^30 já passa de qualquer máximo razoável e não estoura o long
        Duration atraso = regra.atrasoInicial().multipliedBy(1L << Math.min(acimaDoLimite, 30));
        return atraso.compareTo(regra.atrasoMaximo()) > 0 ? regra.atrasoMaximo() : atraso;
    }

    /** Conta marcada: o próximo login passa pela verificação extra (CAPTCHA, quando existir). */
    public boolean exigeVerificacao(String email) {
        return falhas(email) >= regras.porEmail().falhasParaAtraso();
    }

    /** Verdadeiro uma única vez por intervalo, quando a conta chega ao número de falhas do aviso. */
    public boolean deveAvisar(String email) {
        String chave = normalizar(email);
        return falhas(chave) >= regras.porEmail().falhasParaAviso()
                && avisosEnviados.asMap().putIfAbsent(chave, Boolean.TRUE) == null;
    }

    /** "isabela@gmail.com" vira "is***@gmail.com": dá para investigar o log sem expor o e-mail. */
    public static String mascarar(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String usuario = email.substring(0, email.indexOf('@'));
        int visiveis = usuario.length() > 2 ? 2 : 1;
        return usuario.substring(0, Math.min(visiveis, usuario.length())) + "***" + email.substring(email.indexOf('@'));
    }

    /** "  Ana@Teste.COM " e "ana@teste.com" são a mesma conta. */
    public static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private int falhas(String email) {
        Integer falhas = falhasPorEmail.getIfPresent(normalizar(email));
        return falhas == null ? 0 : falhas;
    }

    private static String chavePar(String ip, String email) {
        return ip + ":" + normalizar(email);
    }

    // As fichas voltam todas de uma vez quando a janela termina, contada a partir da primeira falha
    private Bucket novoBalde(Limite limite) {
        return Bucket.builder()
                .addLimit(regra -> regra.capacity(limite.maxFalhas())
                        .refillIntervally(limite.maxFalhas(), limite.janela()))
                .withCustomTimePrecision(relogioDosBaldes)
                .build();
    }

    private static Optional<Duration> esperaSeVazio(Bucket balde) {
        if (balde == null || balde.getAvailableTokens() > 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofNanos(balde.estimateAbilityToConsume(1).getNanosToWaitForRefill()));
    }
}
