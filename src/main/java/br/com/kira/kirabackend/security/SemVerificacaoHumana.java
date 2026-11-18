package br.com.kira.kirabackend.security;

import org.springframework.stereotype.Component;

/** Enquanto não há CAPTCHA: a verificação extra sempre aprova. A proteção fica por conta do atraso e dos limites. */
@Component
public class SemVerificacaoHumana implements VerificacaoHumana {

    @Override
    public boolean aprovada(String ip, String email) {
        return true;
    }
}
