package br.com.topsdojob.v3.persistence.entity.comercial;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoInteracaoComercial;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "comercial_interacao")
public class ComercialInteracaoEntity {
  protected ComercialInteracaoEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "contato_id")
  private UUID contatoId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoInteracaoComercial tipo;

  @Column(name = "resultado")
  private String resultado;

  @Column(name = "responsavel_usuario_id")
  private UUID responsavelUsuarioId;

  @Column(name = "resumo")
  private String resumo;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getContatoId() {
    return contatoId;
  }

  public TipoInteracaoComercial getTipo() {
    return tipo;
  }

  public String getResultado() {
    return resultado;
  }

  public UUID getResponsavelUsuarioId() {
    return responsavelUsuarioId;
  }

  public String getResumo() {
    return resumo;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

}
