package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.dto.request.BloqueioHorarioRequest;
import br.com.kira.kirabackend.dto.response.BloqueioHorarioResponse;
import br.com.kira.kirabackend.service.BloqueioHorarioService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/funcionarias/{funcionariaId}/bloqueios")
public class BloqueioHorarioController {

    private final BloqueioHorarioService bloqueioHorarioService;
    private final AcessoEmpresa acessoEmpresa;

    @PostMapping
    public ResponseEntity<BloqueioHorarioResponse> cadastrar(
            @PathVariable UUID funcionariaId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid BloqueioHorarioRequest request) {
        acessoEmpresa.exigirFuncionariaDaEmpresa(funcionariaId, usuario);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(bloqueioHorarioService.cadastrar(funcionariaId, request));
    }

    @GetMapping
    public ResponseEntity<List<BloqueioHorarioResponse>> listar(
            @PathVariable UUID funcionariaId,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirFuncionariaDaEmpresa(funcionariaId, usuario);
        return ResponseEntity.ok(bloqueioHorarioService.listarPorFuncionaria(funcionariaId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(
            @PathVariable UUID funcionariaId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirFuncionariaDaEmpresa(funcionariaId, usuario);
        bloqueioHorarioService.remover(funcionariaId, id);
        return ResponseEntity.noContent().build();
    }
}