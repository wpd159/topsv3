package br.com.topsdojob.v3.persistence.entity.faq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "faq_item")
public class FaqEntity {

  @Id
  private UUID id;

  private String pergunta;
  private String resposta;
  private String categoria;
  private String status;
  private int ordem;

  @Column(name = "criado_por_usuario_id")
  private UUID criadoPorUsuarioId;

  @Column(name = "atualizado_por_usuario_id")
  private UUID atualizadoPorUsuarioId;

  @Column(name = "criado_request_id")
  private String criadoRequestId;

  @Column(name = "publicado_em")
  private OffsetDateTime publicadoEm;

  @Column(name = "arquivado_em")
  private OffsetDateTime arquivadoEm;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  private long versao;

  protected FaqEntity() {
  }

  public static FaqEntity criarRascunho(
      UUID id,
      String pergunta,
      String resposta,
      String categoria,
      int ordem,
      UUID atorId,
      String requestId,
      OffsetDateTime agora) {
    FaqEntity entity = new FaqEntity();
    entity.id = id;
    entity.pergunta = pergunta;
    entity.resposta = resposta;
    entity.categoria = categoria;
    entity.status = "RASCUNHO";
    entity.ordem = ordem;
    entity.criadoPorUsuarioId = atorId;
    entity.atualizadoPorUsuarioId = atorId;
    entity.criadoRequestId = requestId;
    entity.criadoEm = agora;
    entity.atualizadoEm = agora;
    return entity;
  }

  public void atualizar(
      String pergunta,
      String resposta,
      String categoria,
      int ordem,
      UUID atorId,
      OffsetDateTime agora) {
    this.pergunta = pergunta;
    this.resposta = resposta;
    this.categoria = categoria;
    this.ordem = ordem;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public void publicar(UUID atorId, OffsetDateTime agora) {
    this.status = "PUBLICADO";
    this.publicadoEm = agora;
    this.arquivadoEm = null;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public void retirar(UUID atorId, OffsetDateTime agora) {
    this.status = "RASCUNHO";
    this.arquivadoEm = null;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public void arquivar(UUID atorId, OffsetDateTime agora) {
    this.status = "ARQUIVADO";
    this.arquivadoEm = agora;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public void reordenar(int novaOrdem, UUID atorId, OffsetDateTime agora) {
    this.ordem = novaOrdem;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public UUID getId() { return id; }
  public String getPergunta() { return pergunta; }
  public String getResposta() { return resposta; }
  public String getCategoria() { return categoria; }
  public String getStatus() { return status; }
  public int getOrdem() { return ordem; }
  public UUID getCriadoPorUsuarioId() { return criadoPorUsuarioId; }
  public UUID getAtualizadoPorUsuarioId() { return atualizadoPorUsuarioId; }
  public String getCriadoRequestId() { return criadoRequestId; }
  public OffsetDateTime getPublicadoEm() { return publicadoEm; }
  public OffsetDateTime getArquivadoEm() { return arquivadoEm; }
  public OffsetDateTime getCriadoEm() { return criadoEm; }
  public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
  public long getVersao() { return versao; }
}
