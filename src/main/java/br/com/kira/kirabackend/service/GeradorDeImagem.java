package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.exception.GeracaoProvadorException;

/** Quem gera a prévia do Provador. Hoje roda na Cloudflare (Workers AI); trocar o serviço não mexe no resto. */
public interface GeradorDeImagem {

    /**
     * @param fotoCliente    foto original da pessoa (JPG ou PNG)
     * @param fotoReferencia imagem do estilo desejado (JPG ou PNG)
     * @return a prévia em JPG ou PNG, conforme o que o serviço devolver
     * @throws GeracaoProvadorException se o serviço não devolver uma imagem
     */
    byte[] gerar(byte[] fotoCliente, byte[] fotoReferencia);
}
