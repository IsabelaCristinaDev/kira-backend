package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.Genero;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ClienteRegistroRequest (

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

    @NotBlank(message = "CPF é obrigatório")
    String cpf,

    @NotNull(message = "Data de nascimento é obrigatória")
    LocalDate dataNascimento,

    @NotNull(message = "Gênero é obrigatório")
    Genero genero,

    EnderecoRequest endereco,

    Double latitude,

    Double longitude
) {}