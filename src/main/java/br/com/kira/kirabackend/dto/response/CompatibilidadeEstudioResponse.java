package br.com.kira.kirabackend.dto.response;

import java.util.UUID;

/** Quanto o portfólio do estúdio se parece com a foto de referência, de 0 a 100 (a foto mais parecida dele). */
public record CompatibilidadeEstudioResponse(
        UUID estudioId,
        double similaridade
) {}
