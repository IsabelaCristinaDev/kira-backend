package br.com.kira.kirabackend.dto.response;

import java.time.LocalDateTime;

//  DTO que o Flutter vai usar para montar o calendário
public record SlotDisponivel(
        LocalDateTime dataHoraInicio,
        LocalDateTime dataHoraFim
) {}