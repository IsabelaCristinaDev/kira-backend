package br.com.kira.kirabackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limites de tentativas (kira.security.rate-limit no application.yml). Em vez de vários @Value soltos,
 * um record só: os números ficam juntos, com nome, e o Spring converte "15m" e "1h" em Duration.
 */
@ConfigurationProperties(prefix = "kira.security.rate-limit")
public record LimiteTentativasPropriedades(Login login, Recuperacao recuperacao) {

    public record Login(Limite porIpEEmail, Limite porIp, PorEmail porEmail) {
    }

    /** Pedidos de e-mail de recuperação e tentativas de usar o token recebido. */
    public record Recuperacao(Limite porEmail, Limite porIp, Limite validacaoPorIp) {
    }

    /** Até {@code maximo} vezes dentro da janela; a seguinte recebe 429. */
    public record Limite(int maximo, Duration janela) {
    }

    /** A conta nunca é bloqueada sozinha: depois de algumas falhas, o login só fica mais lento. */
    public record PorEmail(
            int falhasParaAtraso,
            Duration atrasoInicial,
            Duration atrasoMaximo,
            Duration janela,
            int falhasParaAviso,
            Duration intervaloEntreAvisos) {
    }
}
