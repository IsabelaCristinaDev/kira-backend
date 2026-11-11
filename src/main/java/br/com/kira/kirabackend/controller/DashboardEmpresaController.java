package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.dto.response.DashboardEmpresaResponse;
import br.com.kira.kirabackend.service.DashboardEmpresaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/empresas/{empresaId}/dashboard")
public class DashboardEmpresaController {

    private final DashboardEmpresaService dashboardEmpresaService;
    private final AcessoEmpresa acessoEmpresa;

    @GetMapping
    public ResponseEntity<DashboardEmpresaResponse> gerar(
            @PathVariable UUID empresaId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        return ResponseEntity.ok(dashboardEmpresaService.gerar(empresaId, dataInicio, dataFim));
    }
}
