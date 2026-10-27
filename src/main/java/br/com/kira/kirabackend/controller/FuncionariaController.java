package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.dto.request.FuncionariaRequest;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.service.FuncionariaService;
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
@RequestMapping("/api/empresas/{empresaId}/funcionarias")

public class FuncionariaController {

    private final FuncionariaService funcionariaService;
    private final AcessoEmpresa acessoEmpresa;

    @PostMapping
    public ResponseEntity<FuncionariaResponse> cadastrar(
            @PathVariable UUID empresaId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid FuncionariaRequest request) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(funcionariaService.cadastrar(empresaId, request));
    }

    @GetMapping
    public ResponseEntity<List<FuncionariaResponse>> listar(
            @PathVariable UUID empresaId) {
        return ResponseEntity.ok(funcionariaService.listarPorEmpresa(empresaId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FuncionariaResponse> atualizar(
            @PathVariable UUID empresaId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid FuncionariaRequest request) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        return ResponseEntity.ok(funcionariaService.atualizar(empresaId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(
            @PathVariable UUID empresaId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        funcionariaService.desativar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}