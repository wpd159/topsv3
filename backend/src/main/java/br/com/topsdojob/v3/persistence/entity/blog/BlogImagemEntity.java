package br.com.topsdojob.v3.persistence.entity.blog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "blog_imagem")
public class BlogImagemEntity {

  @Id
  private UUID id;

  @Column(name = "criado_por_usuario_id")
  private UUID criadoPorUsuarioId;

  private String tipo;
  private String estado;

  @Column(name = "private_object_key")
  private String privateObjectKey;

  @Column(name = "public_object_key")
  private String publicObjectKey;

  @Column(name = "mime_type")
  private String mimeType;

  private String extensao;
  @Column(length = 64)
  private String sha256;

  @Column(name = "tamanho_bytes")
  private Long tamanhoBytes;

  private Integer largura;
  private Integer altura;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  private long versao;

  protected BlogImagemEntity() {
  }

  public static BlogImagemEntity criarPrivada(
      UUID id,
      UUID atorId,
      String tipo,
      String privateObjectKey,
      String mimeType,
      String extensao,
      String sha256,
      long tamanhoBytes,
      int largura,
      int altura,
      OffsetDateTime agora) {
    BlogImagemEntity entity = new BlogImagemEntity();
    entity.id = id;
    entity.criadoPorUsuarioId = atorId;
    entity.tipo = tipo;
    entity.estado = "PRIVADA";
    entity.privateObjectKey = privateObjectKey;
    entity.mimeType = mimeType;
    entity.extensao = extensao;
    entity.sha256 = sha256;
    entity.tamanhoBytes = tamanhoBytes;
    entity.largura = largura;
    entity.altura = altura;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    return entity;
  }

  public void publicar(String publicObjectKey, OffsetDateTime agora) {
    this.publicObjectKey = publicObjectKey;
    this.estado = "PUBLICA";
    this.atualizadoEm = agora;
  }

  public void retirarDoPublico(OffsetDateTime agora) {
    this.publicObjectKey = null;
    this.estado = "PRIVADA";
    this.atualizadoEm = agora;
  }

  public UUID getId() { return id; }
  public UUID getCriadoPorUsuarioId() { return criadoPorUsuarioId; }
  public String getTipo() { return tipo; }
  public String getEstado() { return estado; }
  public String getPrivateObjectKey() { return privateObjectKey; }
  public String getPublicObjectKey() { return publicObjectKey; }
  public String getMimeType() { return mimeType; }
  public String getExtensao() { return extensao; }
  public String getSha256() { return sha256; }
  public Long getTamanhoBytes() { return tamanhoBytes; }
  public Integer getLargura() { return largura; }
  public Integer getAltura() { return altura; }
  public OffsetDateTime getCriadoEm() { return criadoEm; }
  public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
  public long getVersao() { return versao; }
}
