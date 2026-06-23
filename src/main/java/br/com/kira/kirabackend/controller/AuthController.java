package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/registro/cliente")
    public ResponseEntity<Void> registrarCliente(
            @Valid @RequestBody ClienteRegistroRequest request) {
        authService.registrarCliente(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/registro/empresa")
    public ResponseEntity<Void> registrarEmpresa(
            @Valid @RequestBody EmpresaRegistroRequest request) {
        authService.registrarEmpresa(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}