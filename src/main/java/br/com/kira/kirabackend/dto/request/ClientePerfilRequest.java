package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.Genero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// E-mail e CPF não mudam por aqui: são o login e o documento da conta
public record ClientePerfilRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,
        @NotBlank(message = "Telefone é obrigatório")
        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        String telefone,
        @NotNull(message = "Data de nascimento é obrigatória")
        @Past(message = "Data de nascimento deve estar no passado")
        LocalDate dataNascimento,
        @NotNull(message = "Gênero é obrigatório")
        Genero genero,
        EnderecoRequest endereco,
        Double latitude,
        Double longitude
) {}
