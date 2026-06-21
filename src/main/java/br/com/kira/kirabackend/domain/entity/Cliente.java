package br.com.kira.kirabackend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@DiscriminatorValue("CLIENTE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder

public class Cliente extends Usuario{

    @Column(nullable=false, unique=true , length= 14)
    private String cpf;

    @Column(name="data_nascimento")
    private LocalDate dataNascimento;

}
