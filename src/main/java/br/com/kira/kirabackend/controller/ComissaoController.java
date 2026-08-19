package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.ComissaoAgendamento;
import br.com.kira.kirabackend.service.ComissaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comissoes")
public class ComissaoController {

    @Autowired
    private ComissaoService comissaoService;

    @GetMapping("/funcionaria/{funcionariaId}")
    public ResponseEntity<List<ComissaoAgendamento>> listarPorFuncionaria(
            @PathVariable UUID funcionariaId) {
        return ResponseEntity.ok(comissaoService.listarPorFuncionaria(funcionariaId));
    }

    @GetMapping("/funcionaria/{funcionariaId}/total")
    public ResponseEntity<BigDecimal> totalComissao(
            @PathVariable UUID funcionariaId) {
        return ResponseEntity.ok(comissaoService.totalComissaoFuncionaria(funcionariaId));
    }
}