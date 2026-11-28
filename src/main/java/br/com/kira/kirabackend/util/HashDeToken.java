package br.com.kira.kirabackend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 dos tokens guardados no banco (recuperação de senha e refresh token): quem lê o banco
 * não consegue usar o token. O mesmo token sempre gera o mesmo hash, então dá para buscar por ele.
 */
public final class HashDeToken {

    private HashDeToken() {
    }

    public static String sha256(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Todo Java tem SHA-256 por especificação: não acontece
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
