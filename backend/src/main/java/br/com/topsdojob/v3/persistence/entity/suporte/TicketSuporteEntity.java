package br.com.topsdojob.v3.persistence.entity.suporte;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PrioridadeTicketSuporte;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusTicketSuporte;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_suporte")
public class TicketSuporteEntity {
  protected TicketSuporteEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "pagamento_id")
  private UUID pagamentoId;

  @Column(name = "revisao_anuncio_id")
  private UUID revisaoAnuncioId;

  @Column(name = "assunto")
  private String assunto;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusTicketSuporte status;

  @Enumerated(EnumType.STRING)
  @Column(name = "prioridade")
  private PrioridadeTicketSuporte prioridade;

  @Column(name = "responsavel_usuario_id")
  private UUID responsavelUsuarioId;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Column(name = "encerrado_em")
  private OffsetDateTime encerradoEm;

  @Version
  @Column(name = "versao")
  private Integer versao;

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getPagamentoId() {
    return pagamentoId;
  }

  public UUID getRevisaoAnuncioId() {
    return revisaoAnuncioId;
  }

  public String getAssunto() {
    return assunto;
  }

  public StatusTicketSuporte getStatus() {
    return status;
  }

  public PrioridadeTicketSuporte getPrioridade() {
    return prioridade;
  }

  public UUID getResponsavelUsuarioId() {
    return responsavelUsuarioId;
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

  public Integer getVersao() {
    return versao;
  }

}
