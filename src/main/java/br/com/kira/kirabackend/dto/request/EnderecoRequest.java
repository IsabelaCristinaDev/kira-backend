package br.com.kira.kirabackend.dto.request;

public record EnderecoRequest(
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado
) {}