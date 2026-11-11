package br.com.kira.kirabackend.dto.response;

/** Resposta do pedido de recuperação de senha: um texto que é sempre o mesmo, exista a conta ou não. */
public record RecuperacaoSenhaResponse(String mensagem) {
}
