package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.request.RefreshTokenRequest;
import br.com.kira.kirabackend.dto.request.TrocaSenhaRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.security.IpDoCliente;
import br.com.kira.kirabackend.security.ProtecaoLogin;
import br.com.kira.kirabackend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ProtecaoLogin protecaoLogin;
    private final IpDoCliente ipDoCliente;

    // A proteção envolve o login (como um filtro): limite de tentativas, atraso e aviso ficam no ProtecaoLogin
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest data, HttpServletRequest request) {
        var loginResponse = protecaoLogin.proteger(ipDoCliente.de(request), data.email(), () -> authService.login(data));
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/registro/cliente")
    public ResponseEntity<Void> registrarCliente(@RequestBody @Valid ClienteRegistroRequest data) {
        authService.registrarCliente(data);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/registro/empresa")
    public ResponseEntity<Void> registrarEmpresa(@RequestBody @Valid EmpresaRegistroRequest data) {
        authService.registrarEmpresa(data);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody @Valid RefreshTokenRequest data) {
        var loginResponse = authService.refreshToken(data);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid RefreshTokenRequest data) {
        authService.logout(data);
        return ResponseEntity.noContent().build();
    }

    // Rota protegida: a troca é sempre da conta logada
    @PatchMapping("/senha")
    public ResponseEntity<LoginResponse> trocarSenha(
            @AuthenticationPrincipal Usuario usuario,
            @RequestBody @Valid TrocaSenhaRequest data) {
        return ResponseEntity.ok(authService.trocarSenha(usuario, data));
    }
}
