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

  public void aplicarDecisao(StatusArquivoMidia statusArquivo) {
    this.statusArquivo = statusArquivo;
  }

}
