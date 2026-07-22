package br.com.topsdojob.v3.persistence.entity.midia;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "arquivo_midia")
public class ArquivoMidiaEntity {
  protected ArquivoMidiaEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "storage_provider")
  private String storageProvider;

  @Column(name = "bucket")
  private String bucket;

  @Column(name = "chave_objeto")
  private String chaveObjeto;

  @Column(name = "nome_original")
  private String nomeOriginal;

  @Column(name = "mime_type")
  private String mimeType;

  @Column(name = "tamanho_bytes")
  private Long tamanhoBytes;

  @Column(name = "largura")
  private Integer largura;

  @Column(name = "altura")
  private Integer altura;

  @Column(name = "duracao_ms")
  private Integer duracaoMs;

  @Column(name = "sha256")
  private String sha256;

  @Column(name = "etag")
  private String etag;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_arquivo")
  private StatusArquivoMidia statusArquivo;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "pipeline_versao")
  private Integer pipelineVersao;

  @Column(name = "marca_dagua_versao")
  private String marcaDaguaVersao;

  @Column(name = "processado_em")
  private OffsetDateTime processadoEm;

  @Column(name = "sha256_origem")
  private String sha256Origem;

  public UUID getId() {
    return id;
  }

  public String getStorageProvider() {
    return storageProvider;
  }

  public String getBucket() {
    return bucket;
  }

  public String getChaveObjeto() {
    return chaveObjeto;
  }

  public String getNomeOriginal() {
    return nomeOriginal;
  }

  public String getMimeType() {
    return mimeType;
  }

  public Long getTamanhoBytes() {
    return tamanhoBytes;
  }

  public Integer getLargura() {
    return largura;
  }

  public Integer getAltura() {
    return altura;
  }

  public Integer getDuracaoMs() {
    return duracaoMs;
  }

  public String getSha256() {
    return sha256;
  }

  public String getEtag() {
    return etag;
  }

  public StatusArquivoMidia getStatusArquivo() {
    return statusArquivo;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public Integer getPipelineVersao() {
    return pipelineVersao;
  }

  public String getMarcaDaguaVersao() {
    return marcaDaguaVersao;
  }

  public OffsetDateTime getProcessadoEm() {
    return processadoEm;
  }

  public String getSha256Origem() {
    return sha256Origem;
  }

  public void aplicarDecisao(StatusArquivoMidia statusArquivo) {
    this.statusArquivo = statusArquivo;
  }

  public void moverNoStorage(String bucket, String chaveObjeto) {
    this.storageProvider = "R2";
    this.bucket = bucket;
    this.chaveObjeto = chaveObjeto;
  }

  public void registrarProcessamento(
      int pipelineVersao,
      String marcaDaguaVersao,
      OffsetDateTime processadoEm,
      String sha256Origem) {
    if (pipelineVersao < 1 || marcaDaguaVersao == null || marcaDaguaVersao.isBlank()
        || processadoEm == null || sha256Origem == null || !sha256Origem.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Metadados do processamento de foto invalidos");
    }
    this.pipelineVersao = pipelineVersao;
    this.marcaDaguaVersao = marcaDaguaVersao;
    this.processadoEm = processadoEm;
    this.sha256Origem = sha256Origem;
  }

  public static ArquivoMidiaEntity criarUploadPendente(
      UUID id,
      String storageProvider,
      String bucket,
      String chaveObjeto,
      String nomeOriginal,
      String mimeType,
      long tamanhoBytes,
      Integer largura,
      Integer altura,
      Integer duracaoMs,
      String sha256,
      OffsetDateTime criadoEm) {
    ArquivoMidiaEntity entity = new ArquivoMidiaEntity();
    entity.id = id;
    entity.storageProvider = storageProvider;
    entity.bucket = bucket;
    entity.chaveObjeto = chaveObjeto;
    entity.nomeOriginal = nomeOriginal;
    entity.mimeType = mimeType;
    entity.tamanhoBytes = tamanhoBytes;
    entity.largura = largura;
    entity.altura = altura;
    entity.duracaoMs = duracaoMs;
    entity.sha256 = sha256;
    entity.etag = null;
    entity.statusArquivo = StatusArquivoMidia.PENDENTE;
    entity.criadoEm = criadoEm;
    return entity;
  }

  public static ArquivoMidiaEntity criarFixtureHomologacao(
      UUID id,
      String chaveObjeto,
      String mimeType,
      StatusArquivoMidia statusArquivo,
      OffsetDateTime criadoEm) {
    ArquivoMidiaEntity entity = new ArquivoMidiaEntity();
    entity.id = id;
    entity.storageProvider = "LOCAL_MOCK";
    entity.bucket = "topsv3-hml-fixture";
    entity.chaveObjeto = chaveObjeto;
    entity.nomeOriginal = null;
    entity.mimeType = mimeType;
    entity.tamanhoBytes = 1024L;
    entity.largura = mimeType.startsWith("image/") ? 1080 : 720;
    entity.altura = 1920;
    entity.duracaoMs = mimeType.startsWith("video/") ? 15000 : null;
    entity.sha256 = null;
    entity.etag = null;
    entity.statusArquivo = statusArquivo;
    entity.criadoEm = criadoEm;
    return entity;
  }

}
