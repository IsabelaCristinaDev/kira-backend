package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dados que o navegador entrega ao aceitar as notificações. */
public record DispositivoWebRequest(

        @NotBlank(message = "Endereço do navegador é obrigatório")
        @Size(max = 1000, message = "Endereço do navegador muito longo")
        String endpoint,

        @NotBlank(message = "Chave do navegador é obrigatória")
        @Size(max = 200, message = "Chave do navegador muito longa")
        String p256dh,

        @NotBlank(message = "Segredo do navegador é obrigatório")
        @Size(max = 100, message = "Segredo do navegador muito longo")
        String auth
) {}
