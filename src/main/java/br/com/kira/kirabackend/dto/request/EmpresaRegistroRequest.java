package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.security.SenhaCabeNoBcrypt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record EmpresaRegistroRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        @SenhaCabeNoBcrypt
        String senha,

        @NotBlank(message = "Telefone é obrigatório")
        String telefone,

        String cnpj,

        String cpf,

        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
        String descricao,

        LocalDate dataNascimento,

        @Valid
        EnderecoRequest endereco,

        Double latitude,

        Double longitude,

        List<String> especialidades,

        // @Valid confere cada serviço; o teto evita uma lista sem fim num único cadastro
        @Size(max = 50, message = "Cadastre no máximo 50 serviços")
        List<@Valid ServicoRegistroRequest> servicos,

        @NotNull(message = "Tipo de estabelecimento é obrigatório")
        TipoEstabelecimento tipoEstabelecimento
) {}