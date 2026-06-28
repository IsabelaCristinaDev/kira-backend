package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.AvaliacaoRequest;
import br.com.kira.kirabackend.dto.response.AvaliacaoResponse;
import br.com.kira.kirabackend.service.AvaliacaoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/avaliacoes")
public class AvaliacaoController {

    @Autowired
    private AvaliacaoService avaliacaoService;

    @PostMapping("/cliente")
    public ResponseEntity<AvaliacaoResponse> avaliarComoCliente(
            @RequestBody @Valid AvaliacaoRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(avaliacaoService.avaliarComoCliente(request));
    }

    @PostMapping("/empresa")
    public ResponseEntity<AvaliacaoResponse> avaliarComoEmpresa(
            @RequestBody @Valid AvaliacaoRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(avaliacaoService.avaliarComoEmpresa(request));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<AvaliacaoResponse>> listarAvaliacoesEmpresa(
            @PathVariable UUID empresaId) {
        return ResponseEntity.ok(avaliacaoService.listarAvaliacoesEmpresa(empresaId));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<AvaliacaoResponse>> listarAvaliacoesCliente(
            @PathVariable UUID clienteId) {
        return ResponseEntity.ok(avaliacaoService.listarAvaliacoesCliente(clienteId));
    }

    @GetMapping("/empresa/{empresaId}/media")
    public ResponseEntity<Double> mediaAvaliacaoEmpresa(
            @PathVariable UUID empresaId) {
        return ResponseEntity.ok(avaliacaoService.mediaAvaliacaoEmpresa(empresaId));
    }
}