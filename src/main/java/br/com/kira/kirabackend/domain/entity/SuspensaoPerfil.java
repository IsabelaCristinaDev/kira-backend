package br.com.kira.kirabackend.domain.entity;

import br.com.kira.kirabackend.util.KiraTimeZone;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "suspensao_perfil")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuspensaoPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "denuncia_id", nullable = false)
    private Denuncia denuncia;

    @Column(nullable = false)
    private Boolean preventiva = false;

    @Column(nullable = false)
    private Boolean ativa = true;

    @Column(name = "data_inicio", nullable = false, updatable = false)
    private LocalDateTime dataInicio;

    @Column(name = "data_fim")
    private LocalDateTime dataFim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revogada_por_id")
    private Usuario revogadaPor;

    @PrePersist
    protected void aoPersistir() {
        this.dataInicio = LocalDateTime.now(KiraTimeZone.DEFAULT);
        if (this.ativa == null) {
            this.ativa = true;
        }
    }
}
