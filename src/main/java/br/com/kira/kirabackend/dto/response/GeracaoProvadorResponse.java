package br.com.kira.kirabackend.dto.response;

import java.time.LocalDateTime;

/** A prévia vai embutida (data URI JPG ou PNG): nada da imagem fica guardado no servidor. */
public record GeracaoProvadorResponse(
        String imagemUri,
        LocalDateTime geradoEm
) {}
