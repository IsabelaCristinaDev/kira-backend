package br.com.kira.kirabackend.dto.response;

import java.time.DayOfWeek;

public record MovimentoPorDiaSemanaResponse(
        DayOfWeek diaSemana,
        int totalAtendimentos
) {}