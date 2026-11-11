package br.com.kira.kirabackend.dto.response;

public record MovimentoPorHoraResponse(
        int hora,
        int totalAtendimentos
) {}