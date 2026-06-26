package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.FuncionariaRequest;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.service.FuncionariaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/empresas/{empresaId}/funcionarias")
public class FuncionariaController {

    @Autowired
    private FuncionariaService funcionariaService;

    @PostMapping
    public ResponseEntity<FuncionariaResponse> cadastrar(
            @PathVariable UUID empresaId,
            @RequestBody @Valid FuncionariaRequest request) {
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
            @RequestBody @Valid FuncionariaRequest request) {
        return ResponseEntity.ok(funcionariaService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(
            @PathVariable UUID empresaId,
            @PathVariable UUID id) {
        funcionariaService.desativar(id);
        return ResponseEntity.noContent().build();
    }
}