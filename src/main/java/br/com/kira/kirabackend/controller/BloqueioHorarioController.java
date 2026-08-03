package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.BloqueioHorarioRequest;
import br.com.kira.kirabackend.dto.response.BloqueioHorarioResponse;
import br.com.kira.kirabackend.service.BloqueioHorarioService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/funcionarias/{funcionariaId}/bloqueios")
public class BloqueioHorarioController {

    private final BloqueioHorarioService bloqueioHorarioService;

    @PostMapping
    public ResponseEntity<BloqueioHorarioResponse> cadastrar(
            @PathVariable UUID funcionariaId,
            @RequestBody @Valid BloqueioHorarioRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bloqueioHorarioService.cadastrar(funcionariaId, request));
    }

    @GetMapping
    public ResponseEntity<List<BloqueioHorarioResponse>> listar(
            @PathVariable UUID funcionariaId) {
        return ResponseEntity.ok(bloqueioHorarioService.listarPorFuncionaria(funcionariaId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(
            @PathVariable UUID funcionariaId,
            @PathVariable UUID id) {
        bloqueioHorarioService.remover(id);
        return ResponseEntity.noContent().build();
    }
}