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
@Table(name = "token_recuperacao_senha")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenRecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    @Column(nullable = false)
    private Boolean usado = false;

    public boolean isExpirado() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT).isAfter(dataExpiracao);
    }
}