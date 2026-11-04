package br.com.kira.kirabackend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record FotoPortfolioResponse(UUID id, String url, LocalDateTime dataCriacao) {}
