package br.com.kira.kirabackend.observer;

import java.util.List;

/** Conta excluída a pedido da própria pessoa. Leva os endereços das fotos que precisam sair do disco. */
public record ContaExcluidaEvent(List<String> arquivos) {}
