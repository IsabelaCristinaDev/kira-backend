package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest data) {
        var loginResponse = authService.login(data);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/registro/cliente")
    public ResponseEntity registrarCliente(@RequestBody @Valid ClienteRegistroRequest data) { // <-- Mudei o nome aqui
        authService.registrarCliente(data);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/registro/empresa")
    public ResponseEntity registrarEmpresa(@RequestBody @Valid EmpresaRegistroRequest data) { // <-- Mudei o nome aqui
        authService.registrarEmpresa(data);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}