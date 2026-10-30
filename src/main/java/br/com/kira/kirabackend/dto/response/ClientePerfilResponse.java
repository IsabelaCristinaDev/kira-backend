package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.Genero;

import java.time.LocalDate;
import java.util.UUID;

public record ClientePerfilResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        String cpf,
        LocalDate dataNascimento,
        Genero genero,
        EnderecoResponse endereco,
        Double latitude,
        Double longitude,
        String fotoUrl
) {}
