package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.service.AgendaFuncionariaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/funcionarias/{funcionariaId}/agenda")
@RequiredArgsConstructor
public class AgendaFuncionariaController {

    private final AgendaFuncionariaService agendaFuncionariaService;
    private final AcessoEmpresa acessoEmpresa;

    @GetMapping("/dia")
    public ResponseEntity<List<AgendamentoResponse>> agendaDiaria(
            @PathVariable UUID funcionariaId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        acessoEmpresa.exigirFuncionariaDaEmpresa(funcionariaId, usuario);
        return ResponseEntity.ok(
                agendaFuncionariaService.listarPorDia(funcionariaId, data));
    }

    @GetMapping("/semana")
    public ResponseEntity<List<AgendamentoResponse>> agendaSemanal(
            @PathVariable UUID funcionariaId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio) {
        acessoEmpresa.exigirFuncionariaDaEmpresa(funcionariaId, usuario);
        return ResponseEntity.ok(
                agendaFuncionariaService.listarPorSemana(funcionariaId, dataInicio));
    }
}