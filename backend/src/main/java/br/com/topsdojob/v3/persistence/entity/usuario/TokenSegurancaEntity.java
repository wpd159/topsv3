package br.com.topsdojob.v3.persistence.entity.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "token_seguranca")
public class TokenSegurancaEntity {
    protected TokenSegurancaEntity() {}

    @Id private UUID id;
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;
    @Column(nullable = false) private String tipo;
    @Column(name = "token_hash", nullable = false) private String tokenHash;
    @Column(name = "expira_em", nullable = false) private OffsetDateTime expiraEm;
    @Column(nullable = false) private Integer tentativas;
    @Column(name = "consumido_em") private OffsetDateTime consumidoEm;
    @Column(name = "criado_em", nullable = false) private OffsetDateTime criadoEm;

    public UUID getId() { return id; }
    public UUID getUsuarioId() { return usuarioId; }
    public String getTipo() { return tipo; }
    public String getTokenHash() { return tokenHash; }
    public OffsetDateTime getExpiraEm() { return expiraEm; }
    public Integer getTentativas() { return tentativas; }
    public OffsetDateTime getConsumidoEm() { return consumidoEm; }

    public static TokenSegurancaEntity criar(UUID usuarioId, String tipo, String tokenHash,
                                              OffsetDateTime expiraEm, OffsetDateTime agora) {
        TokenSegurancaEntity entity = new TokenSegurancaEntity();
        entity.id = UUID.randomUUID();
        entity.usuarioId = usuarioId;
        entity.tipo = tipo;
        entity.tokenHash = tokenHash;
        entity.expiraEm = expiraEm;
        entity.tentativas = 0;
        entity.criadoEm = agora;
        return entity;
    }

    public void registrarTentativa() { tentativas++; }
    public void consumir(OffsetDateTime agora) { consumidoEm = agora; }
}
