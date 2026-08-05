package br.com.topsdojob.v3.persistence.entity.midia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "story_selecao_administrativa")
public class StorySelecaoAdministrativaEntity {

    private static final java.time.Duration DURACAO_PADRAO = java.time.Duration.ofHours(24);

    protected StorySelecaoAdministrativaEntity() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "story_selecao_administrativa_seq")
    @SequenceGenerator(
            name = "story_selecao_administrativa_seq",
            sequenceName = "story_selecao_administrativa_id_seq",
            allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "anuncio_id")
    private UUID anuncioId;

    @Column(name = "ativa")
    private boolean ativa;

    @Column(name = "ativado_por")
    private UUID ativadoPor;

    @Column(name = "ativado_em")
    private OffsetDateTime ativadoEm;

    @Column(name = "expira_em")
    private OffsetDateTime expiraEm;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "criado_em")
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em")
    private OffsetDateTime atualizadoEm;

    @Version
    @Column(name = "versao")
    private Integer versao;

    public Long getId() {
        return id;
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

    public OffsetDateTime getExpiraEm() {
        return expiraEm != null || ativadoEm == null ? expiraEm : ativadoEm.plus(DURACAO_PADRAO);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
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

    public void desativar(OffsetDateTime agora) {
        this.ativa = false;
        this.atualizadoEm = agora;
    }
}
