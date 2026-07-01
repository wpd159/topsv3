package br.com.topsdojob.v3.persistence.entity.midia;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.FinalidadeAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "anuncio_midia")
public class AnuncioMidiaEntity {
  protected AnuncioMidiaEntity() {
  }

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "anuncio_id")
  private UUID anuncioId;

  @Column(name = "arquivo_midia_id")
  private UUID arquivoMidiaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo")
  private TipoAnuncioMidia tipo;

  @Enumerated(EnumType.STRING)
  @Column(name = "finalidade")
  private FinalidadeAnuncioMidia finalidade;

  @Column(name = "ordem")
  private Integer ordem;

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  private StatusAnuncioMidia status;

  @Enumerated(EnumType.STRING)
  @Column(name = "classificacao_conteudo")
  private ClassificacaoConteudo classificacaoConteudo;

  @Column(name = "criado_em")
  private OffsetDateTime criadoEm;

  @Column(name = "atualizado_em")
  private OffsetDateTime atualizadoEm;

  public UUID getId() {
    return id;
  }

  public UUID getAnuncioId() {
    return anuncioId;
  }

  public UUID getArquivoMidiaId() {
    return arquivoMidiaId;
  }

  public TipoAnuncioMidia getTipo() {
    return tipo;
  }

  public FinalidadeAnuncioMidia getFinalidade() {
    return finalidade;
  }

  public Integer getOrdem() {
    return ordem;
  }

  public StatusAnuncioMidia getStatus() {
    return status;
  }

  public ClassificacaoConteudo getClassificacaoConteudo() {
    return classificacaoConteudo;
  }

  public OffsetDateTime getCriadoEm() {
    return criadoEm;
  }

  public OffsetDateTime getAtualizadoEm() {
    return atualizadoEm;
  }

}
