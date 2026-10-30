package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// E-mail, CNPJ e CPF não mudam por aqui: são o login e os documentos da conta
public record EmpresaPerfilRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,
        @NotBlank(message = "Telefone é obrigatório")
        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        String telefone,
        String descricao,
        List<String> especialidades,
        @Past(message = "Data de nascimento deve estar no passado")
        LocalDate dataNascimento,
        EnderecoRequest endereco,
        Double latitude,
        Double longitude,
        @NotNull(message = "Tipo de estabelecimento é obrigatório")
        TipoEstabelecimento tipoEstabelecimento
) {}
