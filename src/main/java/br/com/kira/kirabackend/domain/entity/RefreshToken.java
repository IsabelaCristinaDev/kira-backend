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
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Só o SHA-256 do token: quem lê o banco não consegue renovar a sessão de ninguém.
    // A coluna continua "token" para o Hibernate (ddl-auto: update) não criar uma coluna nova
    @Column(name = "token", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    @Column(nullable = false)
    private Boolean revogado = false;

    public boolean isExpirado() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT).isAfter(dataExpiracao);
    }

    public boolean isValido() {
        return !isExpirado() && !Boolean.TRUE.equals(revogado);
    }
}
