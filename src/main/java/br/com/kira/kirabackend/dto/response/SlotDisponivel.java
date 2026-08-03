package br.com.kira.kirabackend.dto.response;

import java.time.LocalDateTime;


public record SlotDisponivel(
        LocalDateTime dataHoraInicio,
        LocalDateTime dataHoraFim
) {}