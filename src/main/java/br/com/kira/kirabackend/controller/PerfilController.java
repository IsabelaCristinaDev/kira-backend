package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.ClientePerfilRequest;
import br.com.kira.kirabackend.dto.request.EmpresaPerfilRequest;
import br.com.kira.kirabackend.dto.request.ExclusaoContaRequest;
import br.com.kira.kirabackend.dto.request.PreferenciasRequest;
import br.com.kira.kirabackend.dto.response.ClientePerfilResponse;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse;
import br.com.kira.kirabackend.dto.response.EmpresaPerfilResponse;
import br.com.kira.kirabackend.dto.response.FotoPerfilResponse;
import br.com.kira.kirabackend.dto.response.PreferenciasResponse;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.DadosPessoaisService;
import br.com.kira.kirabackend.service.ExclusaoContaService;
import br.com.kira.kirabackend.service.FotoPerfilService;
import br.com.kira.kirabackend.service.PerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
public class PerfilController {

    private final PerfilService perfilService;
    private final AcessoEmpresa acessoEmpresa;
    private final FotoPerfilService fotoPerfilService;
    private final DadosPessoaisService dadosPessoaisService;
    private final ExclusaoContaService exclusaoContaService;

    // LGPD: cópia de todos os dados pessoais da conta logada
    @GetMapping("/api/perfil/dados")
    public ResponseEntity<DadosPessoaisResponse> exportarDados(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(dadosPessoaisService.exportar(usuario.getId()));
    }

    // LGPD: exclusão da conta logada, confirmada com a senha atual
    @DeleteMapping("/api/perfil")
    public ResponseEntity<Void> excluirConta(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ExclusaoContaRequest request) {
        exclusaoContaService.excluir(usuario.getId(), request.senha());
        return ResponseEntity.noContent().build();
    }

    // A foto é sempre a da conta logada, por isso a rota não recebe id
    @PostMapping("/api/perfil/foto")
    public ResponseEntity<FotoPerfilResponse> definirFoto(
            @RequestParam("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(fotoPerfilService.definir(usuario.getId(), arquivo));
    }

    @DeleteMapping("/api/perfil/foto")
    public ResponseEntity<Void> removerFoto(@AuthenticationPrincipal Usuario usuario) {
        fotoPerfilService.remover(usuario.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/clientes/{clienteId}/perfil")
    public ResponseEntity<ClientePerfilResponse> obterCliente(
            @PathVariable UUID clienteId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(perfilService.obterCliente(clienteId, usuario.getId()));
    }

    @PutMapping("/api/clientes/{clienteId}/perfil")
    public ResponseEntity<ClientePerfilResponse> atualizarCliente(
            @PathVariable UUID clienteId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid ClientePerfilRequest request) {
        return ResponseEntity.ok(perfilService.atualizarCliente(clienteId, usuario.getId(), request));
    }

    @GetMapping("/api/clientes/{clienteId}/preferencias")
    public ResponseEntity<PreferenciasResponse> obterPreferencias(
            @PathVariable UUID clienteId,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(perfilService.obterPreferencias(clienteId, usuario.getId()));
    }

    @PutMapping("/api/clientes/{clienteId}/preferencias")
    public ResponseEntity<PreferenciasResponse> atualizarPreferencias(
            @PathVariable UUID clienteId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid PreferenciasRequest request) {
        return ResponseEntity.ok(perfilService.atualizarPreferencias(clienteId, usuario.getId(), request));
    }

    @GetMapping("/api/empresas/{empresaId}/perfil")
    public ResponseEntity<EmpresaPerfilResponse> obterEmpresa(
            @PathVariable UUID empresaId,
            @AuthenticationPrincipal Usuario usuario) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        return ResponseEntity.ok(perfilService.obterEmpresa(empresaId));
    }

    @PutMapping("/api/empresas/{empresaId}/perfil")
    public ResponseEntity<EmpresaPerfilResponse> atualizarEmpresa(
            @PathVariable UUID empresaId,
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid EmpresaPerfilRequest request) {
        acessoEmpresa.exigirEmpresa(empresaId, usuario);
        return ResponseEntity.ok(perfilService.atualizarEmpresa(empresaId, request));
    }
}
