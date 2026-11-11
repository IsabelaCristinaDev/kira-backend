package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.CategoriaServico;

import java.util.List;

public record PreferenciasResponse(List<CategoriaServico> categorias) {}
