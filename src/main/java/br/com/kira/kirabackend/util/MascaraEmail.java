package br.com.kira.kirabackend.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Esconde e-mails antes de irem para o log (LGPD): "isabela@gmail.com" vira "is***@gmail.com".
 * Fica em util porque é usada pela segurança, pelos services e pelo job, sem ser de nenhum deles.
 */
public final class MascaraEmail {

    // Simples de propósito: serve para achar e-mails em mensagens de erro, não para validar
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private MascaraEmail() {
    }

    public static String mascarar(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int arroba = email.indexOf('@');
        String usuario = email.substring(0, arroba);
        int visiveis = Math.min(usuario.length() > 2 ? 2 : 1, usuario.length());
        return usuario.substring(0, visiveis) + "***" + email.substring(arroba);
    }

    /** Mascara todos os e-mails de um texto, como a mensagem de erro do servidor de e-mail. */
    public static String mascararNoTexto(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher encontrados = EMAIL.matcher(texto);
        StringBuilder resultado = new StringBuilder();
        while (encontrados.find()) {
            encontrados.appendReplacement(resultado, Matcher.quoteReplacement(mascarar(encontrados.group())));
        }
        encontrados.appendTail(resultado);
        return resultado.toString();
    }
}
