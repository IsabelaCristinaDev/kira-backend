package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.ClientePerfilRequest;
import br.com.kira.kirabackend.dto.request.EmpresaPerfilRequest;
import br.com.kira.kirabackend.dto.response.ClientePerfilResponse;
import br.com.kira.kirabackend.dto.response.EmpresaPerfilResponse;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.PerfilService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
public class PerfilController {

    private final PerfilService perfilService;
    private final AcessoEmpresa acessoEmpresa;

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
