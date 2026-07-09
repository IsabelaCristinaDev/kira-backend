package br.com.kira.kirabackend.controller;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.response.AvaliacaoResponse;
import br.com.kira.kirabackend.dto.response.EstudioResponse;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.service.AvaliacaoService;
import br.com.kira.kirabackend.service.EstudioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/studios")
public class EstudioController {

    @Autowired
    private EstudioService estudioService;

    @Autowired
    private AvaliacaoService avaliacaoService;

    @GetMapping
    public ResponseEntity<List<EstudioResponse>> listarTodos() {
        return ResponseEntity.ok(estudioService.listarTodos());
    }

    @GetMapping("/busca")
    public ResponseEntity<List<EstudioResponse>> buscarPorNome(
            @RequestParam String nome) {
        return ResponseEntity.ok(estudioService.buscarPorNome(nome));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstudioResponse> buscarPorId(
            @PathVariable UUID id) {
        return ResponseEntity.ok(estudioService.buscarPorId(id));
    }

    @GetMapping("/{id}/funcionarias")
    public ResponseEntity<List<FuncionariaResponse>> listarFuncionarias(
            @PathVariable UUID id) {
        return ResponseEntity.ok(estudioService.listarFuncionarias(id));
    }

    @GetMapping("/{id}/servicos")
    public ResponseEntity<List<ServicoResponse>> listarServicos(
            @PathVariable UUID id) {
        return ResponseEntity.ok(estudioService.listarServicos(id));
    }

    @GetMapping("/{id}/avaliacoes")
    public ResponseEntity<List<AvaliacaoResponse>> listarAvaliacoes(
            @PathVariable UUID id) {
        return ResponseEntity.ok(avaliacaoService.listarAvaliacoesEmpresa(id));
    }

    @GetMapping("/{id}/media")
    public ResponseEntity<Double> mediaAvaliacoes(
            @PathVariable UUID id) {
        return ResponseEntity.ok(avaliacaoService.mediaAvaliacaoEmpresa(id));
    } // <-- A chave fechava aqui!

    @GetMapping("/tipo")
    public ResponseEntity<List<EstudioResponse>> buscarPorTipo(
            @RequestParam TipoEstabelecimento tipo) {
        return ResponseEntity.ok(estudioService.buscarPorTipo(tipo));
    }

    @GetMapping("/cidade")
    public ResponseEntity<List<EstudioResponse>> buscarPorCidade(
            @RequestParam String cidade) {
        return ResponseEntity.ok(estudioService.buscarPorCidade(cidade));
    }
}