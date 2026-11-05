package br.com.kira.kirabackend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Quantas prévias do Provador a pessoa gerou no dia. Cada pessoa tem uma única linha, reaproveitada
 * quando o dia muda, então não fica histórico de uso nem a tabela cresce com o tempo.
 */
@Entity
@Table(name = "uso_provador")
@Getter
@Setter
@NoArgsConstructor
public class UsoProvador {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private LocalDate dia;

    @Column(nullable = false)
    private int quantidade;
}
