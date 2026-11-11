package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.StatusDenuncia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnaliseDenunciaRequest(

        @NotNull(message = "Decisão é obrigatória")
        StatusDenuncia decisao,

        @NotBlank(message = "Parecer é obrigatório")
        String parecer,

        @NotNull(message = "Informe se o perfil deve ser suspenso")
        Boolean suspenderPerfil,

        @NotNull(message = "Informe se a autoridade competente foi acionada")
        Boolean autoridadeAcionada,

        @NotNull(message = "Informe se a denúncia é infundada")
        Boolean infundada
) {}
