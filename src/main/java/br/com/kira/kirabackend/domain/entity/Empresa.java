package br.com.kira.kirabackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@DiscriminatorValue("EMPRESA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder

public class Empresa  extends Usuario {

    @Column(nullable= false, unique = true , length = 18 )
    private String cnpj;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 200)
    private String endereco;
}

