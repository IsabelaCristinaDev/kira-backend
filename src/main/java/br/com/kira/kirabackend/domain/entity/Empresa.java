package br.com.kira.kirabackend.domain.entity;

import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "empresa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Empresa extends Usuario {

    @Column(unique = true, length = 18)
    private String cnpj;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 200)
    private String endereco;

    @Column(name = "tipo_estabelecimento", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TipoEstabelecimento tipoEstabelecimento;
}
