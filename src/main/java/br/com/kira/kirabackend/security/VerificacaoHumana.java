package br.com.kira.kirabackend.security;

/**
 * Verificação extra pedida quando uma conta acumula falhas de login (padrão Strategy).
 * Hoje só existe {@link SemVerificacaoHumana}. Para integrar um CAPTCHA, basta criar outra implementação
 * {@code @Component} (e tirar a atual): ela lê a resposta do desafio do request (ex.: um header) e
 * confere com o serviço do CAPTCHA. Nada no login precisa mudar.
 */
public interface VerificacaoHumana {

    /** @return true se quem está tentando passou na verificação */
    boolean aprovada(String ip, String email);
}
