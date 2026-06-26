package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmpresaRegistroRequest(

        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        String senha,

        @NotBlank(message = "Telefone é obrigatório")
        String telefone,

        String cnpj,

        String cpf,

        String descricao,

        String endereco,

        @NotNull(message = "Tipo de estabelecimento é obrigatório")
        TipoEstabelecimento tipoEstabelecimento
) {}