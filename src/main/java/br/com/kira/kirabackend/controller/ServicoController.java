package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.ServicoRequest;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.service.ServicoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/empresas/{empresaId}/servicos")
public class ServicoController {

    @Autowired
    private ServicoService servicoService;

    @PostMapping
    public ResponseEntity<ServicoResponse> cadastrar(
            @PathVariable UUID empresaId,
            @RequestBody @Valid ServicoRequest request) {
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
            @RequestBody @Valid ServicoRequest request) {
        return ResponseEntity.ok(servicoService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(
            @PathVariable UUID empresaId,
            @PathVariable UUID id) {
        servicoService.desativar(id);
        return ResponseEntity.noContent().build();
    }
}