package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// A lista troca as preferências inteiras; vazia apaga todas
public record PreferenciasRequest(
        @NotNull(message = "Categorias é obrigatório")
        @Size(max = 20, message = "Envie no máximo 20 categorias")
        List<@NotNull(message = "Categoria não pode ser vazia") CategoriaServico> categorias
) {}
