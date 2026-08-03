package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.DisponibilidadeRequest;
import br.com.kira.kirabackend.dto.response.DisponibilidadeResponse;
import br.com.kira.kirabackend.dto.response.SlotDisponivel;
import br.com.kira.kirabackend.service.DisponibilidadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/empresas/{empresaId}/disponibilidade")
public class DisponibilidadeController {

    private final DisponibilidadeService disponibilidadeService;

    @PostMapping
    public ResponseEntity<DisponibilidadeResponse> cadastrar(
            @PathVariable UUID empresaId,
            @RequestBody @Valid DisponibilidadeRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(disponibilidadeService.cadastrar(empresaId, request));
    }

    @GetMapping
    public ResponseEntity<List<DisponibilidadeResponse>> listar(
            @PathVariable UUID empresaId) {
        return ResponseEntity.ok(disponibilidadeService.listarPorEmpresa(empresaId));
    }

    @GetMapping("/slots")
    public ResponseEntity<List<SlotDisponivel>> calcularSlots(
            @PathVariable UUID empresaId,
            @RequestParam UUID servicoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(
                disponibilidadeService.calcularSlotsDisponiveis(empresaId, servicoId, data));
    }
}