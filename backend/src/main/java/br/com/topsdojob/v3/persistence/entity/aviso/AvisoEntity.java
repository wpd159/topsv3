package br.com.topsdojob.v3.persistence.entity.aviso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "aviso_administrativo")
public class AvisoEntity {

  @Id
  private UUID id;

  private String titulo;
  private String descricao;

  @Column(name = "local_exibicao")
  private String localExibicao;

  @Column(name = "frequencia_exibicao")
  private String frequenciaExibicao;

  private String status;

  @Column(name = "permite_dispensar")
  private boolean permiteDispensar;

  @Column(name = "ativo_de")
  private OffsetDateTime ativoDe;

  @Column(name = "ativo_ate")
  private OffsetDateTime ativoAte;

  @Column(name = "criado_por_usuario_id")
  private UUID criadoPorUsuarioId;

  @Column(name = "criado_por_nome")
  private String criadoPorNome;

  @Column(name = "atualizado_por_usuario_id")
  private UUID atualizadoPorUsuarioId;

  @Column(name = "criado_request_id")
  private String criadoRequestId;

  @Column(name = "publicado_em")
  private OffsetDateTime publicadoEm;

  @Column(name = "retirado_em")
  private OffsetDateTime retiradoEm;

  @Column(name = "arquivado_em")
  private OffsetDateTime arquivadoEm;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  @Version
  private long versao;

  protected AvisoEntity() {
  }

  public static AvisoEntity criarRascunho(
      UUID id,
      String titulo,
      String descricao,
      String localExibicao,
      String frequenciaExibicao,
      boolean permiteDispensar,
      OffsetDateTime ativoDe,
      OffsetDateTime ativoAte,
      UUID atorId,
      String atorNome,
      String requestId,
      OffsetDateTime agora) {
    AvisoEntity aviso = new AvisoEntity();
    aviso.id = id;
    aviso.titulo = titulo;
    aviso.descricao = descricao;
    aviso.localExibicao = localExibicao;
    aviso.frequenciaExibicao = frequenciaExibicao;
    aviso.status = "RASCUNHO";
    aviso.permiteDispensar = permiteDispensar;
    aviso.ativoDe = ativoDe;
    aviso.ativoAte = ativoAte;
    aviso.criadoPorUsuarioId = atorId;
    aviso.criadoPorNome = atorNome;
    aviso.atualizadoPorUsuarioId = atorId;
    aviso.criadoRequestId = requestId;
    aviso.criadoEm = agora;
    aviso.atualizadoEm = agora;
    return aviso;
  }

  public void atualizar(
      String titulo,
      String descricao,
      String localExibicao,
      String frequenciaExibicao,
      boolean permiteDispensar,
      OffsetDateTime ativoDe,
      OffsetDateTime ativoAte,
      UUID atorId,
      OffsetDateTime agora) {
    this.titulo = titulo;
    this.descricao = descricao;
    this.localExibicao = localExibicao;
    this.frequenciaExibicao = frequenciaExibicao;
    this.permiteDispensar = permiteDispensar;
    this.ativoDe = ativoDe;
    this.ativoAte = ativoAte;
    this.atualizadoPorUsuarioId = atorId;
    this.atualizadoEm = agora;
  }

  public void publicar(UUID atorId, OffsetDateTime agora) {
    status = "PUBLICADO";
    publicadoEm = agora;
    retiradoEm = null;
    arquivadoEm = null;
    atualizadoPorUsuarioId = atorId;
    atualizadoEm = agora;
  }

  public void retirar(UUID atorId, OffsetDateTime agora) {
    status = "RASCUNHO";
    retiradoEm = agora;
    arquivadoEm = null;
    atualizadoPorUsuarioId = atorId;
    atualizadoEm = agora;
  }

  public void arquivar(UUID atorId, OffsetDateTime agora) {
    status = "ARQUIVADO";
    arquivadoEm = agora;
    atualizadoPorUsuarioId = atorId;
    atualizadoEm = agora;
  }

  public UUID getId() { return id; }
  public String getTitulo() { return titulo; }
  public String getDescricao() { return descricao; }
  public String getLocalExibicao() { return localExibicao; }
  public String getFrequenciaExibicao() { return frequenciaExibicao; }
  public String getStatus() { return status; }
  public boolean isPermiteDispensar() { return permiteDispensar; }
  public OffsetDateTime getAtivoDe() { return ativoDe; }
  public OffsetDateTime getAtivoAte() { return ativoAte; }
  public UUID getCriadoPorUsuarioId() { return criadoPorUsuarioId; }
  public String getCriadoPorNome() { return criadoPorNome; }
  public UUID getAtualizadoPorUsuarioId() { return atualizadoPorUsuarioId; }
  public String getCriadoRequestId() { return criadoRequestId; }
  public OffsetDateTime getPublicadoEm() { return publicadoEm; }
  public OffsetDateTime getRetiradoEm() { return retiradoEm; }
  public OffsetDateTime getArquivadoEm() { return arquivadoEm; }
  public OffsetDateTime getCriadoEm() { return criadoEm; }
  public OffsetDateTime getAtualizadoEm() { return atualizadoEm; }
  public long getVersao() { return versao; }
}
