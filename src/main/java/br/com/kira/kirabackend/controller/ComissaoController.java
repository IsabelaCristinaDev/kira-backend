package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.domain.entity.ComissaoAgendamento;
import br.com.kira.kirabackend.service.ComissaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comissoes")
@RequiredArgsConstructor
public class ComissaoController {

    private final ComissaoService comissaoService;
    private final AcessoEmpresa acessoEmpresa;

    @GetMapping("/funcionaria/{funcionariaId}")
    public ResponseEntity<List<ComissaoAgendamento>> listarPorFuncionaria(
            @PathVariable UUID funcionariaId,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirFuncionariaDaEmpresa(funcionariaId, usuario);
        return ResponseEntity.ok(comissaoService.listarPorFuncionaria(funcionariaId));
    }

    @GetMapping("/funcionaria/{funcionariaId}/total")
    public ResponseEntity<BigDecimal> totalComissao(
            @PathVariable UUID funcionariaId,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirFuncionariaDaEmpresa(funcionariaId, usuario);
        return ResponseEntity.ok(comissaoService.totalComissaoFuncionaria(funcionariaId));
    }
}