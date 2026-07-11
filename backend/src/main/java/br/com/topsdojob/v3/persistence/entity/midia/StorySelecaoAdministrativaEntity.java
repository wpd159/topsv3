package br.com.topsdojob.v3.persistence.entity.midia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "story_selecao_administrativa")
public class StorySelecaoAdministrativaEntity {

    public static final short SINGLETON_ID = 1;

    protected StorySelecaoAdministrativaEntity() {
    }

    @Id
    @Column(name = "singleton_id")
    private Short singletonId;

    @Column(name = "anuncio_id")
    private UUID anuncioId;

    @Column(name = "ativa")
    private boolean ativa;

    @Column(name = "ativado_por")
    private UUID ativadoPor;

    @Column(name = "ativado_em")
    private OffsetDateTime ativadoEm;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    @Version
    @Column(name = "versao")
    private Integer versao;

    public Short getSingletonId() {
        return singletonId;
    }

    public UUID getAnuncioId() {
        return anuncioId;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public UUID getAtivadoPor() {
        return ativadoPor;
    }

    public OffsetDateTime getAtivadoEm() {
        return ativadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public Integer getVersao() {
        return versao;
    }

    public static StorySelecaoAdministrativaEntity nova(OffsetDateTime agora) {
        StorySelecaoAdministrativaEntity entity = new StorySelecaoAdministrativaEntity();
        entity.singletonId = SINGLETON_ID;
        entity.ativa = false;
        entity.criadoEm = agora;
        entity.atualizadoEm = agora;
        return entity;
    }

    public void ativar(UUID anuncioId, UUID atorId, OffsetDateTime agora) {
        this.anuncioId = anuncioId;
        this.ativa = true;
        this.ativadoPor = atorId;
        this.ativadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void desativar(OffsetDateTime agora) {
        this.anuncioId = null;
        this.ativa = false;
        this.ativadoPor = null;
        this.ativadoEm = null;
        this.atualizadoEm = agora;
    }
}
