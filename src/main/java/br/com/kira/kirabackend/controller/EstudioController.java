package br.com.kira.kirabackend.controller;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.domain.enums.OrdenacaoBusca;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.request.BuscaEstudioFiltro;
import br.com.kira.kirabackend.dto.response.AvaliacaoResponse;
import br.com.kira.kirabackend.dto.response.EstudioBuscaResponse;
import br.com.kira.kirabackend.dto.response.EstudioResponse;
import br.com.kira.kirabackend.dto.response.FuncionariaPublicaResponse;
import br.com.kira.kirabackend.dto.response.PaginaResponse;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.service.AvaliacaoService;
import br.com.kira.kirabackend.service.BuscaEstudioService;
import br.com.kira.kirabackend.service.EstudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/studios")
public class EstudioController {

    private final EstudioService estudioService;
    private final AvaliacaoService avaliacaoService;
    private final BuscaEstudioService buscaEstudioService;

    @GetMapping
    public ResponseEntity<List<EstudioResponse>> listarTodos() {
        return ResponseEntity.ok(estudioService.listarTodos());
    }

    // Rota pública. Com login de cliente, a distância vem das coordenadas do perfil dela.
    @GetMapping("/pesquisa")
    public ResponseEntity<PaginaResponse<EstudioBuscaResponse>> pesquisar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) TipoEstabelecimento tipo,
            @RequestParam(required = false) String servico,
            @RequestParam(required = false) CategoriaServico categoria,
            @RequestParam(required = false) BigDecimal precoMaximo,
            @RequestParam(required = false) Double notaMinima,
            @RequestParam(required = false) Double distanciaMaximaKm,
            @RequestParam(required = false) OrdenacaoBusca ordenacao,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        BuscaEstudioFiltro filtro = new BuscaEstudioFiltro(
                texto, cidade, tipo, servico, categoria, precoMaximo, notaMinima, distanciaMaximaKm, ordenacao);
        return ResponseEntity.ok(buscaEstudioService.buscar(filtro, usuario, pagina, tamanho));
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
    public ResponseEntity<List<FuncionariaPublicaResponse>> listarFuncionarias(
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
    }

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