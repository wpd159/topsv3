package br.com.topsdojob.v3.persistence.entity.midia;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusStoryAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ModoConteudoStory;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemEncerramentoStory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "story_anuncio")
public class StoryAnuncioEntity {
  protected StoryAnuncioEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_midia_id")
  private UUID anuncioMidiaId;

  @Column(name = "arquivo_midia_id")
  private UUID arquivoMidiaId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Enumerated(EnumType.STRING)
  @Column(name = "modo_conteudo")
  private ModoConteudoStory modoConteudo;

  @Column(name = "ativacao_beneficio_id")
  private UUID ativacaoBeneficioId;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "request_fingerprint")
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusStoryAnuncio status;

  @Column(name = "inicio_em")
  private OffsetDateTime inicioEm;

  @Column(name = "fim_em")
  private OffsetDateTime fimEm;

  @Column(name = "ordem")
  private Integer ordem;

  @Column(name = "criado_por")
  private UUID criadoPor;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Column(name = "encerrado_em")
  private OffsetDateTime encerradoEm;

  @Column(name = "encerrado_por")
  private UUID encerradoPor;

  @Enumerated(EnumType.STRING)
  @Column(name = "origem_encerramento")
  private OrigemEncerramentoStory origemEncerramento;

  @Column(name = "motivo_encerramento")
  private String motivoEncerramento;

  @Column(name = "descricao_encerramento")
  private String descricaoEncerramento;

  @Column(name = "direito_preservado")
  private boolean direitoPreservado;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioMidiaId() {
    return anuncioMidiaId;
  }

  public UUID getArquivoMidiaId() {
    return arquivoMidiaId;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public ModoConteudoStory getModoConteudo() {
    return modoConteudo;
  }

  public ModoConteudoStory getModoConteudoEfetivo() {
    return modoConteudo == null ? ModoConteudoStory.MIDIA_UPLOAD : modoConteudo;
  }

  public UUID getAtivacaoBeneficioId() {
    return ativacaoBeneficioId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public StatusStoryAnuncio getStatus() {
    return status;
  }

  public OffsetDateTime getInicioEm() {
    return inicioEm;
  }

  public OffsetDateTime getFimEm() {
    return fimEm;
  }

  public Integer getOrdem() {
    return ordem;
  }

  public UUID getCriadoPor() {
    return criadoPor;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

  public OffsetDateTime getEncerradoEm() {
    return encerradoEm;
  }

  public UUID getEncerradoPor() {
    return encerradoPor;
  }

  public OrigemEncerramentoStory getOrigemEncerramento() {
    return origemEncerramento;
  }

  public String getMotivoEncerramento() {
    return motivoEncerramento;
  }

  public String getDescricaoEncerramento() {
    return descricaoEncerramento;
  }

  public boolean isDireitoPreservado() {
    return direitoPreservado;
  }

  public boolean estaPublicamenteAtivo(OffsetDateTime agora) {
    return agora != null
        && status == StatusStoryAnuncio.PUBLICADO
        && encerradoEm == null
        && (inicioEm == null || !inicioEm.isAfter(agora))
        && (fimEm == null || fimEm.isAfter(agora));
  }

  public boolean suspenderPorBloqueio(OffsetDateTime agora) {
    if (status == StatusStoryAnuncio.EXPIRADO || status == StatusStoryAnuncio.REMOVIDO) {
      return false;
    }
    status = StatusStoryAnuncio.EXPIRADO;
    atualizadoEm = agora;
    return true;
  }

  public boolean expirarSeVencido(OffsetDateTime agora) {
    if (status != StatusStoryAnuncio.PUBLICADO
        || fimEm == null
        || fimEm.isAfter(agora)) {
      return false;
    }
    status = StatusStoryAnuncio.EXPIRADO;
    atualizadoEm = agora;
    return true;
  }

  public static StoryAnuncioEntity criarAnuncio(
      UUID id,
      UUID anuncioId,
      UUID ativacaoBeneficioId,
      String idempotencyKey,
      String requestFingerprint,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      UUID criadoPor) {
    if (id == null || anuncioId == null
        || ativacaoBeneficioId == null || criadoPor == null
        || idempotencyKey == null || idempotencyKey.isBlank()
        || requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")
        || inicioEm == null || fimEm == null || !fimEm.isAfter(inicioEm)) {
      throw new IllegalArgumentException("Dados do Story de anuncio invalidos");
    }
    StoryAnuncioEntity entity = new StoryAnuncioEntity();
    entity.id = id;
    entity.anuncioId = anuncioId;
    entity.anuncioMidiaId = null;
    entity.arquivoMidiaId = null;
    entity.modoConteudo = ModoConteudoStory.ANUNCIO;
    entity.ativacaoBeneficioId = ativacaoBeneficioId;
    entity.idempotencyKey = idempotencyKey;
    entity.requestFingerprint = requestFingerprint;
    entity.status = StatusStoryAnuncio.PUBLICADO;
    entity.inicioEm = inicioEm;
    entity.fimEm = fimEm;
    entity.ordem = 0;
    entity.criadoPor = criadoPor;
    entity.criadoEm = inicioEm;
    entity.atualizadoEm = inicioEm;
    entity.direitoPreservado = false;
    return entity;
  }

  public static StoryAnuncioEntity criarMidiaUpload(
      UUID id,
      UUID arquivoMidiaId,
      UUID ativacaoBeneficioId,
      String idempotencyKey,
      String requestFingerprint,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      UUID criadoPor) {
    if (id == null || arquivoMidiaId == null || ativacaoBeneficioId == null || criadoPor == null
        || idempotencyKey == null || idempotencyKey.isBlank()
        || requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")
        || inicioEm == null || fimEm == null || !fimEm.isAfter(inicioEm)) {
      throw new IllegalArgumentException("Dados do Story de midia invalidos");
    }
    StoryAnuncioEntity entity = new StoryAnuncioEntity();
    entity.id = id;
    entity.anuncioId = null;
    entity.anuncioMidiaId = null;
    entity.arquivoMidiaId = arquivoMidiaId;
    entity.modoConteudo = ModoConteudoStory.MIDIA_UPLOAD;
    entity.ativacaoBeneficioId = ativacaoBeneficioId;
    entity.idempotencyKey = idempotencyKey;
    entity.requestFingerprint = requestFingerprint;
    entity.status = StatusStoryAnuncio.PUBLICADO;
    entity.inicioEm = inicioEm;
    entity.fimEm = fimEm;
    entity.ordem = 0;
    entity.criadoPor = criadoPor;
    entity.criadoEm = inicioEm;
    entity.atualizadoEm = inicioEm;
    entity.direitoPreservado = false;
    return entity;
  }

  public boolean encerrar(
      UUID atorId,
      OrigemEncerramentoStory origem,
      String motivo,
      String descricao,
      boolean preservarDireito,
      OffsetDateTime agora) {
    if (status == StatusStoryAnuncio.REMOVIDO && encerradoEm != null) {
      return false;
    }
    if (atorId == null || origem == null || agora == null) {
      throw new IllegalArgumentException("Encerramento do Story invalido");
    }
    status = StatusStoryAnuncio.REMOVIDO;
    encerradoEm = agora;
    encerradoPor = atorId;
    origemEncerramento = origem;
    motivoEncerramento = motivo;
    descricaoEncerramento = descricao;
    direitoPreservado = preservarDireito;
    atualizadoEm = agora;
    return true;
  }

  public static StoryAnuncioEntity criarFixtureHomologacao(
      UUID id,
      UUID anuncioMidiaId,
      OffsetDateTime inicioEm,
      OffsetDateTime fimEm,
      Integer ordem,
      UUID criadoPor,
      OffsetDateTime criadoEm) {
    StoryAnuncioEntity entity = new StoryAnuncioEntity();
    entity.id = id;
    entity.anuncioMidiaId = anuncioMidiaId;
    entity.status = StatusStoryAnuncio.PUBLICADO;
    entity.inicioEm = inicioEm;
    entity.fimEm = fimEm;
    entity.ordem = ordem;
    entity.criadoPor = criadoPor;
    entity.criadoEm = criadoEm;
    entity.atualizadoEm = criadoEm;
    return entity;
  }

}
