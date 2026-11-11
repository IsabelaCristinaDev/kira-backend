package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmpresaPerfilResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        String cnpj,
        String cpf,
        String descricao,
        List<String> especialidades,
        LocalDate dataNascimento,
        EnderecoResponse endereco,
        Double latitude,
        Double longitude,
        TipoEstabelecimento tipoEstabelecimento,
        String fotoUrl
) {}
