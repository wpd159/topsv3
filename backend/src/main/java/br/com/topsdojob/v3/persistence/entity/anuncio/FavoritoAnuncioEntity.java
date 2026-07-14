package br.com.topsdojob.v3.persistence.entity.anuncio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "favorito_anuncio")
public class FavoritoAnuncioEntity {

    protected FavoritoAnuncioEntity() {
    }

    public static FavoritoAnuncioEntity criar(UUID usuarioId, UUID anuncioId, OffsetDateTime criadoEm) {
        FavoritoAnuncioEntity favorito = new FavoritoAnuncioEntity();
        favorito.id = UUID.randomUUID();
        favorito.usuarioId = usuarioId;
        favorito.anuncioId = anuncioId;
        favorito.criadoEm = criadoEm;
        return favorito;
    }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "anuncio_id", nullable = false)
    private UUID anuncioId;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    public UUID getId() {
        return id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID getAnuncioId() {
        return anuncioId;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
