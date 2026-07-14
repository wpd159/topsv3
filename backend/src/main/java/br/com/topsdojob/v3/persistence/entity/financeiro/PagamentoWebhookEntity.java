package br.com.topsdojob.v3.persistence.entity.financeiro;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ProvedorPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ValidacaoWebhook;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamento_webhook")
public class PagamentoWebhookEntity {
  protected PagamentoWebhookEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "provedor")
  private ProvedorPagamento provedor;

  @Column(name = "evento_id")
  private String eventoId;

  @Column(name = "txid")
  private String txid;

  @Column(name = "payload_hash")
  private String payloadHash;

  @Column(name = "origem_ip_hash")
  private String origemIpHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "validacao_resultado")
  private ValidacaoWebhook validacaoResultado;

  @Column(name = "recebido_em")
  private OffsetDateTime recebidoEm;

  @Column(name = "processado_em")
  private OffsetDateTime processadoEm;

  @Column(name = "resultado")
  private String resultado;

  @Column(name = "erro_resumido")
  private String erroResumido;

  @Column(name = "tentativas")
  private Integer tentativas;

  public UUID getId() {
    return id;
  }

  public ProvedorPagamento getProvedor() {
    return provedor;
  }

  public String getEventoId() {
    return eventoId;
  }

  public String getTxid() {
    return txid;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public String getOrigemIpHash() {
    return origemIpHash;
  }

  public ValidacaoWebhook getValidacaoResultado() {
    return validacaoResultado;
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

  public String getErroResumido() {
    return erroResumido;
  }

  public Integer getTentativas() {
    return tentativas;
  }

  public static PagamentoWebhookEntity receber(
      UUID id,
      String eventoId,
      String txid,
      String payloadHash,
      String origemIpHash,
      ValidacaoWebhook validacaoResultado,
      OffsetDateTime recebidoEm) {
    PagamentoWebhookEntity entity = new PagamentoWebhookEntity();
    entity.id = id;
    entity.provedor = ProvedorPagamento.EFI;
    entity.eventoId = eventoId;
    entity.txid = txid;
    entity.payloadHash = payloadHash;
    entity.origemIpHash = origemIpHash;
    entity.validacaoResultado = validacaoResultado;
    entity.recebidoEm = recebidoEm;
    entity.tentativas = 1;
    entity.resultado = "RECEBIDO";
    return entity;
  }

  public void registrarNovaTentativa() {
    this.tentativas = (this.tentativas == null ? 0 : this.tentativas) + 1;
  }

  public void concluir(String resultado, String erroResumido, OffsetDateTime processadoEm) {
    this.resultado = resultado;
    this.erroResumido = erroResumido;
    this.processadoEm = processadoEm;
  }
}
