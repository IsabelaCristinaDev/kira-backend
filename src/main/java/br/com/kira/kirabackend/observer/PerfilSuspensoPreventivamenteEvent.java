package br.com.kira.kirabackend.observer;

public record PerfilSuspensoPreventivamenteEvent(
        String email,
        String nome
) {}
