package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.dto.request.ServicoRequest;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.service.ServicoService;
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
@RequestMapping("/api/empresas/{empresaId}/servicos")
public class ServicoController {


    private final ServicoService servicoService;
    private final AcessoEmpresa acessoEmpresa;

    @PostMapping
    public ResponseEntity<ServicoResponse> cadastrar(
            @PathVariable UUID empresaId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ServicoRequest request) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(servicoService.cadastrar(empresaId, request));
    }

    @GetMapping
    public ResponseEntity<List<ServicoResponse>> listar(
            @PathVariable UUID empresaId) {
        return ResponseEntity.ok(servicoService.listarPorEmpresa(empresaId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServicoResponse> atualizar(
            @PathVariable UUID empresaId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ServicoRequest request) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        return ResponseEntity.ok(servicoService.atualizar(empresaId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(
            @PathVariable UUID empresaId,
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        servicoService.desativar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}