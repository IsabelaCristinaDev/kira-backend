package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.response.FotoPortfolioResponse;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final AcessoEmpresa acessoEmpresa;

    // Público, como o resto do perfil do estúdio
    @GetMapping("/api/studios/{empresaId}/portfolio")
    public ResponseEntity<List<FotoPortfolioResponse>> listar(@PathVariable UUID empresaId) {
        return ResponseEntity.ok(portfolioService.listar(empresaId));
    }

    @PostMapping("/api/empresas/{empresaId}/portfolio")
    public ResponseEntity<FotoPortfolioResponse> adicionar(
            @PathVariable UUID empresaId,
            @RequestParam("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(portfolioService.adicionar(empresaId, arquivo));
    }

    @DeleteMapping("/api/empresas/{empresaId}/portfolio/{fotoId}")
    public ResponseEntity<Void> remover(
            @PathVariable UUID empresaId,
            @PathVariable UUID fotoId,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        portfolioService.remover(empresaId, fotoId);
        return ResponseEntity.noContent().build();
    }
}
