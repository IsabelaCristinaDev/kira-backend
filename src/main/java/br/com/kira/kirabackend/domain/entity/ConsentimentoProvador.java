package br.com.kira.kirabackend.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consentimento LGPD para o Provador virtual. Cada pessoa tem um único registro, que guarda
 * a versão do termo aceita, quando foi aceita e quando foi revogada (se foi).
 */
@Entity
@Table(name = "consentimento_provador", uniqueConstraints = @UniqueConstraint(
        name = "uk_consentimento_provador_usuario",
        columnNames = "usuario_id"))
@Getter
@Setter
@NoArgsConstructor
public class ConsentimentoProvador {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 20)
    private String versao;

    @Column(name = "aceito_em", nullable = false)
    private LocalDateTime aceitoEm;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;

    /** Vale só se não foi revogado e se a versão aceita é a do termo atual. */
    public boolean estaAtivo(String versaoAtual) {
        return revogadoEm == null && versaoAtual.equals(versao);
    }
}
