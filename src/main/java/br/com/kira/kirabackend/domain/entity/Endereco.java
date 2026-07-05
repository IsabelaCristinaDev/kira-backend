package br.com.kira.kirabackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Endereco {

    @Column(name = "endereco_cep", length = 8)
    private String cep;

    @Column(name = "endereco_logradouro", length = 200)
    private String logradouro;

    @Column(name = "endereco_numero", length = 20)
    private String numero;

    @Column(name = "endereco_complemento", length = 100)
    private String complemento;

    @Column(name = "endereco_bairro", length = 100)
    private String bairro;

    @Column(name = "endereco_cidade", length = 100)
    private String cidade;

    @Column(name = "endereco_estado", length = 2)
    private String estado;
}