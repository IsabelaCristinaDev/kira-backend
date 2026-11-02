package br.com.kira.kirabackend.dto.response;

/** Chave que o navegador pede ao aceitar as notificações. Vem nula se o envio não estiver configurado. */
public record ChavePublicaResponse(String chavePublica) {}
