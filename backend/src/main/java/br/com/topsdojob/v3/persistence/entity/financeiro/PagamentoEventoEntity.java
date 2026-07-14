package br.com.topsdojob.v3.persistence.entity.financeiro;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamento_evento")
public class PagamentoEventoEntity {
  protected PagamentoEventoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "pagamento_id")
  private UUID pagamentoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provedor")
  private ProvedorPagamento provedor;

  @Column(name = "provedor_evento_id")
  private String provedorEventoId;

  @Column(name = "tipo_evento")
  private String tipoEvento;

  @Column(name = "payload_hash")
  private String payloadHash;

  @Column(name = "status_provedor")
  private String statusProvedor;

  @Column(name = "recebido_em")
  private OffsetDateTime recebidoEm;

  @Column(name = "processado_em")
  private OffsetDateTime processadoEm;

  @Column(name = "resultado")
  private String resultado;

  public UUID getId() {
    return id;
  }

  public UUID getPagamentoId() {
    return pagamentoId;
  }

  public ProvedorPagamento getProvedor() {
    return provedor;
  }

  public String getProvedorEventoId() {
    return provedorEventoId;
  }

  public String getTipoEvento() {
    return tipoEvento;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public String getStatusProvedor() {
    return statusProvedor;
  }

  public OffsetDateTime getRecebidoEm() {
    return recebidoEm;
  }

  public OffsetDateTime getProcessadoEm() {
    return processadoEm;
  }

  public String getResultado() {
    return resultado;
  }

  public static PagamentoEventoEntity registrarEfi(
      UUID id,
      UUID pagamentoId,
      String provedorEventoId,
      String tipoEvento,
      String payloadHash,
      String statusProvedor,
      OffsetDateTime recebidoEm,
      OffsetDateTime processadoEm,
      String resultado) {
    PagamentoEventoEntity entity = new PagamentoEventoEntity();
    entity.id = id;
    entity.pagamentoId = pagamentoId;
    entity.provedor = ProvedorPagamento.EFI;
    entity.provedorEventoId = provedorEventoId;
    entity.tipoEvento = tipoEvento;
    entity.payloadHash = payloadHash;
    entity.statusProvedor = statusProvedor;
    entity.recebidoEm = recebidoEm;
    entity.processadoEm = processadoEm;
    entity.resultado = resultado;
    return entity;
  }

}
