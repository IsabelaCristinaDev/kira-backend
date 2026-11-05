package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository.Coordenadas;

import java.util.Optional;

/** Quem transforma um endereço em coordenadas. Hoje é o Nominatim; trocar o serviço não mexe no resto. */
@FunctionalInterface
public interface Geocodificador {

    /**
     * Nunca lança erro por falha do serviço: sem resposta confiável, o cadastro segue sem coordenadas.
     *
     * @return as coordenadas do endereço, ou vazio se não encontrar ou o serviço não responder
     */
    Optional<Coordenadas> localizar(Endereco endereco);
}
